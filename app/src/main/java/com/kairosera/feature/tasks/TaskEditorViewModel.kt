package com.kairosera.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.domain.model.Category
import com.kairosera.domain.model.Frequency
import com.kairosera.domain.model.Priority
import com.kairosera.domain.model.Reminder
import com.kairosera.domain.model.ReminderTiming
import com.kairosera.domain.model.RepeatRule
import com.kairosera.domain.model.Subtask
import com.kairosera.domain.model.Task
import com.kairosera.domain.usecase.SaveTask
import com.kairosera.domain.usecase.TaskValidationError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class RepeatChoice { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY }

data class TaskForm(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val date: LocalDate = LocalDate.now(),
    val allDay: Boolean = false,
    val start: LocalTime = LocalTime.of(9, 0),
    val end: LocalTime? = null,
    val priority: Priority = Priority.NONE,
    val categoryId: Long? = null,
    val tagsText: String = "",
    val repeat: RepeatChoice = RepeatChoice.NONE,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val repeatEnd: LocalDate? = null,
    val reminders: List<Reminder> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val isSample: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val archivedAt: Instant? = null,
)

sealed interface EditorStatus {
    data object Loading : EditorStatus
    data object Editing : EditorStatus
    data object Saving : EditorStatus
    data object Saved : EditorStatus
    data object Missing : EditorStatus
    data object Failed : EditorStatus
}

class TaskEditorViewModel(private val c: AppContainer, private val taskId: Long?, occurrenceDate: LocalDate?) : ViewModel() {
    private val _form = MutableStateFlow(TaskForm(date = occurrenceDate ?: LocalDate.now(), start = nextHalfHour()))
    val form: StateFlow<TaskForm> = _form.asStateFlow()
    private val _status = MutableStateFlow<EditorStatus>(if (taskId == null) EditorStatus.Editing else EditorStatus.Loading)
    val status: StateFlow<EditorStatus> = _status.asStateFlow()
    private val _errors = MutableStateFlow<List<TaskValidationError>>(emptyList())
    val errors: StateFlow<List<TaskValidationError>> = _errors.asStateFlow()

    val categories: StateFlow<List<Category>> = c.tasks.observeCategories()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isNew get() = taskId == null

    init {
        if (taskId != null) load(taskId)
    }

    fun load(id: Long) {
        _status.value = EditorStatus.Loading
        viewModelScope.launch {
            val task = runCatching { c.tasks.getTask(id) }.getOrElse { _status.value = EditorStatus.Failed; return@launch }
            if (task == null || task.deletedAt != null) {
                _status.value = EditorStatus.Missing
                return@launch
            }
            _form.value = task.toForm()
            _status.value = EditorStatus.Editing
        }
    }

    fun edit(transform: (TaskForm) -> TaskForm) {
        _form.update(transform)
        if (_errors.value.isNotEmpty()) _errors.value = emptyList()
    }

    suspend fun newReminder(timing: ReminderTiming): Reminder {
        val s = c.settings.settings.first()
        return Reminder(timing = timing, persistent = s.defaultPersistent, sound = s.defaultSound)
    }

    fun save() {
        if (_status.value == EditorStatus.Saving) return
        _status.value = EditorStatus.Saving
        viewModelScope.launch {
            val result = runCatching { c.saveTask(_form.value.toTask()) }.getOrNull()
            when (result) {
                is SaveTask.Result.Saved -> _status.value = EditorStatus.Saved
                is SaveTask.Result.Invalid -> { _errors.value = result.errors; _status.value = EditorStatus.Editing }
                null -> _status.value = EditorStatus.Failed
            }
        }
    }

    fun moveToTrash(onDone: () -> Unit) {
        val id = _form.value.id.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            runCatching { c.moveToTrash(id) }
            onDone()
        }
    }

    fun dismissFailure() { _status.value = EditorStatus.Editing }

    private fun TaskForm.toTask(): Task {
        val rule = when (repeat) {
            RepeatChoice.NONE -> null
            RepeatChoice.DAILY -> RepeatRule(Frequency.DAILY, interval, endDate = repeatEnd)
            RepeatChoice.WEEKDAYS -> RepeatRule(Frequency.WEEKLY, 1, WORKWEEK, endDate = repeatEnd)
            RepeatChoice.WEEKLY -> RepeatRule(Frequency.WEEKLY, interval, weekdays.ifEmpty { setOf(date.dayOfWeek) }, endDate = repeatEnd)
            RepeatChoice.MONTHLY -> RepeatRule(Frequency.MONTHLY, interval, endDate = repeatEnd)
            RepeatChoice.YEARLY -> RepeatRule(Frequency.YEARLY, interval, endDate = repeatEnd)
        }
        return Task(
            id = id,
            title = title,
            description = description.trim(),
            notes = notes.trim(),
            date = date,
            startTime = if (allDay) null else start,
            endTime = if (allDay) null else end,
            priority = priority,
            categoryId = categoryId,
            repeatRule = rule,
            reminders = if (allDay) reminders.filter { it.timing is ReminderTiming.AtTime } else reminders,
            subtasks = subtasks,
            tags = tagsText.split(',', '#').map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_TAGS),
            createdAt = createdAt,
            archivedAt = archivedAt,
            isSample = isSample,
        )
    }

    private fun Task.toForm(): TaskForm {
        val rule = repeatRule
        val choice = when {
            rule == null -> RepeatChoice.NONE
            rule.frequency == Frequency.DAILY -> RepeatChoice.DAILY
            rule.frequency == Frequency.WEEKLY && rule.interval == 1 && rule.weekdays == WORKWEEK -> RepeatChoice.WEEKDAYS
            rule.frequency == Frequency.WEEKLY -> RepeatChoice.WEEKLY
            rule.frequency == Frequency.MONTHLY -> RepeatChoice.MONTHLY
            else -> RepeatChoice.YEARLY
        }
        return TaskForm(
            id = id, title = title, description = description, notes = notes, date = date,
            allDay = startTime == null, start = startTime ?: nextHalfHour(), end = endTime,
            priority = priority, categoryId = categoryId, tagsText = tags.joinToString(", "),
            repeat = choice, interval = rule?.interval ?: 1, weekdays = rule?.weekdays.orEmpty(), repeatEnd = rule?.endDate,
            reminders = reminders, subtasks = subtasks, isSample = isSample, createdAt = createdAt, archivedAt = archivedAt,
        )
    }

    companion object {
        private const val MAX_TAGS = 10
        val WORKWEEK = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        fun nextHalfHour(): LocalTime {
            val now = LocalTime.now()
            val minutes = ((now.hour * 60 + now.minute) / 30 + 1) * 30
            return if (minutes >= 24 * 60) LocalTime.of(23, 30) else LocalTime.of(minutes / 60, minutes % 60)
        }
    }
}
