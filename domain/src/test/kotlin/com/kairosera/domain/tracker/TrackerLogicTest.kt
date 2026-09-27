package com.kairosera.domain.tracker

import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.reading.ReadingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class TrackerLogicTest {
    private val today = LocalDate.of(2026, 9, 23) // Wednesday

    private fun tracker(vararg fields: TrackerField, frequency: TrackerFrequency = TrackerFrequency.Daily) =
        Tracker(id = 1, name = "t", fields = fields.toList(), frequency = frequency)

    private fun entry(vararg values: FieldValue) = TrackerEntry(1, today, values.associateBy { it.fieldId })

    @Test fun checkboxScoresDone() {
        val t = tracker(TrackerField(id = 1, label = "Workout", type = MeasurementType.CHECKBOX))
        assertTrue(TrackerScoring.score(t, entry(FieldValue(1, number = 1.0))).done)
        assertFalse(TrackerScoring.score(t, entry()).done)
        assertFalse(TrackerScoring.score(t, null).done)
    }

    @Test fun numberAgainstTargetIsPartial() {
        val t = tracker(TrackerField(id = 1, label = "Steps", type = MeasurementType.NUMBER, target = 8000.0))
        val s = TrackerScoring.score(t, entry(FieldValue(1, number = 6000.0)))
        assertEquals(0.75, s.fraction, 1e-9)
        assertFalse(s.done)
        assertTrue(TrackerScoring.score(t, entry(FieldValue(1, number = 9000.0))).done)
    }

    @Test fun checklistCountsCheckedItems() {
        val t = tracker(TrackerField(id = 1, label = "Java", type = MeasurementType.CHECKLIST, options = listOf("List", "Set", "Map", "Problems")))
        val s = TrackerScoring.score(t, entry(FieldValue(1, checked = setOf(0, 1, 9))))
        assertEquals(0.5, s.fraction, 1e-9) // index 9 does not exist and is ignored
    }

    @Test fun textFieldsNeverCountButMainGoalDoes() {
        val t = tracker(
            TrackerField(id = 1, label = "Study", type = MeasurementType.DURATION, target = 60.0),
            TrackerField(id = 2, label = "Water", type = MeasurementType.DECIMAL, target = 2.5),
            TrackerField(id = 3, label = "Notes", type = MeasurementType.TEXT),
        )
        val s = TrackerScoring.score(t, entry(FieldValue(1, number = 60.0), FieldValue(3, text = "ok")))
        assertTrue("main goal met counts as done", s.done)
        assertEquals(0.5, s.fraction, 1e-9)
    }

    @Test fun disabledFieldsAreIgnored() {
        val t = tracker(
            TrackerField(id = 1, label = "Workout", type = MeasurementType.CHECKBOX),
            TrackerField(id = 2, label = "Weight", type = MeasurementType.DECIMAL, enabled = false),
        )
        assertEquals(1.0, TrackerScoring.score(t, entry(FieldValue(1, number = 1.0))).fraction, 1e-9)
    }

    @Test fun dailyStreakIgnoresTodayUntilDone() {
        val done = (1L..5L).map { today.minusDays(it) }.toSet()
        val s = TrackerStatsCalculator.compute(TrackerFrequency.Daily, today.minusDays(30), done, done, today)
        assertEquals(5, s.currentStreak)
        assertFalse(s.missedYesterday)
        val withToday = TrackerStatsCalculator.compute(TrackerFrequency.Daily, today.minusDays(30), done + today, done, today)
        assertEquals(6, withToday.currentStreak)
    }

    @Test fun missedYesterdayEndsCurrentButKeepsBest() {
        val done = (2L..8L).map { today.minusDays(it) }.toSet()
        val s = TrackerStatsCalculator.compute(TrackerFrequency.Daily, today.minusDays(30), done, done, today)
        assertEquals(0, s.currentStreak)
        assertEquals(7, s.bestStreak)
        assertTrue(s.missedYesterday)
    }

    @Test fun selectedDaysSkipNonDueDays() {
        val mwf = TrackerFrequency.SelectedDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        // Mon 21, Fri 18, Wed 16 done; Tue/Thu not due.
        val done = setOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 16))
        val s = TrackerStatsCalculator.compute(mwf, LocalDate.of(2026, 9, 14), done, done, today)
        assertEquals(3, s.currentStreak)
        assertFalse(s.missedYesterday) // Tuesday was not due
    }

    @Test fun weeklyTargetCountsWeeks() {
        val freq = TrackerFrequency.TimesPerWeek(3)
        val lastWeek = setOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 18))
        val weekBefore = setOf(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 11))
        val thisWeek = setOf(LocalDate.of(2026, 9, 21))
        val s = TrackerStatsCalculator.compute(freq, LocalDate.of(2026, 9, 1), lastWeek + weekBefore + thisWeek, emptySet(), today)
        assertEquals(StreakUnit.WEEK, s.unit)
        assertEquals(2, s.currentStreak) // the unfinished current week does not break it
    }

    @Test fun studyProgressUsesLeavesOnly() {
        val topics = listOf(
            StudyTopic(id = 1, trackerId = 1, title = "Java Core"),
            StudyTopic(id = 2, trackerId = 1, parentId = 1, title = "Collections", status = TopicStatus.DONE),
            StudyTopic(id = 3, trackerId = 1, parentId = 1, title = "Generics", status = TopicStatus.LEARNING),
            StudyTopic(id = 4, trackerId = 1, parentId = 1, title = "Streams"),
        )
        val s = StudyProgress.summarize(topics)
        assertEquals(3, s.leafTopics)
        assertEquals(1, s.done)
        assertEquals(1, s.inProgress)
        assertEquals(listOf(0, 1, 1, 1), StudyProgress.flatten(topics).map { it.second })
    }

    @Test fun completingTargetNeverMarksTopicDone() {
        val t = StudyTopic(id = 1, trackerId = 1, title = "HashMap")
        val after = StudyProgress.onTargetCompleted(t, Instant.EPOCH)
        assertEquals(TopicStatus.LEARNING, after.status)
        assertEquals(1, after.practiceDone)
        val review = StudyProgress.onTargetCompleted(t.copy(status = TopicStatus.REVIEWING), Instant.EPOCH)
        assertEquals(TopicStatus.REVIEWING, review.status)
    }

    @Test fun flattenSurvivesCycles() {
        val topics = listOf(
            StudyTopic(id = 1, trackerId = 1, parentId = 2, title = "a"),
            StudyTopic(id = 2, trackerId = 1, parentId = 1, title = "b"),
        )
        assertEquals(2, StudyProgress.flatten(topics).size)
    }

    @Test fun readingSessionMovesBookmarkWithinBounds() {
        val book = Book(title = "Atomic Habits", totalPages = 320, currentPage = 300)
        val after = ReadingMath.afterSession(book, 50, today, Instant.EPOCH)
        assertEquals(320, after.currentPage)
        assertEquals(BookStatus.READING, after.status)
        assertEquals(today, after.startDate)
        assertEquals(1.0, after.progress, 1e-9)
    }

    @Test fun pagesPerDayToTarget() {
        val book = Book(title = "b", totalPages = 320, currentPage = 120, targetDate = today.plusDays(9))
        assertEquals(20, ReadingMath.pagesPerDayToTarget(book, today))
    }
}
