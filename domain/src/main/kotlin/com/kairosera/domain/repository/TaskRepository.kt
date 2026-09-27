package com.kairosera.domain.repository

import com.kairosera.domain.model.Category
import com.kairosera.domain.model.OccurrenceState
import com.kairosera.domain.model.OccurrenceStatus
import com.kairosera.domain.model.Task
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface TaskRepository {
    /** Active tasks that can have an occurrence in [from]..[to]: one-time tasks dated inside it, and series overlapping it. */
    fun observeTasksBetween(from: LocalDate, to: LocalDate): Flow<List<Task>>

    fun observeOccurrenceStates(from: LocalDate, to: LocalDate): Flow<List<OccurrenceState>>

    fun observeTask(id: Long): Flow<Task?>

    fun observeCategories(): Flow<List<Category>>

    fun observeTrash(): Flow<List<Task>>

    suspend fun getTask(id: Long): Task?

    /** All active tasks that have at least one reminder. Used to rebuild the notification schedule. */
    suspend fun getTasksWithReminders(): List<Task>

    suspend fun getOccurrenceStates(from: LocalDate, to: LocalDate): List<OccurrenceState>

    /** Inserts or updates a task with its rule, reminders, subtasks and tags in one transaction. Returns the id. */
    suspend fun saveTask(task: Task): Long

    /** Sets (or clears, when [status] is null) the state of one occurrence, atomically with the task's own completion fields. */
    suspend fun setOccurrenceState(taskId: Long, date: LocalDate, status: OccurrenceStatus?, at: Instant)

    suspend fun setSubtaskDone(subtaskId: Long, done: Boolean, at: Instant)

    /** Soft delete: the task goes to Trash and can be restored. */
    suspend fun moveToTrash(taskId: Long, at: Instant)

    suspend fun restoreFromTrash(taskId: Long, at: Instant)

    /** Permanent deletion. Callers must have explicit user confirmation. */
    suspend fun deletePermanently(taskId: Long)

    suspend fun saveCategory(category: Category): Long

    suspend fun hasSampleData(): Boolean

    /** Moves every sample task to Trash (recoverable). */
    suspend fun trashSampleData(at: Instant)
}
