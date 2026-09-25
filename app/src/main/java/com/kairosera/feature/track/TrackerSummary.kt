package com.kairosera.feature.track

import com.kairosera.domain.tracker.DayScore
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerEntry
import com.kairosera.domain.tracker.TrackerScoring
import com.kairosera.domain.tracker.TrackerStats
import com.kairosera.domain.tracker.TrackerStatsCalculator
import java.time.LocalDate
import java.time.ZoneId

/** Everything a tracker card or detail needs about one day plus its history. */
data class TrackerSummary(
    val tracker: Tracker,
    val today: DayScore,
    val todayEntry: TrackerEntry?,
    val stats: TrackerStats,
    val dueToday: Boolean,
    /** Score by date for the history window (only dates with entries). */
    val scores: Map<LocalDate, DayScore>,
)

object TrackerSummaries {
    /** Days of history loaded for streaks. A year is plenty and keeps queries small. */
    const val HISTORY_DAYS = 400L

    fun summarize(tracker: Tracker, entries: List<TrackerEntry>, today: LocalDate): TrackerSummary {
        val mine = entries.filter { it.trackerId == tracker.id }
        val scores = mine.associate { it.date to TrackerScoring.score(tracker, it) }
        val created = tracker.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
        val start = (listOf(created) + mine.map { it.date }).min()
        val todayEntry = mine.firstOrNull { it.date == today }
        return TrackerSummary(
            tracker = tracker,
            today = scores[today] ?: TrackerScoring.score(tracker, null),
            todayEntry = todayEntry,
            stats = TrackerStatsCalculator.compute(
                frequency = tracker.frequency,
                startDate = start,
                doneDates = scores.filterValues { it.done }.keys,
                activeDates = scores.filterValues { it.fraction > 0.0 }.keys,
                today = today,
            ),
            dueToday = TrackerStatsCalculator.isDue(tracker.frequency, today),
            scores = scores,
        )
    }
}
