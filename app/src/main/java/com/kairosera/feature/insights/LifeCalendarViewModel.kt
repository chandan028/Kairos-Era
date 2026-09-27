package com.kairosera.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.ui.components.UiState
import com.kairosera.domain.activity.DayDetail
import com.kairosera.domain.activity.History
import com.kairosera.domain.stats.DayActivity
import com.kairosera.domain.stats.StatsCalculator
import com.kairosera.domain.stats.StatsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

data class CalendarData(val month: YearMonth, val today: LocalDate, val selected: LocalDate, val history: History, val summary: StatsSummary?) {
    val selectedDay: DayDetail? get() = history.day(selected)
}

data class YearData(val year: Year, val today: LocalDate, val days: Map<LocalDate, DayActivity>) {
    val activeDays: Int get() = days.values.count { it.isActive && !it.date.isAfter(today) }
}

/** The person's history a month (or a year) at a time, plus the day they tap on. Past, present and planned future alike. */
@OptIn(ExperimentalCoroutinesApi::class)
class LifeCalendarViewModel(private val c: AppContainer, initial: LocalDate?) : ViewModel() {
    enum class Mode { MONTH, YEAR }

    private val _month = MutableStateFlow(YearMonth.from(initial ?: LocalDate.now()))
    val month: StateFlow<YearMonth> = _month.asStateFlow()
    private val _selected = MutableStateFlow(initial ?: LocalDate.now())
    private val _mode = MutableStateFlow(Mode.MONTH)
    val mode: StateFlow<Mode> = _mode.asStateFlow()
    private val _year = MutableStateFlow(Year.from(initial ?: LocalDate.now()))
    val year: StateFlow<Year> = _year.asStateFlow()
    private val retry = MutableStateFlow(0)

    val state: StateFlow<UiState<CalendarData>> = combine(_month, todayFlow(), retry) { m, today, _ -> m to today }.flatMapLatest { (m, today) ->
        HistorySource.observe(c, m.atDay(1), m.atEndOfMonth())
            .combine(_selected) { h, sel ->
                val last = minOf(today, m.atEndOfMonth())
                val summary = if (m.atDay(1).isAfter(today)) null else StatsCalculator.summarize(h.days.map { it.activity }, m.atDay(1), last)
                UiState.Ready(CalendarData(m, today, sel, h, summary)) as UiState<CalendarData>
            }
            .flowOn(Dispatchers.Default)
            .catch { emit(UiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** Only queried while the year view is open: one year of day counts, no text. */
    val yearState: StateFlow<UiState<YearData>> = combine(_mode, _year, todayFlow(), retry) { mode, y, today, _ -> Triple(mode, y, today) }.flatMapLatest { (mode, y, today) ->
        if (mode != Mode.YEAR) return@flatMapLatest flowOf(UiState.Loading)
        HistorySource.observe(c, y.atDay(1), minOf(y.atMonth(12).atEndOfMonth(), today))
            .map { h -> UiState.Ready(YearData(y, today, h.days.associate { it.date to it.activity })) as UiState<YearData> }
            .flowOn(Dispatchers.Default)
            .catch { emit(UiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun select(date: LocalDate) {
        _selected.value = date
        if (YearMonth.from(date) != _month.value) _month.value = YearMonth.from(date)
    }

    fun shiftMonth(by: Long) {
        val m = _month.value.plusMonths(by)
        _month.value = m
        val today = LocalDate.now()
        _selected.value = if (YearMonth.from(today) == m) today else m.atDay(1)
    }

    fun shiftYear(by: Long) { _year.value = _year.value.plusYears(by) }

    fun showYear() {
        _year.value = Year.from(_month.value)
        _mode.value = Mode.YEAR
    }

    fun openMonth(m: YearMonth) {
        _mode.value = Mode.MONTH
        _month.value = m
        val today = LocalDate.now()
        _selected.value = if (YearMonth.from(today) == m) today else m.atDay(1)
    }

    fun showMonth() { _mode.value = Mode.MONTH }

    fun retry() { retry.value++ }
}

/** One full day, for the day detail timeline. */
@OptIn(ExperimentalCoroutinesApi::class)
class DayDetailViewModel(c: AppContainer, val date: LocalDate) : ViewModel() {
    private val retry = MutableStateFlow(0)

    val state: StateFlow<UiState<Pair<DayDetail, LocalDate>>> = combine(retry, todayFlow()) { _, today -> today }.flatMapLatest { today ->
        HistorySource.observe(c, date, date)
            .map { h -> UiState.Ready(h.days.single() to today) as UiState<Pair<DayDetail, LocalDate>> }
            .flowOn(Dispatchers.Default)
            .catch { emit(UiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun retry() { retry.value++ }
}
