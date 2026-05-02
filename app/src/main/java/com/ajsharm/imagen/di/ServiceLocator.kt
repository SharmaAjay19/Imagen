package com.ajsharm.imagen.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.ajsharm.imagen.data.AzureImageClient
import com.ajsharm.imagen.data.BackupManager
import com.ajsharm.imagen.data.ImageStorage
import com.ajsharm.imagen.data.ImagenDatabase
import com.ajsharm.imagen.data.PrefsStore
import com.ajsharm.imagen.data.Repository
import com.ajsharm.imagen.data.SecureConfigStore
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val Context.appDataStore by preferencesDataStore(name = "imagen_prefs")

object ServiceLocator {
    lateinit var appContext: Context
        private set
    lateinit var db: ImagenDatabase
        private set
    lateinit var repository: Repository
        private set
    lateinit var imageStorage: ImageStorage
        private set
    lateinit var secureConfig: SecureConfigStore
        private set
    lateinit var prefs: PrefsStore
        private set
    lateinit var azureClient: AzureImageClient
        private set
    lateinit var backupManager: BackupManager
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        db = Room.databaseBuilder(appContext, ImagenDatabase::class.java, "imagen.db")
            .fallbackToDestructiveMigration()
            .build()
        imageStorage = ImageStorage(appContext)
        secureConfig = SecureConfigStore(appContext)
        prefs = PrefsStore(appContext.appDataStore)
        val http = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.SECONDS) // no overall cap; rely on per-stage timeouts and explicit cancellation
            .retryOnConnectionFailure(true)
            .build()
        azureClient = AzureImageClient(http, secureConfig)
        repository = Repository(db.sessionDao(), db.messageDao(), imageStorage)
        backupManager = BackupManager(appContext, db, imageStorage)
        bootstrapConfigIfPresent()
    }

    /**
     * Debug helper: if a file `imagen-bootstrap-config.json` exists in the app's
     * private files dir (planted via `adb ... run-as`), import it into SecureConfigStore
     * and delete it. Recognises the same key aliases as the in-app importer.
     */
    private fun bootstrapConfigIfPresent() {
        val f = File(appContext.filesDir, "imagen-bootstrap-config.json")
        if (!f.exists()) return
        runCatching {
            val obj = Json.parseToJsonElement(f.readText()) as? JsonObject ?: return@runCatching
            fun field(vararg keys: String): String? {
                for (k in keys) {
                    val v = obj[k]?.jsonPrimitive?.contentOrNull
                    if (!v.isNullOrBlank()) return v
                }
                return null
            }
            val ep = field("endpoint", "AZURE_OPENAI_ENDPOINT", "azure_endpoint")
            val key = field("apiKey", "api_key", "AZURE_OPENAI_API_KEY", "key")
            val dep = field("deployment", "deploymentName", "AZURE_OPENAI_DEPLOYMENT", "model")
                ?: SecureConfigStore.DEFAULT_DEPLOYMENT
            val ver = field("apiVersion", "api_version", "AZURE_OPENAI_API_VERSION", "version")
                ?: SecureConfigStore.DEFAULT_API_VERSION
            if (!ep.isNullOrBlank() && !key.isNullOrBlank()) {
                secureConfig.endpoint = ep
                secureConfig.apiKey = key
                secureConfig.deploymentName = dep
                secureConfig.apiVersion = ver
            }
        }
        f.delete()
    }
}
