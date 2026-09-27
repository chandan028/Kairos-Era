package com.kairosera.domain.time

import com.kairosera.domain.model.NotificationStatus
import com.kairosera.domain.model.OccurrenceStatus
import com.kairosera.domain.model.Reminder
import com.kairosera.domain.model.ReminderTiming
import com.kairosera.domain.model.ScheduledNotification
import com.kairosera.domain.model.Task
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Decides which notifications should exist, given what the database says.
 *
 * It is a pure function of (tasks, reminders, occurrence states, existing notification rows, now, zone),
 * so it can run after a reboot, a time or time-zone change, an app update, or on every app start,
 * and always converges on the same answer. Running it twice never creates duplicates: each
 * (reminder, occurrence date) pair maps to at most one row, and the row id is the notification id.
 *
 * Reminder times are *floating local times*: "08:00" means 08:00 wherever the phone is. After a
 * time-zone change the trigger instants are recomputed with the new zone.
 */
object ReminderPlanner {

    /** A reminder whose time passed while the phone was off still fires if it is at most this late. */
    val MISSED_GRACE: Duration = Duration.ofHours(12)

    private const val MAX_DAY_OFFSET = 7L

    data class Input(
        val tasks: List<Task>,
        /** Done/skipped states keyed by task id. Only recent dates are needed. */
        val occurrenceStates: Map<Long, Map<LocalDate, OccurrenceStatus>>,
        /** Every notification row for recent and future dates, in any status. */
        val existing: List<ScheduledNotification>,
        val now: Instant,
        val zone: ZoneId,
    )

    /** A row to insert (id == null) or update (id != null), then arm an alarm for. */
    data class Arm(
        val id: Long?,
        val reminderId: Long,
        val taskId: Long,
        val occurrenceDate: LocalDate,
        val triggerAt: Instant,
        val status: NotificationStatus,
    )

    data class Plan(
        /** Rows whose alarm must be (re)armed. Arming the same id twice is harmless. */
        val arm: List<Arm>,
        /** Rows to mark CANCELLED; their alarm and any posted notification must be removed. */
        val cancel: List<ScheduledNotification>,
        /** SHOWING rows that must be visible; re-post if the system dropped them (e.g. after reboot). */
        val ensureVisible: List<ScheduledNotification>,
    )

    fun plan(input: Input): Plan {
        val tasksById = input.tasks.associateBy { it.id }
        val remindersById = input.tasks.flatMap { t -> t.reminders.map { it.copy(taskId = t.id) } }.associateBy { it.id }
        val rowsByReminder = input.existing.groupBy { it.reminderId }

        val arm = mutableListOf<Arm>()
        val cancel = mutableListOf<ScheduledNotification>()
        val visible = mutableListOf<ScheduledNotification>()

        // Rows whose reminder or task no longer exists (deleted, archived, reminder removed).
        for ((reminderId, rows) in rowsByReminder) {
            val reminder = remindersById[reminderId]
            val task = reminder?.let { tasksById[it.taskId] }
            if (reminder == null || task == null || !task.isActive) {
                cancel += rows.filter { !it.isTerminal }
            }
        }

        for (reminder in remindersById.values) {
            val task = tasksById.getValue(reminder.taskId)
            if (!task.isActive) continue
            val rows = rowsByReminder[reminder.id].orEmpty()
            val states = input.occurrenceStates[task.id].orEmpty()

            fun occurrenceStillOpen(date: LocalDate): Boolean =
                RecurrenceEngine.occursOn(task.date, task.repeatRule, date) && states[date] == null &&
                    triggerFor(task, reminder, date, input.zone) != null

            // Existing SHOWING / SNOOZED rows: keep while the occurrence is still open.
            for (row in rows.filter { it.status == NotificationStatus.SHOWING || it.status == NotificationStatus.SNOOZED }) {
                if (!occurrenceStillOpen(row.occurrenceDate)) {
                    cancel += row
                } else if (row.status == NotificationStatus.SHOWING) {
                    visible += row
                } else {
                    arm += Arm(row.id, row.reminderId, row.taskId, row.occurrenceDate, row.triggerAt, row.status)
                }
            }

            val handledDates = rows.filter { it.status != NotificationStatus.SCHEDULED && it.status != NotificationStatus.CANCELLED }
                .map { it.occurrenceDate }.toSet()
            val next = nextDue(task, reminder, states, handledDates, input.now, input.zone)
            val scheduledRows = rows.filter { it.status == NotificationStatus.SCHEDULED }

            if (next == null) {
                cancel += scheduledRows
                continue
            }
            val (date, trigger) = next
            val existingForDate = scheduledRows.firstOrNull { it.occurrenceDate == date }
                ?: rows.firstOrNull { it.occurrenceDate == date && it.status == NotificationStatus.CANCELLED }
            arm += Arm(existingForDate?.id, reminder.id, task.id, date, trigger, NotificationStatus.SCHEDULED)
            cancel += scheduledRows.filter { it.occurrenceDate != date }
        }
        return Plan(arm = arm, cancel = cancel.distinctBy { it.id }, ensureVisible = visible)
    }

    /**
     * The next occurrence whose reminder should be armed. A trigger in the past (within [MISSED_GRACE])
     * is returned as-is; the scheduler fires it immediately.
     */
    fun nextDue(
        task: Task,
        reminder: Reminder,
        states: Map<LocalDate, OccurrenceStatus>,
        handledDates: Set<LocalDate>,
        now: Instant,
        zone: ZoneId,
    ): Pair<LocalDate, Instant>? {
        val earliest = now.minus(MISSED_GRACE)
        // Occurrence dates whose trigger could be >= earliest start a few days back (reminders may be set days before).
        val scanFrom = earliest.atZone(zone).toLocalDate().minusDays(MAX_DAY_OFFSET)
        val candidates = if (task.repeatRule == null) {
            sequenceOf(task.date)
        } else {
            RecurrenceEngine.occurrences(task.date, task.repeatRule).dropWhile { it < scanFrom }
        }
        for (date in candidates) {
            if (date < scanFrom) continue
            if (states[date] != null || date in handledDates) continue
            val trigger = triggerFor(task, reminder, date, zone) ?: continue
            if (trigger < earliest) continue
            return date to trigger
        }
        return null
    }

    /** The instant a reminder fires for one occurrence, or null if the timing is invalid for this task. */
    fun triggerFor(task: Task, reminder: Reminder, date: LocalDate, zone: ZoneId): Instant? {
        val local: LocalDateTime = when (val t = reminder.timing) {
            is ReminderTiming.AtTime -> {
                if (t.dayOffset !in -MAX_DAY_OFFSET..MAX_DAY_OFFSET) return null
                date.plusDays(t.dayOffset.toLong()).atTime(t.time)
            }
            is ReminderTiming.BeforeStart -> {
                val start = task.startTime ?: return null
                if (t.minutes < 0) return null
                date.atTime(start).minusMinutes(t.minutes.toLong())
            }
        }
        // In a DST gap (e.g. 02:30 that never happens) this shifts forward by the gap length;
        // in an overlap it picks the earlier offset, so the reminder fires once, the first time.
        return ZonedDateTime.ofLocal(local, zone, null).toInstant()
    }
}
