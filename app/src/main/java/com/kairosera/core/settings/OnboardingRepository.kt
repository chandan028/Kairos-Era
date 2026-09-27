package com.kairosera.core.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime

enum class OnboardingStep { WELCOME, FOCUS, FIRST_ACTION, REMINDERS, READY }

/** What the person answered, in their words. Kept while onboarding runs and cleared when it ends. */
enum class FocusArea(val key: String) { GET_THINGS_DONE("tasks"), LEARN("learn"), HABITS("habits"), HEALTH("health"), READ("read"), GOALS("goals") }

enum class ReminderChoice { UNDECIDED, ALLOWED, DECLINED }

data class OnboardingDraft(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val focus: Set<FocusArea> = emptySet(),
    val firstThing: String = "",
    val time: LocalTime? = null,
    val reminders: ReminderChoice = ReminderChoice.UNDECIDED,
    /** True once the system permission dialog has been shown, so it is never shown twice. */
    val permissionAsked: Boolean = false,
)

private val Context.onboardingStore by preferencesDataStore(name = "onboarding")

/**
 * Onboarding progress in its own small DataStore, so leaving the app or process death mid-way
 * brings the person back to the same step with their answers. Nothing here leaves the device.
 */
class OnboardingRepository(private val context: Context) {
    private object Keys {
        val step = stringPreferencesKey("step")
        val focus = stringPreferencesKey("focus")
        val first = stringPreferencesKey("first_thing")
        val time = intPreferencesKey("time_minutes")
        val reminders = stringPreferencesKey("reminders")
        val asked = booleanPreferencesKey("permission_asked")
    }

    val draft: Flow<OnboardingDraft> = context.onboardingStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toDraft() }

    private fun Preferences.toDraft() = OnboardingDraft(
        step = OnboardingStep.entries.firstOrNull { it.name == this[Keys.step] } ?: OnboardingStep.WELCOME,
        focus = this[Keys.focus]?.split(',')?.mapNotNull { k -> FocusArea.entries.firstOrNull { it.key == k } }?.toSet().orEmpty(),
        firstThing = this[Keys.first].orEmpty(),
        time = this[Keys.time]?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) },
        reminders = ReminderChoice.entries.firstOrNull { it.name == this[Keys.reminders] } ?: ReminderChoice.UNDECIDED,
        permissionAsked = this[Keys.asked] ?: false,
    )

    suspend fun save(d: OnboardingDraft) = context.onboardingStore.edit {
        it[Keys.step] = d.step.name
        it[Keys.focus] = d.focus.joinToString(",") { f -> f.key }
        it[Keys.first] = d.firstThing.take(MAX_TITLE)
        if (d.time == null) it.remove(Keys.time) else it[Keys.time] = d.time.hour * 60 + d.time.minute
        it[Keys.reminders] = d.reminders.name
        it[Keys.asked] = d.permissionAsked
    }

    suspend fun clear() = context.onboardingStore.edit { it.clear() }

    companion object {
        const val MAX_TITLE = 200
    }
}
