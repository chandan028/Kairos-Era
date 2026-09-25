package com.kairosera.data.repository

import androidx.room.withTransaction
import com.kairosera.core.database.KairosDatabase
import com.kairosera.core.database.OccurrenceStateEntity
import com.kairosera.core.database.TaskTagEntity
import com.kairosera.domain.model.Category
import com.kairosera.domain.model.OccurrenceState
import com.kairosera.domain.model.OccurrenceStatus
import com.kairosera.domain.model.Task
import com.kairosera.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class RoomTaskRepository(private val db: KairosDatabase) : TaskRepository {
    private val dao = db.taskDao()

    override fun observeTasksBetween(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        dao.observeBetween(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { it.toDomain() } }

    override fun observeOccurrenceStates(from: LocalDate, to: LocalDate): Flow<List<OccurrenceState>> =
        dao.observeStates(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { it.toDomain() } }

    override fun observeTask(id: Long): Flow<Task?> = dao.observe(id).map { it?.toDomain() }

    override fun observeCategories(): Flow<List<Category>> = dao.observeCategories().map { l -> l.map { it.toDomain() } }

    override fun observeTrash(): Flow<List<Task>> = dao.observeTrash().map { l -> l.map { it.toDomain() } }

    override suspend fun getTask(id: Long): Task? = dao.get(id)?.toDomain()

    override suspend fun getTasksWithReminders(): List<Task> = dao.getWithReminders().map { it.toDomain() }

    override suspend fun getOccurrenceStates(from: LocalDate, to: LocalDate): List<OccurrenceState> =
        dao.getStates(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    override suspend fun saveTask(task: Task): Long = db.withTransaction {
        val existing = if (task.id != 0L) dao.get(task.id) else null
        val completedAt = existing?.task?.completedAt?.takeIf { task.repeatRule == null }
        val id = if (existing == null) {
            dao.insert(task.copy(id = 0).toEntity(completedAt = null))
        } else {
            dao.update(task.toEntity(completedAt))
            task.id
        }
        // Reminders and subtasks keep their ids across edits, so scheduled notifications stay mapped.
        val keepExistingIds = existing != null
        val reminderIds = task.reminders.map { r ->
            val entity = r.toEntity(id).let { e -> if (keepExistingIds) e else e.copy(id = 0) }
            val newId = dao.upsertReminder(entity)
            if (entity.id != 0L) entity.id else newId
        }
        dao.deleteRemindersExcept(id, reminderIds)
        val subtaskIds = task.subtasks.mapIndexed { i, s ->
            val entity = s.copy(position = i).toEntity(id).let { e -> if (keepExistingIds) e else e.copy(id = 0) }
            val newId = dao.upsertSubtask(entity)
            if (entity.id != 0L) entity.id else newId
        }
        dao.deleteSubtasksExcept(id, subtaskIds)
        dao.clearTags(id)
        dao.insertTags(task.tags.map { TaskTagEntity(id, it) })
        id
    }

    override suspend fun setOccurrenceState(taskId: Long, date: LocalDate, status: OccurrenceStatus?, at: Instant) {
        db.withTransaction {
            val recurring = dao.isRecurring(taskId) ?: return@withTransaction
            if (status == null) {
                dao.clearState(taskId, date.toEpochDay())
            } else {
                dao.upsertState(OccurrenceStateEntity(taskId, date.toEpochDay(), status.name, at.toEpochMilli()))
            }
            if (!recurring) {
                dao.setCompletedAt(taskId, if (status == OccurrenceStatus.DONE) at.toEpochMilli() else null, at.toEpochMilli())
            } else {
                dao.touch(taskId, at.toEpochMilli())
            }
        }
    }

    override suspend fun setSubtaskDone(subtaskId: Long, done: Boolean, at: Instant) {
        db.withTransaction {
            dao.setSubtaskDone(subtaskId, done)
            dao.taskIdOfSubtask(subtaskId)?.let { dao.touch(it, at.toEpochMilli()) }
        }
    }

    override suspend fun moveToTrash(taskId: Long, at: Instant) = dao.setDeletedAt(taskId, at.toEpochMilli(), at.toEpochMilli())

    override suspend fun restoreFromTrash(taskId: Long, at: Instant) = dao.setDeletedAt(taskId, null, at.toEpochMilli())

    override suspend fun deletePermanently(taskId: Long) {
        dao.deleteTrashed(taskId)
    }

    override suspend fun saveCategory(category: Category): Long = dao.upsertCategory(category.toEntity())

    override suspend fun hasSampleData(): Boolean = dao.hasSamples()

    override suspend fun trashSampleData(at: Instant) = dao.trashSamples(at.toEpochMilli())

    suspend fun search(query: String): List<Pair<Long, String>> {
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        if (escaped.isEmpty()) return emptyList()
        return dao.search(escaped).map { it.id to it.title }
    }
}
