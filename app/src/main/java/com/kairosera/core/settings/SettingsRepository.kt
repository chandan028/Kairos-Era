package com.kairosera.core.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kairosera.domain.model.ReminderSound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Cards on Home, in the user's order. */
enum class HomeCard { QUOTE, PROGRESS, NEXT_UP, TRACKERS }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val name: String = "",
    val focusAreas: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultSound: ReminderSound = ReminderSound.KAIROS_BELL,
    val defaultPersistent: Boolean = true,
    val snoozeMinutes: Int = 10,
    val homeCards: List<HomeCard> = HomeCard.entries,
    val hiddenCards: Set<HomeCard> = emptySet(),
    val favoriteQuotes: Set<Int> = emptySet(),
    /** App lock. Device-specific, so never written to or read from a backup file. */
    val lockEnabled: Boolean = false,
    val lockAfterSeconds: Int = 60,
    val lastBackupAt: Long? = null,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Small preferences only. Anything the user writes lives in Room. */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val name = stringPreferencesKey("name")
        val focus = stringPreferencesKey("focus_areas")
        val theme = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val sound = stringPreferencesKey("default_sound")
        val persistent = booleanPreferencesKey("default_persistent")
        val snooze = intPreferencesKey("snooze_minutes")
        val homeCards = stringPreferencesKey("home_cards")
        val hiddenCards = stringPreferencesKey("hidden_cards")
        val favoriteQuotes = stringPreferencesKey("favorite_quotes")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val lockAfter = intPreferencesKey("lock_after_seconds")
        val lastBackup = androidx.datastore.preferences.core.longPreferencesKey("last_backup_at")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val order = this[Keys.homeCards]?.split(',')?.mapNotNull { n -> HomeCard.entries.firstOrNull { it.name == n } }.orEmpty()
        return AppSettings(
            onboardingDone = this[Keys.onboardingDone] ?: false,
            name = this[Keys.name].orEmpty(),
            focusAreas = this[Keys.focus]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
            themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.theme] } ?: ThemeMode.SYSTEM,
            dynamicColor = this[Keys.dynamicColor] ?: false,
            defaultSound = ReminderSound.fromKey(this[Keys.sound] ?: ReminderSound.KAIROS_BELL.key),
            defaultPersistent = this[Keys.persistent] ?: true,
            snoozeMinutes = (this[Keys.snooze] ?: 10).coerceIn(1, 120),
            // Cards added in later versions are appended so they are never lost.
            homeCards = (order + HomeCard.entries).distinct(),
            hiddenCards = this[Keys.hiddenCards]?.split(',')?.mapNotNull { n -> HomeCard.entries.firstOrNull { it.name == n } }?.toSet().orEmpty(),
            favoriteQuotes = this[Keys.favoriteQuotes]?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty(),
            lockEnabled = this[Keys.lockEnabled] ?: false,
            lockAfterSeconds = (this[Keys.lockAfter] ?: 60).coerceIn(0, 3600),
            lastBackupAt = this[Keys.lastBackup],
        )
    }

    suspend fun completeOnboarding(name: String, focus: Set<String>) = context.dataStore.edit {
        it[Keys.name] = name.trim().take(40)
        it[Keys.focus] = focus.joinToString(",")
        it[Keys.onboardingDone] = true
    }

    suspend fun setName(name: String) = context.dataStore.edit { it[Keys.name] = name.trim().take(40) }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    suspend fun setDefaultSound(sound: ReminderSound) = context.dataStore.edit { it[Keys.sound] = sound.key }
    suspend fun setDefaultPersistent(value: Boolean) = context.dataStore.edit { it[Keys.persistent] = value }
    suspend fun setSnoozeMinutes(minutes: Int) = context.dataStore.edit { it[Keys.snooze] = minutes.coerceIn(1, 120) }
    suspend fun setHomeCards(order: List<HomeCard>, hidden: Set<HomeCard>) = context.dataStore.edit {
        it[Keys.homeCards] = order.joinToString(",") { c -> c.name }
        it[Keys.hiddenCards] = hidden.joinToString(",") { c -> c.name }
    }
    suspend fun toggleFavoriteQuote(day: Int) = context.dataStore.edit {
        val current = it[Keys.favoriteQuotes]?.split(',')?.mapNotNull { s -> s.toIntOrNull() }?.toSet().orEmpty()
        it[Keys.favoriteQuotes] = (if (day in current) current - day else current + day).joinToString(",")
    }

    suspend fun setLock(enabled: Boolean) = context.dataStore.edit { it[Keys.lockEnabled] = enabled }
    suspend fun setLockAfterSeconds(seconds: Int) = context.dataStore.edit { it[Keys.lockAfter] = seconds.coerceIn(0, 3600) }
    suspend fun setLastBackupAt(epochMillis: Long) = context.dataStore.edit { it[Keys.lastBackup] = epochMillis }

    /**
     * Preferences that travel inside a backup file, as plain strings. Onboarding progress, the
     * app lock and the last-backup time stay with the phone and are never included.
     */
    suspend fun exportForBackup(): Map<String, String> {
        val p = context.dataStore.data.catch { emit(emptyPreferences()) }.first()
        return buildMap {
            p[Keys.name]?.let { put("name", it) }
            p[Keys.theme]?.let { put("theme_mode", it) }
            p[Keys.dynamicColor]?.let { put("dynamic_color", it.toString()) }
            p[Keys.sound]?.let { put("default_sound", it) }
            p[Keys.persistent]?.let { put("default_persistent", it.toString()) }
            p[Keys.snooze]?.let { put("snooze_minutes", it.toString()) }
            p[Keys.homeCards]?.let { put("home_cards", it) }
            p[Keys.hiddenCards]?.let { put("hidden_cards", it) }
            p[Keys.favoriteQuotes]?.let { put("favorite_quotes", it) }
        }
    }

    /** Applies [exportForBackup] output. Unknown keys and malformed values are ignored, never trusted. */
    suspend fun importFromBackup(values: Map<String, String>) = context.dataStore.edit { p ->
        values["name"]?.let { p[Keys.name] = it.trim().take(40) }
        values["theme_mode"]?.takeIf { v -> ThemeMode.entries.any { it.name == v } }?.let { p[Keys.theme] = it }
        values["dynamic_color"]?.toBooleanStrictOrNull()?.let { p[Keys.dynamicColor] = it }
        values["default_sound"]?.let { p[Keys.sound] = ReminderSound.fromKey(it).key }
        values["default_persistent"]?.toBooleanStrictOrNull()?.let { p[Keys.persistent] = it }
        values["snooze_minutes"]?.toIntOrNull()?.let { p[Keys.snooze] = it.coerceIn(1, 120) }
        values["home_cards"]?.take(200)?.let { p[Keys.homeCards] = it }
        values["hidden_cards"]?.take(200)?.let { p[Keys.hiddenCards] = it }
        values["favorite_quotes"]?.take(4000)?.let { v -> p[Keys.favoriteQuotes] = v.split(',').mapNotNull { it.toIntOrNull() }.joinToString(",") }
    }
}
