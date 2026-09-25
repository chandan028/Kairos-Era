package com.kairosera.domain.activity

import com.kairosera.domain.journal.JournalEntry
import com.kairosera.domain.journal.Mood
import com.kairosera.domain.model.OccurrenceState
import com.kairosera.domain.model.OccurrenceStatus
import com.kairosera.domain.model.Task
import com.kairosera.domain.model.TaskOccurrence
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.ReadingSession
import com.kairosera.domain.tracker.FieldValue
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.StudyTarget
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerEntry
import com.kairosera.domain.tracker.TrackerField
import com.kairosera.domain.tracker.TrackerTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ActivityHistoryTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 9, 24)
    private val at = day.atTime(20, 30).toInstant(zone)
    private val created = day.minusDays(10).atStartOfDay().toInstant(zone)

    private val study = Tracker(1, "Java", "💻", template = TrackerTemplate.STUDY, createdAt = created,
        fields = listOf(TrackerField(11, "Time", MeasurementType.DURATION, target = 45.0)))
    private val fitness = Tracker(2, "Movement", "🏃", template = TrackerTemplate.FITNESS, createdAt = created,
        fields = listOf(TrackerField(21, "Steps", MeasurementType.NUMBER, unit = "steps", target = 8000.0), TrackerField(22, "Workout", MeasurementType.DURATION)))
    private val book = Book(5, "Atomic Habits")

    private fun build(
        plan: Map<LocalDate, List<TaskOccurrence>> = emptyMap(),
        trackers: List<Tracker> = listOf(study, fitness),
        entries: List<TrackerEntry> = emptyList(),
        targets: List<StudyTarget> = emptyList(),
        sessions: List<ReadingSession> = emptyList(),
        journal: List<JournalEntry> = emptyList(),
    ) = ActivityHistory.build(day.minusDays(1), day, plan, trackers, entries, targets, sessions, listOf(book), journal, zone)

    @Test fun everySourceBecomesOneActivityList() {
        val done = TaskOccurrence(Task(7, "Java collections", date = day), day, OccurrenceState(7, day, OccurrenceStatus.DONE, at))
        val open = TaskOccurrence(Task(8, "Project work", date = day), day, null)
        val h = build(
            plan = mapOf(day to listOf(done, open)),
            entries = listOf(
                TrackerEntry(1, day, mapOf(11L to FieldValue(11, number = 45.0)), at),
                TrackerEntry(2, day, mapOf(21L to FieldValue(21, number = 6432.0), 22L to FieldValue(22, number = 30.0)), at),
            ),
            targets = listOf(StudyTarget(3, 1, day, title = "HashMap", done = true), StudyTarget(4, 1, day, title = "TreeMap")),
            sessions = listOf(ReadingSession(9, 5, day, pages = 20, minutes = 25, createdAt = at)),
            journal = listOf(JournalEntry(1, day, Mood.GOOD, text = "Finally understood HashMap", updatedAt = at)),
        )
        val d = h.day(day)!!
        assertEquals(
            listOf(ActivityType.TASK_COMPLETED, ActivityType.STUDY_SESSION, ActivityType.FITNESS_LOG, ActivityType.STUDY_TOPIC, ActivityType.READING_SESSION, ActivityType.JOURNAL_ENTRY),
            d.items.map { it.type },
        )
        val a = d.activity
        assertEquals(1, a.tasksDone)
        assertEquals(2, a.tasksTotal)
        assertEquals(2, a.trackersDue)
        assertEquals(1, a.studySessions)
        assertEquals(1, a.studyTopics)
        assertEquals(45, a.studyMinutes)
        assertEquals(1, a.workouts)
        assertEquals(30, a.activeMinutes)
        assertEquals(20, a.pages)
        assertEquals(45, a.longestSession)
        assertTrue(a.journaled)
        assertEquals(Mood.GOOD, a.mood)
        assertEquals(3, a.level)
        val steps = d.items.first { it.type == ActivityType.FITNESS_LOG }
        assertEquals(6432.0, steps.value!!, 0.0)
        assertEquals("steps", steps.unit)
        assertEquals("Atomic Habits", d.items.first { it.type == ActivityType.READING_SESSION }.title)
    }

    @Test fun recordsWhoseSourceIsGoneStillCountAsPreviousActivity() {
        val h = build(
            trackers = emptyList(),
            entries = listOf(TrackerEntry(99, day, mapOf(1L to FieldValue(1, number = 3.0)), at)),
            sessions = listOf(ReadingSession(1, 404, day, pages = 12)),
        )
        val items = h.day(day)!!.items
        assertEquals(2, items.size)
        assertTrue(items.all { it.missingSource })
        assertTrue(h.day(day)!!.activity.isActive)
    }

    @Test fun emptyEntriesAndBlankJournalsAreNotActivity() {
        val h = build(
            entries = listOf(TrackerEntry(1, day, mapOf(11L to FieldValue(11, number = 0.0)), at)),
            journal = listOf(JournalEntry(1, day, text = "   ")),
        )
        val d = h.day(day)!!
        assertTrue(d.items.isEmpty())
        assertFalse(d.activity.isActive)
        assertNull(d.journal)
    }

    @Test fun trackersAreNotDueBeforeTheyExisted() {
        val late = study.copy(createdAt = day.atStartOfDay().toInstant(zone))
        val h = build(trackers = listOf(late))
        assertEquals(0, h.day(day.minusDays(1))!!.activity.trackersDue)
        assertEquals(1, h.day(day)!!.activity.trackersDue)
    }
}
