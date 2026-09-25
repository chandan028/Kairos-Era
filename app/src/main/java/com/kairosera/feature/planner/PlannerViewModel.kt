package com.kairosera.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.ui.components.UiState
import com.kairosera.domain.model.Category
import com.kairosera.domain.model.TaskOccurrence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class PlannerMode { DAY, WEEK, MONTH }

data class PlannerData(
    val selected: LocalDate,
    val mode: PlannerMode,
    val from: LocalDate,
    val to: LocalDate,
    val byDate: Map<LocalDate, List<TaskOccurrence>>,
    val categories: Map<Long, Category> = emptyMap(),
)

sealed interface PlannerEvent {
    data class Trashed(val taskId: Long) : PlannerEvent
    data object Failed : PlannerEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModel(private val c: AppContainer, initialDate: LocalDate?) : ViewModel() {
    private val selected = MutableStateFlow(initialDate ?: LocalDate.now())
    private val mode = MutableStateFlow(PlannerMode.DAY)
    private val retry = MutableStateFlow(0)
    private val _events = MutableSharedFlow<PlannerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlannerEvent> = _events

    val selectedDate: StateFlow<LocalDate> = selected.asStateFlow()

    val state: StateFlow<UiState<PlannerData>> = combine(selected, mode, retry) { d, m, _ -> d to m }
        .flatMapLatest { (date, m) ->
            val (from, to) = rangeFor(date, m)
            combine(c.observeDayPlan(from, to), c.tasks.observeCategories()) { plan, cats ->
                UiState.Ready(PlannerData(date, m, from, to, plan, cats.associateBy { it.id })) as UiState<PlannerData>
            }.catch { emit(UiState.Error()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun select(date: LocalDate) { selected.value = date }
    fun setMode(m: PlannerMode) { mode.value = m }
    fun retry() { retry.value++ }

    fun shift(steps: Long) {
        selected.value = when (mode.value) {
            PlannerMode.DAY, PlannerMode.WEEK -> selected.value.plusWeeks(steps)
            PlannerMode.MONTH -> selected.value.plusMonths(steps)
        }
    }

    fun setDone(o: TaskOccurrence, done: Boolean) = launchSafely { c.setOccurrenceDone(o.task.id, o.date, done) }

    fun trash(o: TaskOccurrence) = launchSafely {
        c.moveToTrash(o.task.id)
        _events.tryEmit(PlannerEvent.Trashed(o.task.id))
    }

    fun undoTrash(taskId: Long) = launchSafely { c.restoreTask(taskId) }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { _events.tryEmit(PlannerEvent.Failed) }
        }
    }

    companion object {
        fun rangeFor(date: LocalDate, mode: PlannerMode): Pair<LocalDate, LocalDate> = when (mode) {
            // Day view loads its whole week so the day strip can show how full each day is.
            PlannerMode.DAY, PlannerMode.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
            PlannerMode.MONTH -> YearMonth.from(date).let { it.atDay(1) to it.atEndOfMonth() }
        }
    }
}
