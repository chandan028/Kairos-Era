package com.kairosera.feature.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.data.tracker.TrackerTemplates
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerField
import com.kairosera.domain.tracker.TrackerFrequency
import com.kairosera.domain.tracker.TrackerTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

enum class TrackerEditorError { NAME_EMPTY, NO_FIELDS, FIELD_LABEL_EMPTY, CHECKLIST_EMPTY }

sealed interface TrackerEditorStatus {
    data object Loading : TrackerEditorStatus
    data object Editing : TrackerEditorStatus
    data object Saving : TrackerEditorStatus
    data class Saved(val id: Long) : TrackerEditorStatus
    data object Missing : TrackerEditorStatus
    data object Failed : TrackerEditorStatus
}

class TrackerEditorViewModel(
    private val c: AppContainer,
    private val trackerId: Long?,
    template: TrackerTemplate,
    context: android.content.Context,
) : ViewModel() {
    val isNew = trackerId == null
    private val _form = MutableStateFlow(if (trackerId == null) TrackerTemplates.create(context, template) else Tracker(name = ""))
    val form: StateFlow<Tracker> = _form.asStateFlow()
    private val _status = MutableStateFlow<TrackerEditorStatus>(if (trackerId == null) TrackerEditorStatus.Editing else TrackerEditorStatus.Loading)
    val status: StateFlow<TrackerEditorStatus> = _status.asStateFlow()
    private val _errors = MutableStateFlow<Set<TrackerEditorError>>(emptySet())
    val errors: StateFlow<Set<TrackerEditorError>> = _errors.asStateFlow()

    init {
        if (trackerId != null) viewModelScope.launch {
            val t = runCatching { c.trackers.get(trackerId) }.getOrNull()
            if (t == null || t.deletedAt != null) _status.value = TrackerEditorStatus.Missing
            else { _form.value = t; _status.value = TrackerEditorStatus.Editing }
        }
    }

    fun edit(change: (Tracker) -> Tracker) {
        _form.update(change)
        if (_errors.value.isNotEmpty()) _errors.value = validate(_form.value)
    }

    fun upsertField(index: Int?, field: TrackerField) = edit { t ->
        val list = t.fields.toMutableList()
        if (index == null || index !in list.indices) list.add(field) else list[index] = field
        t.copy(fields = list)
    }

    /** Removes a field from the form. Saved history for it is kept (it's switched off in storage). */
    fun removeField(index: Int) = edit { t -> t.copy(fields = t.fields.filterIndexed { i, _ -> i != index }) }

    fun moveField(index: Int, up: Boolean) = edit { t ->
        val j = if (up) index - 1 else index + 1
        if (index !in t.fields.indices || j !in t.fields.indices) t
        else t.copy(fields = t.fields.toMutableList().apply { add(j, removeAt(index)) })
    }

    fun setFrequency(f: TrackerFrequency) = edit { it.copy(frequency = f) }

    fun save() {
        if (_status.value != TrackerEditorStatus.Editing) return
        val t = _form.value.let { it.copy(name = it.name.trim(), fields = it.fields.map { f -> f.copy(label = f.label.trim()) }) }
        val problems = validate(t)
        _errors.value = problems
        if (problems.isNotEmpty()) return
        _status.value = TrackerEditorStatus.Saving
        viewModelScope.launch {
            val now = Instant.now(c.clock)
            runCatching { c.trackers.save(t.copy(createdAt = if (isNew) now else t.createdAt, updatedAt = now)) }
                .onSuccess { _status.value = TrackerEditorStatus.Saved(it) }
                .onFailure { SafeLog.error("tracker_save_failed", it); _status.value = TrackerEditorStatus.Failed }
        }
    }

    fun dismissFailure() { _status.value = TrackerEditorStatus.Editing }

    companion object {
        const val MAX_NAME = 60
        const val MAX_FIELDS = 20

        fun validate(t: Tracker): Set<TrackerEditorError> = buildSet {
            if (t.name.isBlank()) add(TrackerEditorError.NAME_EMPTY)
            if (t.fields.none { it.enabled }) add(TrackerEditorError.NO_FIELDS)
            if (t.fields.any { it.label.isBlank() }) add(TrackerEditorError.FIELD_LABEL_EMPTY)
            if (t.fields.any { it.type == MeasurementType.CHECKLIST && it.options.isEmpty() }) add(TrackerEditorError.CHECKLIST_EMPTY)
        }
    }
}
