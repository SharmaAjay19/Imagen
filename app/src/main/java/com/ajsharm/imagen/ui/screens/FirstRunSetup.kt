package com.ajsharm.imagen.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajsharm.imagen.data.SecureConfigStore
import com.ajsharm.imagen.ui.theme.LocalImagenColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun FirstRunSetup(
    initial: SecureConfigStore,
    onSave: (endpoint: String, key: String, deployment: String, version: String) -> Unit,
) {
    val c = LocalImagenColors.current
    val ctx = LocalContext.current
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var deployment by remember { mutableStateOf(initial.deploymentName.ifBlank { SecureConfigStore.DEFAULT_DEPLOYMENT }) }
    var version by remember { mutableStateOf(initial.apiVersion.ifBlank { SecureConfigStore.DEFAULT_API_VERSION }) }
    var importError by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
            val obj = Json.parseToJsonElement(text) as? JsonObject
                ?: error("Not a JSON object")
            fun field(vararg keys: String): String? {
                for (k in keys) {
                    val v = obj[k]?.jsonPrimitive?.contentOrNull
                    if (!v.isNullOrBlank()) return v
                }
                return null
            }
            field("endpoint", "AZURE_OPENAI_ENDPOINT", "azure_endpoint")?.let { endpoint = it }
            field("apiKey", "api_key", "AZURE_OPENAI_API_KEY", "key")?.let { apiKey = it }
            field("deployment", "deploymentName", "AZURE_OPENAI_DEPLOYMENT", "model")?.let { deployment = it }
            field("apiVersion", "api_version", "AZURE_OPENAI_API_VERSION", "version")?.let { version = it }
            importError = null
        }.onFailure { importError = it.message ?: "Failed to read file" }
    }

    Box(modifier = Modifier.fillMaxSize().background(c.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Welcome to Imagen", color = c.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect your Azure OpenAI deployment to start generating images.",
                color = c.muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import from config.json") }
            importError?.let {
                Spacer(Modifier.height(4.dp))
                Text("Import failed: $it", color = c.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = endpoint, onValueChange = { endpoint = it },
                label = { Text("Endpoint") },
                placeholder = { Text("https://YOUR_RESOURCE.cognitiveservices.azure.com/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = deployment, onValueChange = { deployment = it },
                label = { Text("Deployment name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = version, onValueChange = { version = it },
                label = { Text("API version") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSave(endpoint, apiKey, deployment, version) },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentOn),
                modifier = Modifier.fillMaxWidth(),
                enabled = endpoint.isNotBlank() && apiKey.isNotBlank() && deployment.isNotBlank(),
            ) { Text("Get started") }
        }
    }
}
