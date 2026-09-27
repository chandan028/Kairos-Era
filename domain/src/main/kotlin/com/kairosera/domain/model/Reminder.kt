package com.kairosera.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** When a reminder fires, relative to an occurrence of its task. */
sealed interface ReminderTiming {
    /** At a fixed local time on the occurrence date (+[dayOffset] days, e.g. -1 = the evening before). */
    data class AtTime(val time: LocalTime, val dayOffset: Int = 0) : ReminderTiming

    /** A number of minutes before the task's start time. Only valid for timed tasks. */
    data class BeforeStart(val minutes: Int) : ReminderTiming
}

/**
 * Sounds are referenced by a stable key, never by an Android resource id, because
 * resource ids are not stable across app upgrades.
 */
enum class ReminderSound(val key: String) {
    DEFAULT("default"),
    KAIROS_BELL("kairos_bell"),
    SOFT_CHIME("soft_chime"),
    FOCUS("focus"),
    CUSTOM("custom"),
    SILENT("silent");

    companion object {
        fun fromKey(key: String?): ReminderSound = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

data class Reminder(
    val id: Long = 0,
    val taskId: Long = 0,
    val timing: ReminderTiming,
    /** Persistent reminders stay in the shade until the user acts (subject to Android's own controls). */
    val persistent: Boolean = false,
    val sound: ReminderSound = ReminderSound.DEFAULT,
)

enum class NotificationStatus {
    /** Alarm armed, waiting to fire. */
    SCHEDULED,

    /** User snoozed; alarm armed for the snooze time. */
    SNOOZED,

    /** Notification is posted and waiting for the user. */
    SHOWING,

    /** User marked the task done from the notification or the app. */
    DONE,

    /** User swiped the notification away. Respected: never re-posted. */
    DISMISSED,

    /** No longer valid (task changed, deleted or completed elsewhere). */
    CANCELLED,
}

/**
 * The mapping Reminder -> notification id -> scheduled time -> status.
 * [id] is the Android notification id and the alarm request code, so the same
 * (reminder, occurrence) pair always maps to the same alarm and the same notification.
 */
data class ScheduledNotification(
    val id: Long = 0,
    val reminderId: Long,
    val taskId: Long,
    val occurrenceDate: LocalDate,
    val triggerAt: Instant,
    val status: NotificationStatus,
    val updatedAt: Instant,
) {
    val isArmed: Boolean get() = status == NotificationStatus.SCHEDULED || status == NotificationStatus.SNOOZED
    val isTerminal: Boolean
        get() = status == NotificationStatus.DONE || status == NotificationStatus.DISMISSED ||
            status == NotificationStatus.CANCELLED
}
