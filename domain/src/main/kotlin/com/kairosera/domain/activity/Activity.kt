package com.kairosera.domain.activity

import com.kairosera.domain.journal.JournalEntry
import com.kairosera.domain.model.TaskOccurrence
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.ReadingSession
import com.kairosera.domain.stats.DayActivity
import com.kairosera.domain.tracker.DayScore
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.StudyTarget
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerEntry
import com.kairosera.domain.tracker.TrackerFrequency
import com.kairosera.domain.tracker.TrackerScoring
import com.kairosera.domain.tracker.TrackerStatsCalculator
import com.kairosera.domain.tracker.TrackerTemplate
import com.kairosera.domain.usecase.DaySummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The kinds of things a person can do in Kairos Era. One list feeds the calendar, the day timeline and statistics. */
enum class ActivityType { TASK_COMPLETED, STUDY_SESSION, STUDY_TOPIC, READING_SESSION, FITNESS_LOG, HABIT_COMPLETED, TRACKER_ENTRY, JOURNAL_ENTRY }

/**
 * One meaningful action on one day, derived from the stored record it points at ([sourceId]).
 * Nothing here is stored: the calendar and statistics are always computed from the real data,
 * so they can never drift from it. [title] is empty and [missingSource] is true when the tracker
 * or book behind an old record no longer exists; screens show it as "Previous activity".
 */
data class ActivityItem(
    val date: LocalDate,
    val type: ActivityType,
    val sourceId: Long,
    val title: String,
    val icon: String? = null,
    /** The headline number (steps, pages, minutes), with [unit] and the field [type][valueType] it came from. */
    val value: Double? = null,
    val unit: String = "",
    val valueType: MeasurementType? = null,
    val minutes: Int = 0,
    val completedAt: Instant? = null,
    val missingSource: Boolean = false,
)

/** A tracker's entry on one day with its score. */
data class LoggedTracker(val tracker: Tracker, val entry: TrackerEntry, val score: DayScore)

/** Everything recorded on one day: the plan, what was done, and the reflection. */
data class DayDetail(
    val date: LocalDate,
    val activity: DayActivity,
    val occurrences: List<TaskOccurrence>,
    val items: List<ActivityItem>,
    val logged: List<LoggedTracker>,
    val journal: JournalEntry?,
)

/** A window of history, day by day, plus the trackers and books its records refer to. */
data class History(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DayDetail>,
    val trackers: List<Tracker>,
    val entries: List<TrackerEntry>,
    val books: Map<Long, Book>,
) {
    private val byDate = days.associateBy { it.date }
    fun day(date: LocalDate): DayDetail? = byDate[date]
}

object ActivityHistory {
    private val STUDY = setOf(TrackerTemplate.STUDY, TrackerTemplate.LEARNING)
    private val FITNESS = setOf(TrackerTemplate.FITNESS, TrackerTemplate.HEALTH)
    private val HEADLINE = setOf(MeasurementType.NUMBER, MeasurementType.DECIMAL, MeasurementType.DURATION, MeasurementType.PAGES, MeasurementType.PERCENTAGE, MeasurementType.RATING)

    /**
     * Builds [from]..[to] from the records of that window only. [trackers] should include every
     * tracker the entries may refer to; an entry whose tracker is gone still counts as activity.
     * Only per-day trackers (daily or chosen weekdays) join a single day's plan: a "3 times a week"
     * goal is judged by its week, not by each day.
     */
    fun build(
        from: LocalDate,
        to: LocalDate,
        plan: Map<LocalDate, List<TaskOccurrence>>,
        trackers: List<Tracker>,
        entries: List<TrackerEntry>,
        targets: List<StudyTarget>,
        sessions: List<ReadingSession>,
        books: List<Book>,
        journal: List<JournalEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): History {
        val byId = trackers.associateBy { it.id }
        val bookById = books.associateBy { it.id }
        val entriesByDate = entries.groupBy { it.date }
        val targetsByDate = targets.groupBy { it.date }
        val sessionsByDate = sessions.groupBy { it.date }
        val journalByDate = journal.filter { it.deletedAt == null }.groupBy { it.date }.mapValues { (_, l) -> l.maxBy { it.updatedAt } }
        val firstLogged = entries.groupBy { it.trackerId }.mapValues { (_, l) -> l.minOf { it.date } }
        // A tracker is due from the day it was created, or its first entry if that is earlier.
        val starts = trackers.associate { t -> t.id to listOfNotNull(t.createdAt.atZone(zone).toLocalDate(), firstLogged[t.id]).min() }
        val perDay = trackers.filter { it.isActive && (it.frequency is TrackerFrequency.Daily || it.frequency is TrackerFrequency.SelectedDays) }

        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { date ->
            val occ = plan[date].orEmpty()
            val progress = DaySummary.progress(occ)
            val logged = mutableListOf<LoggedTracker>()
            val items = mutableListOf<ActivityItem>()

            occ.filter { it.isDone }.forEach { o ->
                items += ActivityItem(date, ActivityType.TASK_COMPLETED, o.task.id, o.task.title, completedAt = o.completedAt)
            }
            entriesByDate[date].orEmpty().forEach { e ->
                val t = byId[e.trackerId]
                if (t == null) {
                    if (e.values.values.any { v -> (v.number ?: 0.0) != 0.0 || !v.text.isNullOrBlank() || v.checked.isNotEmpty() }) {
                        items += ActivityItem(date, ActivityType.TRACKER_ENTRY, e.trackerId, "", completedAt = e.updatedAt, missingSource = true)
                    }
                    return@forEach
                }
                val score = TrackerScoring.score(t, e)
                val wrote = e.values.values.any { !it.text.isNullOrBlank() }
                if (score.fraction <= 0.0 && !wrote) return@forEach
                logged += LoggedTracker(t, e, score)
                val fields = t.enabledFields
                val minutes = fields.filter { it.type == MeasurementType.DURATION }.sumOf { e.values[it.id]?.number ?: 0.0 }.toInt()
                val headline = fields.firstOrNull { it.type in HEADLINE && (e.values[it.id]?.number ?: 0.0) > 0.0 }
                val type = when {
                    t.template in STUDY -> ActivityType.STUDY_SESSION
                    t.template in FITNESS -> ActivityType.FITNESS_LOG
                    t.template == TrackerTemplate.HABIT && score.done -> ActivityType.HABIT_COMPLETED
                    else -> ActivityType.TRACKER_ENTRY
                }
                items += ActivityItem(
                    date, type, t.id, t.name, icon = t.icon,
                    value = headline?.let { e.values[it.id]?.number }, unit = headline?.unit.orEmpty(), valueType = headline?.type,
                    minutes = minutes, completedAt = e.updatedAt,
                )
            }
            targetsByDate[date].orEmpty().filter { it.done }.forEach { st ->
                items += ActivityItem(date, ActivityType.STUDY_TOPIC, st.id, st.title, missingSource = st.trackerId !in byId)
            }
            sessionsByDate[date].orEmpty().forEach { s ->
                val book = bookById[s.bookId]
                items += ActivityItem(
                    date, ActivityType.READING_SESSION, s.bookId, book?.title.orEmpty(),
                    value = s.pages.toDouble(), valueType = MeasurementType.PAGES, minutes = s.minutes,
                    completedAt = s.createdAt.takeIf { it != Instant.EPOCH }, missingSource = book == null,
                )
            }
            val entry = journalByDate[date]
            val journaled = entry != null && !entry.isBlank
            if (journaled) items += ActivityItem(date, ActivityType.JOURNAL_ENTRY, entry!!.id, "", completedAt = entry.updatedAt)

            val scores = logged.associate { it.tracker.id to it.score }
            val due = perDay.filter { t -> !date.isBefore(starts[t.id] ?: date) && TrackerStatsCalculator.isDue(t.frequency, date) }
            val study = items.filter { it.type == ActivityType.STUDY_SESSION }
            val fitness = items.filter { it.type == ActivityType.FITNESS_LOG }
            val reading = items.filter { it.type == ActivityType.READING_SESSION }
            DayDetail(
                date = date,
                activity = DayActivity(
                    date = date,
                    tasksDone = progress.done,
                    tasksTotal = progress.total,
                    trackersDue = due.size,
                    trackerProgress = due.sumOf { scores[it.id]?.fraction ?: 0.0 },
                    trackersLogged = items.count { it.type in TRACKER_TYPES },
                    studySessions = study.size,
                    studyTopics = items.count { it.type == ActivityType.STUDY_TOPIC },
                    studyMinutes = study.sumOf { it.minutes },
                    readingSessions = reading.size,
                    pages = reading.sumOf { it.value?.toInt() ?: 0 },
                    readingMinutes = reading.sumOf { it.minutes },
                    workouts = fitness.size,
                    activeMinutes = fitness.sumOf { it.minutes },
                    habitsDone = items.count { it.type == ActivityType.HABIT_COMPLETED },
                    longestSession = (study + reading).maxOfOrNull { it.minutes } ?: 0,
                    journaled = journaled,
                    mood = entry?.mood,
                ),
                occurrences = occ,
                items = items,
                logged = logged,
                journal = entry?.takeIf { !it.isBlank },
            )
        }.toList()
        return History(from, to, days, trackers, entries, bookById)
    }

    private val TRACKER_TYPES = setOf(ActivityType.STUDY_SESSION, ActivityType.FITNESS_LOG, ActivityType.HABIT_COMPLETED, ActivityType.TRACKER_ENTRY)
}
