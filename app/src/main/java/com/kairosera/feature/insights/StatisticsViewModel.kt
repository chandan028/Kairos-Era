package com.kairosera.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.ui.components.UiState
import com.kairosera.domain.activity.History
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.stats.DayActivity
import com.kairosera.domain.stats.Momentum
import com.kairosera.domain.stats.PersonalRecords
import com.kairosera.domain.stats.StatsCalculator
import com.kairosera.domain.stats.StatsRange
import com.kairosera.domain.stats.StatsSummary
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerScoring
import com.kairosera.domain.tracker.TrackerStatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** One life area and how much of what it asked for got done in the range. Only areas the person actually has. */
data class AreaStat(val kind: Kind, val fraction: Double, val count: Int, val tracker: Tracker? = null) {
    enum class Kind { TASKS, TRACKER, READING, JOURNAL }
}

data class StatsData(
    val range: StatsRange,
    val today: LocalDate,
    val summary: StatsSummary,
    /** The same range just before this one, for a gentle comparison. Null when the person had not started yet. */
    val previous: StatsSummary?,
    /** The range day by day, oldest first, for the activity map. */
    val days: List<DayActivity>,
    val areas: List<AreaStat>,
    val momentum: Momentum,
    val records: PersonalRecords,
) {
    /** Nothing planned, logged or written yet: show the invitation instead of a page of zeros. */
    val isEmpty: Boolean get() = !summary.hasActivity && summary.tasksPlanned == 0 && areas.isEmpty() && previous?.hasActivity != true
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(private val c: AppContainer) : ViewModel() {
    private val _range = MutableStateFlow(StatsRange.MONTH)
    val range: StateFlow<StatsRange> = _range.asStateFlow()
    private val retry = MutableStateFlow(0)

    val state: StateFlow<UiState<StatsData>> = combine(_range, todayFlow(), retry) { r, today, _ -> r to today }.flatMapLatest { (range, today) ->
        // Two ranges back (at least two weeks, for momentum), and nothing older.
        val from = today.minusDays(maxOf(range.days * 2L, 14L) - 1)
        HistorySource.observe(c, from, today)
            .map { history -> UiState.Ready(compute(range, today, history)) as UiState<StatsData> }
            .flowOn(Dispatchers.Default)
            .catch { emit(UiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun setRange(r: StatsRange) { _range.value = r }
    fun retry() { retry.value++ }

    private fun compute(range: StatsRange, today: LocalDate, history: History): StatsData {
        val all = history.days.map { it.activity }
        val started = HistorySource.startedOn(c, history)
        val start = today.minusDays(range.days - 1L)
        val prevStart = start.minusDays(range.days.toLong())
        val summary = StatsCalculator.summarize(all, start, today, started)
        val previous = StatsCalculator.summarize(all, prevStart, start.minusDays(1), started).takeIf { it.daysCounted > 0 }
        return StatsData(
            range = range,
            today = today,
            summary = summary,
            previous = previous,
            days = all.filter { !it.date.isBefore(start) },
            areas = areas(history, summary, today, maxOf(start, started), range),
            momentum = StatsCalculator.momentum(all, today, started),
            records = StatsCalculator.records(all, start, today),
        )
    }

    /** Areas come from the real trackers and records; nothing is shown for things the person never set up. */
    private fun areas(history: History, s: StatsSummary, today: LocalDate, start: LocalDate, range: StatsRange): List<AreaStat> {
        val zone = ZoneId.systemDefault()
        val trackerAreas = history.trackers.mapNotNull { t ->
            val mine = history.entries.filter { it.trackerId == t.id }
            val first = (listOf(t.createdAt.atZone(zone).toLocalDate()) + mine.map { it.date }).min()
            if (first.isAfter(today)) return@mapNotNull null
            val scores = mine.associate { it.date to TrackerScoring.score(t, it) }
            val stats = TrackerStatsCalculator.compute(
                frequency = t.frequency,
                startDate = maxOf(first, start),
                doneDates = scores.filterValues { it.done }.keys,
                activeDates = scores.filterValues { it.fraction > 0.0 }.keys,
                today = today,
                windowDays = range.days.toLong(),
            )
            AreaStat(AreaStat.Kind.TRACKER, stats.completionRate, stats.activeDays, t)
        }
        val days = s.daysCounted.coerceAtLeast(1)
        val reading = s.readingSessions > 0 || history.books.values.any { it.status == BookStatus.READING }
        return buildList {
            if (s.tasksPlanned > 0) add(AreaStat(AreaStat.Kind.TASKS, s.tasksDone.toDouble() / s.tasksPlanned, s.tasksDone))
            addAll(trackerAreas)
            if (reading) add(AreaStat(AreaStat.Kind.READING, s.readingDays.toDouble() / days, s.readingDays))
            if (s.reflections > 0) add(AreaStat(AreaStat.Kind.JOURNAL, s.reflections.toDouble() / days, s.reflections))
        }
    }
}
