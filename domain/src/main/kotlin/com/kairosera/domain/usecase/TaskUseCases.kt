package com.kairosera.domain.usecase

import com.kairosera.domain.model.OccurrenceState
import com.kairosera.domain.model.OccurrenceStatus
import com.kairosera.domain.model.ReminderTiming
import com.kairosera.domain.model.Task
import com.kairosera.domain.model.TaskOccurrence
import com.kairosera.domain.repository.TaskRepository
import com.kairosera.domain.time.RecurrenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Expands task definitions into the occurrences visible on each date of a range. */
object OccurrenceExpander {
    fun expand(tasks: List<Task>, states: List<OccurrenceState>, from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskOccurrence>> {
        val stateIndex = states.associateBy { it.taskId to it.date }
        val result = sortedMapOf<LocalDate, MutableList<TaskOccurrence>>()
        for (task in tasks) {
            if (!task.isActive) continue
            for (date in RecurrenceEngine.occurrencesBetween(task.date, task.repeatRule, from, to)) {
                result.getOrPut(date) { mutableListOf() } += TaskOccurrence(task, date, stateIndex[task.id to date])
            }
        }
        return result.mapValues { (_, list) -> list.sortedWith(occurrenceOrder) }
    }

    /** Timed tasks by start time first, then all-day tasks by priority; done items sink within each group. */
    val occurrenceOrder: Comparator<TaskOccurrence> = compareBy<TaskOccurrence> { it.isDone || it.isSkipped }
        .thenBy { it.task.isAllDay }
        .thenBy { it.task.startTime }
        .thenByDescending { it.task.priority.ordinal }
        .thenBy { it.task.title.lowercase() }
}

class ObserveDayPlan(private val repository: TaskRepository) {
    operator fun invoke(from: LocalDate, to: LocalDate = from): Flow<Map<LocalDate, List<TaskOccurrence>>> =
        combine(repository.observeTasksBetween(from, to), repository.observeOccurrenceStates(from, to)) { tasks, states ->
            OccurrenceExpander.expand(tasks, states, from, to)
        }
}

sealed interface TaskValidationError {
    data object EmptyTitle : TaskValidationError
    data object TitleTooLong : TaskValidationError
    data object EndBeforeStart : TaskValidationError
    data object ReminderNeedsStartTime : TaskValidationError
    data object TooManyReminders : TaskValidationError
}

object TaskValidator {
    const val MAX_TITLE = 200
    const val MAX_REMINDERS = 5

    fun validate(task: Task): List<TaskValidationError> = buildList {
        if (task.title.isBlank()) add(TaskValidationError.EmptyTitle)
        if (task.title.length > MAX_TITLE) add(TaskValidationError.TitleTooLong)
        if (task.startTime != null && task.endTime != null && task.endTime < task.startTime) add(TaskValidationError.EndBeforeStart)
        if (task.startTime == null && task.reminders.any { it.timing is ReminderTiming.BeforeStart }) {
            add(TaskValidationError.ReminderNeedsStartTime)
        }
        if (task.reminders.size > MAX_REMINDERS) add(TaskValidationError.TooManyReminders)
    }
}

class SaveTask(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val onRemindersChanged: suspend () -> Unit,
) {
    sealed interface Result {
        data class Saved(val id: Long) : Result
        data class Invalid(val errors: List<TaskValidationError>) : Result
    }

    suspend operator fun invoke(task: Task): Result {
        val trimmed = task.copy(
            title = task.title.trim(),
            subtasks = task.subtasks.filter { it.title.isNotBlank() }.mapIndexed { i, s -> s.copy(title = s.title.trim(), position = i) },
            tags = task.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        )
        val errors = TaskValidator.validate(trimmed)
        if (errors.isNotEmpty()) return Result.Invalid(errors)
        val now = Instant.now(clock)
        val id = repository.saveTask(
            trimmed.copy(createdAt = if (trimmed.id == 0L) now else trimmed.createdAt, updatedAt = now),
        )
        onRemindersChanged()
        return Result.Saved(id)
    }
}

class SetOccurrenceDone(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val onRemindersChanged: suspend () -> Unit,
) {
    suspend operator fun invoke(taskId: Long, date: LocalDate, done: Boolean) {
        repository.setOccurrenceState(taskId, date, if (done) OccurrenceStatus.DONE else null, Instant.now(clock))
        onRemindersChanged()
    }
}

class MoveTaskToTrash(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val onRemindersChanged: suspend () -> Unit,
) {
    suspend operator fun invoke(taskId: Long) {
        repository.moveToTrash(taskId, Instant.now(clock))
        onRemindersChanged()
    }
}

class RestoreTask(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val onRemindersChanged: suspend () -> Unit,
) {
    suspend operator fun invoke(taskId: Long) {
        repository.restoreFromTrash(taskId, Instant.now(clock))
        onRemindersChanged()
    }
}

/**
 * Moves one occurrence to another date/time. A one-time task is simply edited.
 * For a recurring task the series is left untouched: this occurrence is marked SKIPPED
 * and a one-time copy is created on the new date, so the user's plan is never rewritten.
 */
class RescheduleOccurrence(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val onRemindersChanged: suspend () -> Unit,
) {
    suspend operator fun invoke(taskId: Long, date: LocalDate, newDate: LocalDate, newStart: java.time.LocalTime?): Long? {
        val task = repository.getTask(taskId) ?: return null
        val now = Instant.now(clock)
        val shift = if (task.startTime != null && newStart != null) java.time.Duration.between(task.startTime, newStart) else null
        val newEnd = if (shift != null) task.endTime?.plus(shift) else task.endTime
        val id = if (task.repeatRule == null) {
            repository.saveTask(task.copy(date = newDate, startTime = newStart ?: task.startTime, endTime = newEnd, updatedAt = now))
        } else {
            repository.setOccurrenceState(taskId, date, OccurrenceStatus.SKIPPED, now)
            repository.saveTask(
                task.copy(
                    id = 0,
                    date = newDate,
                    startTime = newStart ?: task.startTime,
                    endTime = newEnd,
                    repeatRule = null,
                    reminders = task.reminders.map { it.copy(id = 0, taskId = 0) },
                    subtasks = task.subtasks.map { it.copy(id = 0, done = false) },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        onRemindersChanged()
        return id
    }
}
