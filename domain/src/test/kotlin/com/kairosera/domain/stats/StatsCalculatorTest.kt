package com.kairosera.domain.stats

import com.kairosera.domain.journal.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {
    private val today = LocalDate.of(2026, 9, 25) // a Friday
    private fun day(ago: Long, block: DayActivity.() -> DayActivity = { this }) = DayActivity(today.minusDays(ago)).block()

    @Test fun emptyRangeHasNoActivityButStillCountsDays() {
        val s = StatsCalculator.summarize((6L downTo 0L).map { day(it) }, today.minusDays(6), today)
        assertEquals(7, s.daysCounted)
        assertEquals(0, s.activeDays)
        assertEquals(0.0, s.consistency!!, 1e-9)
        assertTrue(!s.hasActivity)
    }

    @Test fun consistencyIsActiveDaysOverCountedDays() {
        val days = listOf(
            day(3) { copy(tasksDone = 1, tasksTotal = 2) },
            day(2),
            day(1) { copy(tasksTotal = 3) }, // planned but nothing done: not active
            day(0) { copy(journaled = true) },
        )
        val s = StatsCalculator.summarize(days, today.minusDays(3), today)
        assertEquals(4, s.daysCounted)
        assertEquals(2, s.activeDays)
        assertEquals(0.5, s.consistency!!, 1e-9)
        assertEquals(5, s.tasksPlanned)
        assertEquals(1, s.reflections)
    }

    @Test fun daysBeforeThePersonStartedAreNotCounted() {
        val days = (29L downTo 0L).map { day(it) { if (date.dayOfMonth % 2 == 0) copy(readingSessions = 1, pages = 5) else this } }
        val s = StatsCalculator.summarize(days, today.minusDays(29), today, startedOn = today.minusDays(4))
        assertEquals(5, s.daysCounted)
        assertEquals(2, s.activeDays) // 22 and 24 September
        assertEquals(10, s.pages)
    }

    @Test fun startingInTheFutureCountsNothing() {
        val s = StatsCalculator.summarize(emptyList(), today.minusDays(6), today, startedOn = today.plusDays(1))
        assertEquals(0, s.daysCounted)
        assertNull(s.consistency)
    }

    @Test fun futureDaysNeverCount() {
        val days = listOf(day(0) { copy(tasksDone = 1, tasksTotal = 1) }, DayActivity(today.plusDays(1), tasksDone = 5, tasksTotal = 5))
        val s = StatsCalculator.summarize(days, today, today)
        assertEquals(1, s.tasksDone)
        assertEquals(1, s.daysCounted)
    }

    @Test fun streaksForgiveAnUnfinishedToday() {
        val days = listOf(
            day(5) { copy(journaled = true) },
            day(4) { copy(readingSessions = 1, pages = 10) },
            day(3),
            day(2) { copy(studyTopics = 1) },
            day(1) { copy(tasksDone = 1, tasksTotal = 1) },
            day(0),
        )
        val s = StatsCalculator.summarize(days, today.minusDays(5), today)
        assertEquals(2, s.currentStreak)
        assertEquals(2, s.bestStreak)
        assertEquals(4, s.activeDays)
    }

    @Test fun levelsUseFixedSteps() {
        assertEquals(0, day(0).level)
        assertEquals(1, day(0) { copy(tasksDone = 2) }.level)
        assertEquals(2, day(0) { copy(tasksDone = 2, trackersLogged = 1, journaled = true) }.level)
        assertEquals(3, day(0) { copy(tasksDone = 6) }.level)
    }

    @Test fun planScoreIgnoresDaysWithoutAPlan() {
        assertNull(day(0).planScore)
        assertEquals(0.75, day(0) { copy(tasksDone = 1, tasksTotal = 1, trackersDue = 1, trackerProgress = 0.5) }.planScore!!, 1e-9)
    }

    @Test fun recordsFindPersonalBests() {
        val days = listOf(
            day(9) { copy(tasksDone = 1) },
            day(8) { copy(tasksDone = 7, readingSessions = 1, pages = 40, longestSession = 30) },
            day(7) { copy(readingSessions = 1, pages = 30) }, // same Mon-Sun week as 8 days ago: 70
            day(6),
            day(2) { copy(readingSessions = 1, pages = 60, longestSession = 95) },
            day(1) { copy(tasksDone = 2) },
        )
        val r = StatsCalculator.records(days, today.minusDays(9), today)
        assertEquals(Record(3, today.minusDays(9)), r.longestStreak)
        assertEquals(Record(7, today.minusDays(8)), r.mostTasksInDay)
        assertEquals(70, r.mostPagesInWeek!!.value)
        assertEquals(LocalDate.of(2026, 9, 14), r.mostPagesInWeek!!.date)
        assertEquals(Record(95, today.minusDays(2)), r.longestSession)
    }

    @Test fun momentumComparesTheLastTwoWeeks() {
        fun weeks(last: Int, before: Int) = (0L..13L).map { ago ->
            val active = if (ago < 7) ago < last else ago - 7 < before
            day(ago) { if (active) copy(tasksDone = 1) else this }
        }
        val started = today.minusDays(60)
        assertEquals(Momentum.Rising(5, 3), StatsCalculator.momentum(weeks(5, 3), today, started))
        assertEquals(Momentum.Steady(4), StatsCalculator.momentum(weeks(4, 4), today, started))
        assertEquals(Momentum.Easing(2), StatsCalculator.momentum(weeks(2, 5), today, started))
        assertEquals(Momentum.Quiet, StatsCalculator.momentum(weeks(0, 5), today, started))
        assertEquals(Momentum.JustStarted(2, 3), StatsCalculator.momentum(weeks(2, 0), today, today.minusDays(2)))
    }

    @Test fun averageMoodUsesOnlyDaysWithAMood() {
        val s = StatsCalculator.summarize(listOf(day(1) { copy(journaled = true, mood = Mood.GREAT) }, day(0) { copy(journaled = true, mood = Mood.OKAY) }, day(2)), today.minusDays(2), today)
        assertEquals(4.0, s.averageMood!!, 1e-9)
        assertEquals(2, s.moodDays)
    }
}
