package com.kairosera.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime

/** The Winter Arc reminders. Each can be switched off, moved, and limited to some weekdays. */
enum class ArcReminder(val defaultTime: LocalTime, val defaultOn: Boolean) {
    WATER(LocalTime.of(11, 0), true),
    READING(LocalTime.of(21, 0), true),
    STUDY(LocalTime.of(18, 30), true),
    COLD_SHOWER(LocalTime.of(6, 0), false),
    DEEP_WORK(LocalTime.of(9, 30), false),
    WAKE_PREP(LocalTime.of(21, 30), true),
    SPEAKING(LocalTime.of(19, 30), false),
}

data class ReminderConfig(val enabled: Boolean, val time: LocalTime, /** ISO weekdays 1..7. */ val days: Set<Int>)

data class WinterArcSettings(
    /** Winter Arc is the active mode: Home becomes its dashboard. */
    val enabled: Boolean = false,
    /** Onboarding was finished at least once. */
    val onboarded: Boolean = false,
    val reminders: Map<ArcReminder, ReminderConfig> = ArcReminder.entries.associateWith { ReminderConfig(it.defaultOn, it.defaultTime, ALL_DAYS) },
    /** Count steps with the phone's own step sensor (when allowed). */
    val stepSensor: Boolean = false,
) {
    companion object { val ALL_DAYS = (1..7).toSet() }
}

private val Context.winterArcStore by preferencesDataStore(name = "winter_arc")

/** Winter Arc preferences in their own small DataStore. The challenge data itself lives in Room. */
class WinterArcPrefs(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val onboarded = booleanPreferencesKey("onboarded")
        val stepSensor = booleanPreferencesKey("step_sensor")
        fun on(r: ArcReminder) = booleanPreferencesKey("rem_on_${r.name}")
        fun time(r: ArcReminder) = intPreferencesKey("rem_time_${r.name}")
        fun days(r: ArcReminder) = stringPreferencesKey("rem_days_${r.name}")
        // Step sensor bookkeeping: the counter value at the start of [stepDay] and the last value seen.
        val stepDay = longPreferencesKey("step_day")
        val stepBase = floatPreferencesKey("step_base")
        val stepLast = floatPreferencesKey("step_last")
    }

    val settings: Flow<WinterArcSettings> = context.winterArcStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            WinterArcSettings(
                enabled = p[Keys.enabled] ?: false,
                onboarded = p[Keys.onboarded] ?: false,
                stepSensor = p[Keys.stepSensor] ?: false,
                reminders = ArcReminder.entries.associateWith { r ->
                    ReminderConfig(
                        enabled = p[Keys.on(r)] ?: r.defaultOn,
                        time = p[Keys.time(r)]?.let { LocalTime.of((it / 60).coerceIn(0, 23), (it % 60).coerceIn(0, 59)) } ?: r.defaultTime,
                        days = p[Keys.days(r)]?.split(',')?.mapNotNull { it.toIntOrNull() }?.filter { it in 1..7 }?.toSet() ?: WinterArcSettings.ALL_DAYS,
                    )
                },
            )
        }

    suspend fun current(): WinterArcSettings = settings.first()

    suspend fun setEnabled(on: Boolean) = context.winterArcStore.edit { it[Keys.enabled] = on }
    suspend fun setOnboarded() = context.winterArcStore.edit { it[Keys.onboarded] = true; it[Keys.enabled] = true }
    suspend fun setStepSensor(on: Boolean) = context.winterArcStore.edit { it[Keys.stepSensor] = on }

    suspend fun setReminder(r: ArcReminder, config: ReminderConfig) = context.winterArcStore.edit {
        it[Keys.on(r)] = config.enabled
        it[Keys.time(r)] = config.time.hour * 60 + config.time.minute
        it[Keys.days(r)] = config.days.sorted().joinToString(",")
    }

    /**
     * Turns a raw step-counter reading (steps since the phone started) into steps today. The first
     * reading of a day becomes that day's baseline; a counter that went backwards means a restart.
     */
    suspend fun stepsToday(epochDay: Long, counter: Float): Int {
        var result = 0
        context.winterArcStore.edit { p ->
            val day = p[Keys.stepDay]
            var base = p[Keys.stepBase] ?: counter
            val last = p[Keys.stepLast] ?: counter
            if (day != epochDay) {
                // New day: steps taken since the last reading of the previous day are counted today.
                base = if (counter >= last) last else 0f
                p[Keys.stepDay] = epochDay
            } else if (counter < last) {
                // The phone restarted: the counter began again from zero.
                base = base - last
            }
            p[Keys.stepBase] = base
            p[Keys.stepLast] = counter
            result = (counter - base).toInt().coerceAtLeast(0)
        }
        return result
    }

    suspend fun clear() = context.winterArcStore.edit { it.clear() }
}
