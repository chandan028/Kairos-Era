package com.kairosera.domain.usecase

import com.kairosera.domain.model.Frequency
import com.kairosera.domain.model.OccurrenceState
import com.kairosera.domain.model.OccurrenceStatus
import com.kairosera.domain.model.Priority
import com.kairosera.domain.model.Reminder
import com.kairosera.domain.model.ReminderTiming
import com.kairosera.domain.model.RepeatRule
import com.kairosera.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class OccurrenceExpanderTest {
    private val d = LocalDate.of(2026, 9, 23)

    @Test fun expandsRecurringAndOneTimeTasksWithState() {
        val daily = Task(id = 1, title = "Walk", date = d.minusDays(5), startTime = LocalTime.of(7, 0), repeatRule = RepeatRule(Frequency.DAILY))
        val once = Task(id = 2, title = "Call bank", date = d, priority = Priority.HIGH)
        val trashed = Task(id = 3, title = "Old", date = d, deletedAt = Instant.EPOCH)
        val states = listOf(OccurrenceState(1, d, OccurrenceStatus.DONE, Instant.EPOCH))
        val map = OccurrenceExpander.expand(listOf(daily, once, trashed), states, d.minusDays(1), d)
        assertEquals(listOf("Walk"), map[d.minusDays(1)]!!.map { it.task.title })
        val today = map.getValue(d)
        assertEquals(listOf("Call bank", "Walk"), today.map { it.task.title }) // done items sink
        assertTrue(today.last().isDone)
        val progress = DaySummary.progress(today)
        assertEquals(1, progress.done)
        assertEquals(2, progress.total)
    }

    @Test fun skippedOccurrencesDoNotCountAgainstProgress() {
        val t = Task(id = 1, title = "Run", date = d, repeatRule = RepeatRule(Frequency.DAILY))
        val map = OccurrenceExpander.expand(listOf(t), listOf(OccurrenceState(1, d, OccurrenceStatus.SKIPPED, Instant.EPOCH)), d, d)
        assertEquals(DayProgress(0, 0), DaySummary.progress(map.getValue(d)))
    }

    @Test fun nextUpPrefersUpcomingTimedTasks() {
        fun t(id: Long, h: Int?) = Task(id = id, title = "t$id", date = d, startTime = h?.let { LocalTime.of(it, 0) })
        val occ = OccurrenceExpander.expand(listOf(t(1, 8), t(2, 18), t(3, null), t(4, 21)), emptyList(), d, d).getValue(d)
        val next = DaySummary.nextUp(occ, LocalTime.of(12, 0))
        assertEquals(listOf(2L, 4L, 3L, 1L), next.map { it.task.id })
    }

    @Test fun validatorRejectsBadTasks() {
        val bad = Task(
            title = "  ",
            date = d,
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(9, 0),
        )
        val errors = TaskValidator.validate(bad)
        assertTrue(TaskValidationError.EmptyTitle in errors)
        assertTrue(TaskValidationError.EndBeforeStart in errors)
        val allDayWithRelative = Task(title = "x", date = d, reminders = listOf(Reminder(timing = ReminderTiming.BeforeStart(10))))
        assertTrue(TaskValidationError.ReminderNeedsStartTime in TaskValidator.validate(allDayWithRelative))
    }
}
