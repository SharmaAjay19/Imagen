package com.ajsharm.imagen.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Securely stores Azure OpenAI credentials. */
class SecureConfigStore(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "imagen_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, "").orEmpty()
        set(v) { prefs.edit().putString(KEY_ENDPOINT, v.trim().trimEnd('/')).apply() }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(v) { prefs.edit().putString(KEY_API_KEY, v.trim()).apply() }

    var apiVersion: String
        get() = prefs.getString(KEY_API_VERSION, DEFAULT_API_VERSION) ?: DEFAULT_API_VERSION
        set(v) { prefs.edit().putString(KEY_API_VERSION, v.trim()).apply() }

    var deploymentName: String
        get() = prefs.getString(KEY_DEPLOYMENT, DEFAULT_DEPLOYMENT) ?: DEFAULT_DEPLOYMENT
        set(v) { prefs.edit().putString(KEY_DEPLOYMENT, v.trim()).apply() }

    val isConfigured: Boolean
        get() = endpoint.isNotBlank() && apiKey.isNotBlank() && deploymentName.isNotBlank()

    companion object {
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_API_KEY = "apiKey"
        private const val KEY_API_VERSION = "apiVersion"
        private const val KEY_DEPLOYMENT = "deployment"
        const val DEFAULT_API_VERSION = "2025-04-01-preview"
        const val DEFAULT_DEPLOYMENT = "gpt-image-2"
    }
}
