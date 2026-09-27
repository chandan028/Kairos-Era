package com.kairosera.domain.time

import com.kairosera.domain.model.Frequency
import com.kairosera.domain.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.DayOfWeek.FRIDAY
import java.time.LocalDate

class RecurrenceEngineTest {
    private val start = LocalDate.of(2026, 9, 23) // a Wednesday

    @Test fun oneTimeTaskOccursOnlyOnItsDate() {
        assertTrue(RecurrenceEngine.occursOn(start, null, start))
        assertFalse(RecurrenceEngine.occursOn(start, null, start.plusDays(1)))
        assertEquals(listOf(start), RecurrenceEngine.occurrencesBetween(start, null, start.minusDays(3), start.plusDays(3)))
    }

    @Test fun dailyWithIntervalSkipsDays() {
        val rule = RepeatRule(Frequency.DAILY, interval = 2)
        val dates = RecurrenceEngine.occurrencesBetween(start, rule, start, start.plusDays(6))
        assertEquals(listOf(0L, 2, 4, 6).map { start.plusDays(it) }, dates)
        assertFalse(RecurrenceEngine.occursOn(start, rule, start.plusDays(1)))
        assertFalse(RecurrenceEngine.occursOn(start, rule, start.minusDays(2)))
    }

    @Test fun weeklyOnSelectedWeekdays() {
        val rule = RepeatRule(Frequency.WEEKLY, weekdays = setOf(MONDAY, WEDNESDAY, FRIDAY))
        val dates = RecurrenceEngine.occurrencesBetween(start, rule, start, start.plusDays(7))
        assertEquals(
            listOf(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30)),
            dates,
        )
        dates.forEach { assertTrue(RecurrenceEngine.occursOn(start, rule, it)) }
    }

    @Test fun everyOtherWeek() {
        val rule = RepeatRule(Frequency.WEEKLY, interval = 2)
        assertTrue(RecurrenceEngine.occursOn(start, rule, start.plusWeeks(2)))
        assertFalse(RecurrenceEngine.occursOn(start, rule, start.plusWeeks(1)))
    }

    @Test fun monthlyOn31stClampsToMonthEnd() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val rule = RepeatRule(Frequency.MONTHLY)
        val dates = RecurrenceEngine.occurrencesBetween(jan31, rule, jan31, LocalDate.of(2026, 4, 30))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
            dates,
        )
        assertTrue(RecurrenceEngine.occursOn(jan31, rule, LocalDate.of(2026, 2, 28)))
        assertFalse(RecurrenceEngine.occursOn(jan31, rule, LocalDate.of(2026, 3, 30)))
    }

    @Test fun yearlyOnLeapDay() {
        val leap = LocalDate.of(2028, 2, 29)
        val rule = RepeatRule(Frequency.YEARLY)
        assertTrue(RecurrenceEngine.occursOn(leap, rule, LocalDate.of(2029, 2, 28)))
        assertTrue(RecurrenceEngine.occursOn(leap, rule, LocalDate.of(2032, 2, 29)))
    }

    @Test fun countLimitsOccurrences() {
        val rule = RepeatRule(Frequency.DAILY, occurrenceCount = 3)
        assertTrue(RecurrenceEngine.occursOn(start, rule, start.plusDays(2)))
        assertFalse(RecurrenceEngine.occursOn(start, rule, start.plusDays(3)))
        assertEquals(start.plusDays(2), RecurrenceEngine.lastPossibleDate(start, rule))
        assertNull(RecurrenceEngine.nextOnOrAfter(start, rule, start.plusDays(3)))
    }

    @Test fun endDateStopsSeries() {
        val rule = RepeatRule(Frequency.DAILY, endDate = start.plusDays(1))
        assertEquals(2, RecurrenceEngine.occurrencesBetween(start, rule, start, start.plusDays(30)).size)
    }

    @Test fun historyIsPreservedAcrossDays() {
        // Daily reset never removes earlier occurrences: yesterday is still an occurrence.
        val rule = RepeatRule(Frequency.DAILY)
        assertTrue(RecurrenceEngine.occursOn(start, rule, start.plusDays(10)))
        assertTrue(RecurrenceEngine.occursOn(start, rule, start.plusDays(9)))
    }

    @Test fun invalidRuleIsRejected() {
        try {
            RepeatRule(Frequency.DAILY, interval = 0)
            throw AssertionError("expected failure")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("interval"))
        }
    }
}
