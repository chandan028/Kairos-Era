package com.kairosera.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class Priority { NONE, LOW, MEDIUM, HIGH }

/**
 * A task is a *definition*. A one-time task has exactly one occurrence on [date].
 * A recurring task (non-null [repeatRule]) has occurrences generated from [date] by the rule.
 * The per-date state (done / skipped) is stored separately as [OccurrenceState], so the rule
 * and the history never overwrite each other.
 */
data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val priority: Priority = Priority.NONE,
    val categoryId: Long? = null,
    val repeatRule: RepeatRule? = null,
    val reminders: List<Reminder> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val archivedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val isSample: Boolean = false,
) {
    val isAllDay: Boolean get() = startTime == null
    val isRecurring: Boolean get() = repeatRule != null
    val isActive: Boolean get() = archivedAt == null && deletedAt == null
}

data class Subtask(
    val id: Long = 0,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val colorArgb: Long,
    val icon: String,
    val position: Int = 0,
)

enum class OccurrenceStatus { DONE, SKIPPED }

/** Stored state of one occurrence of a task on one date. Absence means "open". */
data class OccurrenceState(
    val taskId: Long,
    val date: LocalDate,
    val status: OccurrenceStatus,
    val changedAt: Instant,
)

/** A task as it appears on a specific day. Computed, never stored. */
data class TaskOccurrence(
    val task: Task,
    val date: LocalDate,
    val state: OccurrenceState?,
) {
    val isDone: Boolean get() = state?.status == OccurrenceStatus.DONE
    val isSkipped: Boolean get() = state?.status == OccurrenceStatus.SKIPPED
    val completedAt: Instant? get() = state?.takeIf { it.status == OccurrenceStatus.DONE }?.changedAt
}
