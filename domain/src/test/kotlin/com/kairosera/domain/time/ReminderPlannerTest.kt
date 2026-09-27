package com.kairosera.domain.time

import com.kairosera.domain.model.Frequency
import com.kairosera.domain.model.NotificationStatus
import com.kairosera.domain.model.OccurrenceStatus
import com.kairosera.domain.model.Reminder
import com.kairosera.domain.model.ReminderTiming
import com.kairosera.domain.model.RepeatRule
import com.kairosera.domain.model.ScheduledNotification
import com.kairosera.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderPlannerTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)

    private fun at(date: LocalDate, h: Int, m: Int, zone: ZoneId = kolkata): Instant =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant()

    private val java = Task(
        id = 1,
        title = "Java — Collections",
        date = today,
        startTime = LocalTime.of(8, 0),
        repeatRule = RepeatRule(Frequency.DAILY),
        reminders = listOf(Reminder(id = 10, taskId = 1, timing = ReminderTiming.BeforeStart(0))),
    )

    /** Applies a plan the way the real scheduler does, giving new rows sequential ids. */
    private fun apply(plan: ReminderPlanner.Plan, rows: List<ScheduledNotification>, now: Instant): List<ScheduledNotification> {
        val byId = rows.associateBy { it.id }.toMutableMap()
        plan.cancel.forEach { byId[it.id] = it.copy(status = NotificationStatus.CANCELLED) }
        var nextId = (byId.keys.maxOrNull() ?: 0) + 1
        plan.arm.forEach { a ->
            val id = a.id ?: nextId++
            byId[id] = ScheduledNotification(id, a.reminderId, a.taskId, a.occurrenceDate, a.triggerAt, a.status, now)
        }
        return byId.values.sortedBy { it.id }
    }

    private fun plan(tasks: List<Task>, rows: List<ScheduledNotification>, now: Instant, zone: ZoneId = kolkata,
                     states: Map<Long, Map<LocalDate, OccurrenceStatus>> = emptyMap()) =
        ReminderPlanner.plan(ReminderPlanner.Input(tasks, states, rows, now, zone))

    @Test fun schedulesNextOccurrence() {
        val now = at(today, 7, 0)
        val p = plan(listOf(java), emptyList(), now)
        assertEquals(1, p.arm.size)
        assertEquals(today, p.arm.single().occurrenceDate)
        assertEquals(at(today, 8, 0), p.arm.single().triggerAt)
    }

    @Test fun runningTwiceNeverDuplicates() {
        val now = at(today, 7, 0)
        val rows1 = apply(plan(listOf(java), emptyList(), now), emptyList(), now)
        val p2 = plan(listOf(java), rows1, now)
        val rows2 = apply(p2, rows1, now)
        assertEquals(1, rows2.size)
        assertEquals(rows1.single().id, p2.arm.single().id)
        assertTrue(p2.cancel.isEmpty())
    }

    @Test fun afterRebootMissedReminderFiresOnceThenMovesOn() {
        // Phone was off at 08:00 and boots at 09:30: the reminder is still due (within grace).
        val boot = at(today, 9, 30)
        val p = plan(listOf(java), emptyList(), boot)
        assertEquals(today, p.arm.single().occurrenceDate)
        assertTrue(p.arm.single().triggerAt < boot)

        // Once it has been shown, the next plan schedules tomorrow and keeps today's visible.
        val shown = apply(p, emptyList(), boot).map { it.copy(status = NotificationStatus.SHOWING) }
        val p2 = plan(listOf(java), shown, boot)
        assertEquals(today.plusDays(1), p2.arm.single().occurrenceDate)
        assertEquals(shown.single().id, p2.ensureVisible.single().id)
    }

    @Test fun veryOldMissedReminderIsNotFiredLate() {
        val muchLater = at(today, 23, 0)
        val p = plan(listOf(java), emptyList(), muchLater)
        assertEquals(today.plusDays(1), p.arm.single().occurrenceDate)
    }

    @Test fun timeZoneChangeKeepsIdAndMovesInstant() {
        val now = at(today, 6, 0)
        val rows = apply(plan(listOf(java), emptyList(), now), emptyList(), now)
        // Travel to London: 08:00 local now means 08:00 London time.
        val p = plan(listOf(java), rows, now, zone = london)
        val armed = p.arm.single()
        assertEquals(rows.single().id, armed.id)
        assertEquals(at(today, 8, 0, london), armed.triggerAt)
    }

    @Test fun clockMovedForwardFiresSkippedReminder() {
        val before = at(today, 7, 0)
        val rows = apply(plan(listOf(java), emptyList(), before), emptyList(), before)
        val jumped = at(today, 8, 45)
        val p = plan(listOf(java), rows, jumped)
        assertEquals(rows.single().id, p.arm.single().id)
        assertEquals(today, p.arm.single().occurrenceDate)
    }

    @Test fun completingOccurrenceCancelsItsReminder() {
        val now = at(today, 7, 0)
        val rows = apply(plan(listOf(java), emptyList(), now), emptyList(), now)
        val p = plan(listOf(java), rows, now, states = mapOf(1L to mapOf(today to OccurrenceStatus.DONE)))
        assertEquals(rows.single().id, p.cancel.single().id)
        assertEquals(today.plusDays(1), p.arm.single().occurrenceDate)
    }

    @Test fun deletedTaskBeforeNotificationIsCancelled() {
        val now = at(today, 7, 0)
        val rows = apply(plan(listOf(java), emptyList(), now), emptyList(), now)
        val p = plan(listOf(java.copy(deletedAt = now)), rows, now)
        assertEquals(rows.single().id, p.cancel.single().id)
        assertTrue(p.arm.isEmpty())
        val p2 = plan(emptyList(), rows, now)
        assertEquals(rows.single().id, p2.cancel.single().id)
    }

    @Test fun dismissedReminderIsNeverRepostedOrRescheduledForSameDay() {
        val now = at(today, 8, 5)
        val dismissed = listOf(ScheduledNotification(5, 10, 1, today, at(today, 8, 0), NotificationStatus.DISMISSED, now))
        val p = plan(listOf(java), dismissed, now)
        assertEquals(today.plusDays(1), p.arm.single().occurrenceDate)
        assertTrue(p.ensureVisible.isEmpty())
    }

    @Test fun snoozedRowIsKeptWithItsOwnTime() {
        val now = at(today, 8, 5)
        val snoozeUntil = at(today, 8, 15)
        val rows = listOf(ScheduledNotification(5, 10, 1, today, snoozeUntil, NotificationStatus.SNOOZED, now))
        val p = plan(listOf(java), rows, now)
        val kept = p.arm.first { it.id == 5L }
        assertEquals(snoozeUntil, kept.triggerAt)
        assertEquals(NotificationStatus.SNOOZED, kept.status)
        assertTrue(p.arm.any { it.occurrenceDate == today.plusDays(1) })
    }

    @Test fun dstGapShiftsForward() {
        val ny = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2027, 3, 14) // clocks jump 02:00 -> 03:00
        val t = java.copy(date = dstDay, startTime = LocalTime.of(2, 30), repeatRule = null)
        val trigger = ReminderPlanner.triggerFor(t, t.reminders.single(), dstDay, ny)
        assertEquals(LocalDateTime.of(dstDay, LocalTime.of(3, 30)).atZone(ny).toInstant(), trigger)
    }

    @Test fun beforeStartReminderOnAllDayTaskIsIgnored() {
        val t = java.copy(startTime = null)
        assertNull(ReminderPlanner.triggerFor(t, t.reminders.single(), today, kolkata))
        assertTrue(plan(listOf(t), emptyList(), at(today, 6, 0)).arm.isEmpty())
    }

    @Test fun eveningBeforeReminderUsesDayOffset() {
        val t = java.copy(reminders = listOf(Reminder(10, 1, ReminderTiming.AtTime(LocalTime.of(21, 0), dayOffset = -1))), repeatRule = null)
        val trigger = ReminderPlanner.triggerFor(t, t.reminders.single(), today, kolkata)
        assertEquals(at(today.minusDays(1), 21, 0), trigger)
    }

    @Test fun oneTimeTaskInThePastSchedulesNothing() {
        val t = java.copy(repeatRule = null, date = today.minusDays(3))
        assertTrue(plan(listOf(t), emptyList(), at(today, 7, 0)).arm.isEmpty())
    }
}
