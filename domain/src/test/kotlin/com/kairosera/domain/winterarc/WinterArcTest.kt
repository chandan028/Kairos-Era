package com.kairosera.domain.winterarc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class WinterArcTest {
    private val start = LocalDate.of(2026, 10, 5)
    private val arc = WinterArc(startDate = start)

    @Test fun dayNumberCountsFromStart() {
        assertEquals(1, arc.dayNumber(start, start))
        assertEquals(27, arc.dayNumber(start.plusDays(26), start.plusDays(26)))
        assertEquals(90, arc.dayNumber(start.plusDays(89), start.plusDays(89)))
        assertEquals(90, arc.dayNumber(start.plusDays(120), start.plusDays(120)))
        assertEquals(LocalDate.of(2027, 1, 2), arc.endDate(start))
    }

    @Test fun beforeStartIsNotDayOne() {
        val today = start.minusDays(4)
        assertEquals(-3, arc.rawDayNumber(today, today))
        assertFalse(arc.hasStarted(today))
        assertFalse(arc.counts(today, today))
    }

    @Test fun pauseFreezesTheCountAndMovesTheEnd() {
        val pausedOn = start.plusDays(10) // day 11
        val paused = arc.paused(pausedOn)
        val today = pausedOn.plusDays(3)
        assertTrue(paused.isPausedOn(today, today))
        assertFalse(paused.counts(today, today))
        val resumed = paused.resumed(today)
        // Paused 11th..13th Oct+, so the resume day is day 11 again.
        assertEquals(11, resumed.dayNumber(today, today))
        assertEquals(arc.endDate(start).plusDays(3), resumed.endDate(today))
        assertEquals(ArcStatus.ACTIVE, resumed.status)
        assertEquals(resumed.pauses, WinterArc.decodePauses(WinterArc.encodePauses(resumed.pauses)))
    }

    @Test fun resumingOnTheSameDayCancelsThePause() {
        val d = start.plusDays(3)
        val back = arc.paused(d).resumed(d)
        assertTrue(back.pauses.isEmpty())
        assertEquals(4, back.dayNumber(d, d))
    }

    @Test fun waterAndBinaryRules() {
        val habits = HabitKind.entries.map { Habit(it) }
        val day = DayInputs(
            date = start,
            logs = mapOf(
                "WATER" to HabitLog("WATER", start, value = 3000.0),
                "STEPS" to HabitLog("STEPS", start, value = 6240.0),
                "ZERO_SUGAR" to HabitLog("ZERO_SUGAR", start, completed = true),
                "WAKE_EARLY" to HabitLog("WAKE_EARLY", start, value = 4 * 60 + 32.0),
            ),
            readingMinutes = 20,
            studyTasksDone = 2, studyTasksTotal = 4,
            focusMinutes = 90, focusSessions = 1,
            spoke = true,
        )
        val s = HabitRules.summarize(habits, day)
        assertEquals(10, s.totalHabits)
        // water, zero sugar, wake 4:32, speak
        assertEquals(4, s.completedHabits)
        assertEquals(40, s.completionPercentage)
        val steps = s.habits.first { it.kind == HabitKind.STEPS }
        assertEquals(0.624f, steps.progress, 0.001f)
        assertFalse(HabitRules.isComplete(s))
    }

    @Test fun lateWakeDoesNotCount() {
        val late = HabitRules.evaluate(Habit(HabitKind.WAKE_EARLY), DayInputs(start, logs = mapOf("WAKE_EARLY" to HabitLog("WAKE_EARLY", start, value = 5 * 60 + 27.0))))
        assertFalse(late.done)
        assertTrue(late.logged)
        val exactlyFive = HabitRules.evaluate(Habit(HabitKind.WAKE_EARLY), DayInputs(start, logs = mapOf("WAKE_EARLY" to HabitLog("WAKE_EARLY", start, value = 300.0))))
        assertFalse(exactlyFive.done)
    }

    @Test fun studyWithoutPlanUsesMinutes() {
        val h = Habit(HabitKind.STUDY, target = 60.0)
        assertTrue(HabitRules.evaluate(h, DayInputs(start, studyMinutes = 75)).done)
        assertFalse(HabitRules.evaluate(h, DayInputs(start, studyMinutes = 75, studyTasksTotal = 3, studyTasksDone = 2)).done)
    }

    @Test fun disabledHabitsLeaveTheTotal() {
        val habits = HabitKind.entries.map { Habit(it, active = it != HabitKind.COLD_SHOWER) }
        assertEquals(9, HabitRules.summarize(habits, DayInputs(start)).totalHabits)
        assertEquals(8, HabitRules.completeThreshold(10))
        assertEquals(8, HabitRules.completeThreshold(9))
        assertEquals(1, HabitRules.completeThreshold(1))
    }

    @Test fun streaksSkipAnOpenToday() {
        val dates = (0L..9L).map { start.plusDays(it) }
        val done = setOf(0, 1, 2, 4, 5, 6, 7, 8).map { start.plusDays(it.toLong()) }.toSet()
        val today = start.plusDays(9)
        val s = ArcStats.streaks(dates, { it in done }, today)
        assertEquals(5, s.current)
        assertEquals(5, s.longest)
        val broken = ArcStats.streaks(dates, { it in done && it != start.plusDays(8) }, today)
        assertEquals(0, broken.current)
    }

    @Test fun topicsDoNotRepeatUntilThePoolIsUsed() {
        val pool = (1..50).map { "Topic $it" }
        val used = ArrayList<String>()
        val r = Random(7)
        repeat(50) { used += TopicPicker.pick(pool, used, r)!! }
        assertEquals(50, used.toSet().size)
        val next = TopicPicker.pick(pool, used, r)!!
        assertFalse(next in used.takeLast(16))
        assertNotEquals("Topic 1", TopicPicker.pick(listOf("Topic 1", "Topic 2"), emptyList(), r, avoid = "Topic 1"))
    }

    @Test fun dayStates() {
        val today = start.plusDays(5)
        val full = DailySummary(start, 8, 10, emptyList())
        val some = DailySummary(start, 3, 10, emptyList())
        assertEquals(DayState.COMPLETE, HabitRules.stateOf(arc, start, today, full))
        assertEquals(DayState.PARTIAL, HabitRules.stateOf(arc, start, today, some))
        assertEquals(DayState.MISSED, HabitRules.stateOf(arc, start, today, null))
        assertEquals(DayState.TODAY, HabitRules.stateOf(arc, today, today, some))
        assertEquals(DayState.FUTURE, HabitRules.stateOf(arc, today.plusDays(1), today, null))
        assertEquals(DayState.OUTSIDE, HabitRules.stateOf(arc, start.minusDays(1), today, null))
    }
}
