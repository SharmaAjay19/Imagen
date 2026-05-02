package com.ajsharm.imagen.data

import com.ajsharm.imagen.ui.ImageQuality
import com.ajsharm.imagen.ui.ImageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of a successful image generation/edit. */
data class GeneratedImage(
    val pngBytes: ByteArray,
    val revisedPrompt: String?,
)

class AzureApiException(val httpCode: Int, message: String) : RuntimeException(message)

class AzureImageClient(
    private val http: OkHttpClient,
    private val config: SecureConfigStore,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Calls /generations (no inputs) or /edits (with inputs).
     * @param inputs absolute files to upload as references; empty -> text-to-image.
     */
    suspend fun generateOrEdit(
        prompt: String,
        inputs: List<File>,
        size: ImageSize,
        quality: ImageQuality,
    ): GeneratedImage = withContext(Dispatchers.IO) {
        require(config.isConfigured) { "Azure config missing" }
        val url = buildUrl(if (inputs.isEmpty()) "generations" else "edits")
        val body = if (inputs.isEmpty()) buildGenerateBody(prompt, size, quality)
                   else buildEditBody(prompt, inputs, size)
        val req = Request.Builder()
            .url(url)
            .header("api-key", config.apiKey)
            .header("Accept", "application/json")
            .post(body)
            .build()

        val call = http.newCall(req)
        val response = call.awaitCancellable()
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw AzureApiException(resp.code, friendlyError(resp.code, text))
            }
            parseImageResponse(text)
        }
    }

    private fun buildUrl(path: String): String {
        val ep = config.endpoint.trimEnd('/')
        val dep = config.deploymentName
        val ver = config.apiVersion
        return "$ep/openai/deployments/$dep/images/$path?api-version=$ver"
    }

    private fun buildGenerateBody(prompt: String, size: ImageSize, quality: ImageQuality): RequestBody {
        val obj = buildJsonObject {
            put("model", config.deploymentName)
            put("prompt", prompt)
            put("n", 1)
            put("size", size.api)
            put("quality", quality.api)
        }
        return obj.toString().toRequestBody("application/json".toMediaType())
    }

    private fun buildEditBody(prompt: String, inputs: List<File>, size: ImageSize): RequestBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", config.deploymentName)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("n", "1")
            .addFormDataPart("size", size.api)
        // Use repeated "image[]" parts for arrays (Azure accepts list).
        val partName = if (inputs.size == 1) "image" else "image[]"
        inputs.forEach { file ->
            val mt = "image/png".toMediaType()
            builder.addFormDataPart(partName, file.name, file.asRequestBody(mt))
        }
        return builder.build()
    }

    private fun parseImageResponse(text: String): GeneratedImage {
        val root: JsonElement = json.parseToJsonElement(text)
        val data = root.jsonObject["data"]?.jsonArray
            ?: error("Malformed response: missing data")
        val first = data.firstOrNull()?.jsonObject ?: error("Empty data array")
        val b64 = first["b64_json"]?.jsonPrimitive?.contentOrNullSafe()
        val urlStr = first["url"]?.jsonPrimitive?.contentOrNullSafe()
        val revised = first["revised_prompt"]?.jsonPrimitive?.contentOrNullSafe()
        val bytes = when {
            !b64.isNullOrBlank() -> android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            !urlStr.isNullOrBlank() -> downloadBytes(urlStr)
            else -> error("Response had neither b64_json nor url")
        }
        return GeneratedImage(bytes, revised)
    }

    private fun downloadBytes(url: String): ByteArray {
        val req = Request.Builder().url(url).get().build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw AzureApiException(r.code, "Failed to download image (${r.code})")
            r.body?.bytes() ?: error("Empty image body")
        }
    }

    private fun friendlyError(code: Int, body: String): String {
        val parsed = runCatching {
            val m = json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
            m?.get("message")?.jsonPrimitive?.contentOrNullSafe()
        }.getOrNull()
        val detail = parsed?.takeIf { it.isNotBlank() }
        return when (code) {
            401 -> "Invalid API key. Check Settings."
            404 -> "Deployment not found at this endpoint."
            413 -> "Images too large. Try fewer or smaller references."
            429 -> "Rate limited. Try again in a moment."
            in 500..599 -> "Azure service error ($code). Try again."
            else -> detail ?: "Request failed ($code)"
        }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content else content.takeIf { it != "null" }
}

private suspend fun Call.awaitCancellable(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }
