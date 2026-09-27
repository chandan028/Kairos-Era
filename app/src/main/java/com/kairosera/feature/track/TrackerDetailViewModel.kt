package com.kairosera.feature.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.domain.tracker.FieldValue
import com.kairosera.domain.tracker.StudyTarget
import com.kairosera.domain.tracker.StudyTopic
import com.kairosera.domain.tracker.TrackerEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data object Error : DetailState
    data class Ready(val summary: TrackerSummary, val date: LocalDate, val today: LocalDate) : DetailState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerDetailViewModel(private val c: AppContainer, val trackerId: Long) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    private val _date = MutableStateFlow(today)
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /** Values being edited for [date]. Edits save after a short pause so typing never fights the database. */
    private val _draft = MutableStateFlow<Map<Long, FieldValue>>(emptyMap())
    val draft: StateFlow<Map<Long, FieldValue>> = _draft.asStateFlow()
    private var saveJob: Job? = null
    private var draftDate: LocalDate = today

    val state: StateFlow<DetailState> = combine(
        c.trackers.observe(trackerId),
        c.trackers.observeEntriesFor(trackerId, today.minusDays(TrackerSummaries.HISTORY_DAYS), today),
        _date,
    ) { tracker, entries, date ->
        if (tracker == null || tracker.deletedAt != null) DetailState.Missing
        else DetailState.Ready(TrackerSummaries.summarize(tracker, entries, today), date, today)
    }.catch { emit(DetailState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    val topics: StateFlow<List<StudyTopic>> = c.study.observeTopics(trackerId).catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val targets: StateFlow<List<StudyTarget>> = _date.flatMapLatest { c.study.observeTargets(trackerId, it) }.catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { loadDraft(today) }

    private fun loadDraft(date: LocalDate) {
        viewModelScope.launch {
            val entry = runCatching { c.trackers.observeEntriesFor(trackerId, date, date).first().firstOrNull() }.getOrNull()
            if (_date.value == date) {
                draftDate = date
                _draft.value = entry?.values.orEmpty()
            }
        }
    }

    fun setDate(date: LocalDate) {
        val d = date.coerceIn(today.minusDays(TrackerSummaries.HISTORY_DAYS - 1), today)
        if (d == _date.value) return
        flush()
        _date.value = d
        _draft.value = emptyMap()
        loadDraft(d)
    }

    fun update(value: FieldValue) {
        _draft.value = _draft.value + (value.fieldId to value)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            persist(draftDate, _draft.value)
        }
    }

    /** Saves any pending edit right away (date change, leaving the screen). */
    fun flush() {
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            val date = draftDate
            val values = _draft.value
            c.appScope.launch { persist(date, values) }
        }
    }

    private suspend fun persist(date: LocalDate, values: Map<Long, FieldValue>) {
        runCatching { c.trackers.saveEntry(TrackerEntry(trackerId, date, values, Instant.now(c.clock))) }
            .onFailure { SafeLog.error("tracker_entry_save_failed", it) }
    }

    fun moveToTrash(onDone: () -> Unit) {
        flush()
        viewModelScope.launch { runCatching { c.trackers.moveToTrash(trackerId, Instant.now(c.clock)) }.onSuccess { onDone() } }
    }

    fun saveTopic(topic: StudyTopic) {
        viewModelScope.launch { runCatching { c.study.saveTopic(topic.copy(trackerId = trackerId, updatedAt = Instant.now(c.clock))) } }
    }

    fun deleteTopic(id: Long) { viewModelScope.launch { runCatching { c.study.deleteTopic(id) } } }

    fun addTarget(title: String, topicId: Long?) {
        val clean = title.trim().take(200)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                c.study.saveTarget(StudyTarget(trackerId = trackerId, date = _date.value, topicId = topicId, title = clean, position = targets.value.size))
            }
        }
    }

    fun setTargetDone(id: Long, done: Boolean) {
        viewModelScope.launch { runCatching { c.study.setTargetDone(id, done, Instant.now(c.clock)) } }
    }

    fun deleteTarget(id: Long) { viewModelScope.launch { runCatching { c.study.deleteTarget(id) } } }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    private companion object { const val SAVE_DELAY_MS = 500L }
}
