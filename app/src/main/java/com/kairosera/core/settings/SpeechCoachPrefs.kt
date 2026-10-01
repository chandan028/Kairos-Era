package com.kairosera.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kairosera.ai.AiBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class SpeechCoachSettings(
    /** GPU first (falls back to CPU by itself) or CPU only. */
    val backend: AiBackend = AiBackend.GPU,
    /** Keep each recording after analysis instead of deleting it. Off by default. */
    val keepRecordings: Boolean = false,
)

private val Context.speechCoachStore by preferencesDataStore(name = "speech_coach")

/** The speaking coach's two settings, in their own small DataStore. */
class SpeechCoachPrefs(private val context: Context) {
    private object Keys {
        val backend = stringPreferencesKey("backend")
        val keep = booleanPreferencesKey("keep_recordings")
    }

    val settings: Flow<SpeechCoachSettings> = context.speechCoachStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            SpeechCoachSettings(
                backend = p[Keys.backend]?.let { v -> AiBackend.entries.firstOrNull { it.name == v } } ?: AiBackend.GPU,
                keepRecordings = p[Keys.keep] ?: false,
            )
        }

    suspend fun current(): SpeechCoachSettings = settings.first()
    suspend fun setBackend(b: AiBackend) { context.speechCoachStore.edit { it[Keys.backend] = b.name } }
    suspend fun setKeepRecordings(on: Boolean) { context.speechCoachStore.edit { it[Keys.keep] = on } }
    suspend fun clear() { context.speechCoachStore.edit { it.clear() } }
}
