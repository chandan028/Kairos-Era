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
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Cards on Home, in the user's order. */
enum class HomeCard { PROGRESS, NEXT_UP, QUOTE, TRACKERS }

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
}
