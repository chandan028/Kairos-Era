package com.kairosera.feature.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.ui.components.UiState
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.tracker.FieldValue
import com.kairosera.domain.tracker.StudyProgress
import com.kairosera.domain.tracker.StudySummary
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.TrackerEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class TrackData(
    val today: LocalDate,
    val trackers: List<TrackerSummary>,
    /** Topic progress per study tracker. */
    val study: Map<Long, StudySummary> = emptyMap(),
    /** Today's study targets per tracker: done to total. */
    val targetsToday: Map<Long, Pair<Int, Int>> = emptyMap(),
    val currentBook: Book? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrackViewModel(private val c: AppContainer) : ViewModel() {
    private val retry = MutableStateFlow(0)

    val state: StateFlow<UiState<TrackData>> = retry.flatMapLatest {
        val today = LocalDate.now()
        combine(
            c.trackers.observeActive(),
            c.trackers.observeEntries(today.minusDays(TrackerSummaries.HISTORY_DAYS), today),
            c.study.observeAllTopics(),
            c.study.observeTargetsBetween(today, today),
            c.books.observeBooks(),
        ) { list, entries, topics, targets, books ->
            UiState.Ready(
                TrackData(
                    today = today,
                    trackers = list.map { TrackerSummaries.summarize(it, entries, today) },
                    study = topics.groupBy { it.trackerId }.mapValues { StudyProgress.summarize(it.value) },
                    targetsToday = targets.groupBy { it.trackerId }.mapValues { (_, t) -> t.count { it.done } to t.size },
                    currentBook = books.firstOrNull { it.status == BookStatus.READING },
                ),
            ) as UiState<TrackData>
        }.catch { emit(UiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun retry() { retry.value++ }

    /** One-tap completion for trackers whose main field is a checkbox. Other fields keep their values. */
    fun toggleMain(summary: TrackerSummary) {
        val field = summary.tracker.enabledFields.firstOrNull() ?: return
        if (field.type != MeasurementType.CHECKBOX) return
        val values = summary.todayEntry?.values.orEmpty().toMutableMap()
        val on = (values[field.id]?.number ?: 0.0) > 0.0
        values[field.id] = FieldValue(field.id, number = if (on) null else 1.0)
        viewModelScope.launch {
            runCatching { c.trackers.saveEntry(TrackerEntry(summary.tracker.id, LocalDate.now(), values, Instant.now(c.clock))) }
        }
    }

    fun move(id: Long, up: Boolean) {
        val list = (state.value as? UiState.Ready)?.data?.trackers?.map { it.tracker.id } ?: return
        val i = list.indexOf(id)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        val reordered = list.toMutableList().apply { add(j, removeAt(i)) }
        viewModelScope.launch { runCatching { c.trackers.reorder(reordered) } }
    }
}
