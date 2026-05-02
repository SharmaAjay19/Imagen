package com.ajsharm.imagen.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ajsharm.imagen.ui.ImageQuality
import com.ajsharm.imagen.ui.ImageSize
import com.ajsharm.imagen.ui.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PrefsStore(private val ds: DataStore<Preferences>) {

    private val K_THEME = stringPreferencesKey("theme")
    private val K_SIZE = stringPreferencesKey("size")
    private val K_QUALITY = stringPreferencesKey("quality")
    private val K_CURRENT_SESSION = stringPreferencesKey("currentSessionId")

    val theme: Flow<ThemeChoice> = ds.data.map { p ->
        runCatching { ThemeChoice.valueOf(p[K_THEME] ?: ThemeChoice.MIDNIGHT.name) }
            .getOrDefault(ThemeChoice.MIDNIGHT)
    }

    suspend fun setTheme(choice: ThemeChoice) {
        ds.edit { it[K_THEME] = choice.name }
    }

    suspend fun loadStartup(): Startup {
        val p = ds.data.first()
        val size = runCatching { ImageSize.valueOf(p[K_SIZE] ?: ImageSize.S1024.name) }.getOrDefault(ImageSize.S1024)
        val quality = runCatching { ImageQuality.valueOf(p[K_QUALITY] ?: ImageQuality.AUTO.name) }.getOrDefault(ImageQuality.AUTO)
        val current = p[K_CURRENT_SESSION]?.takeIf { it.isNotBlank() }
        return Startup(size, quality, current)
    }

    suspend fun setSize(s: ImageSize) { ds.edit { it[K_SIZE] = s.name } }
    suspend fun setQuality(q: ImageQuality) { ds.edit { it[K_QUALITY] = q.name } }
    suspend fun setCurrentSession(id: String?) {
        ds.edit { p ->
            if (id == null) p.remove(K_CURRENT_SESSION) else p[K_CURRENT_SESSION] = id
        }
    }

    data class Startup(
        val size: ImageSize,
        val quality: ImageQuality,
        val currentSessionId: String?,
    )
}
