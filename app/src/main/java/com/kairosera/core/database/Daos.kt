package com.kairosera.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedAt IS NULL AND archivedAt IS NULL AND (
            (repeatFrequency IS NULL AND date BETWEEN :from AND :to) OR
            (repeatFrequency IS NOT NULL AND date <= :to AND (seriesEnd IS NULL OR seriesEnd >= :from))
        )
        """,
    )
    fun observeBetween(from: Long, to: Long): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskWithDetails?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskWithDetails?

    @Transaction
    @Query(
        """
        SELECT * FROM tasks WHERE deletedAt IS NULL AND archivedAt IS NULL
        AND id IN (SELECT DISTINCT taskId FROM reminders)
        """,
    )
    suspend fun getWithReminders(): List<TaskWithDetails>

    @Transaction
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<TaskWithDetails>>

    @Query(
        """
        SELECT * FROM tasks WHERE deletedAt IS NULL AND
        (title LIKE '%' || :query || '%' ESCAPE '\' OR description LIKE '%' || :query || '%' ESCAPE '\'
         OR notes LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY updatedAt DESC LIMIT 50
        """,
    )
    suspend fun search(query: String): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompletedAt(id: Long, completedAt: Long?, updatedAt: Long)

    @Query("UPDATE tasks SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Long?, updatedAt: Long)

    @Query("UPDATE tasks SET deletedAt = :at, updatedAt = :at WHERE isSample = 1 AND deletedAt IS NULL")
    suspend fun trashSamples(at: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM tasks WHERE isSample = 1 AND deletedAt IS NULL)")
    suspend fun hasSamples(): Boolean

    @Query("DELETE FROM tasks WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun deleteTrashed(id: Long): Int

    @Query("SELECT * FROM reminders WHERE taskId = :taskId")
    suspend fun reminders(taskId: Long): List<ReminderEntity>

    @Upsert
    suspend fun upsertReminder(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE taskId = :taskId AND id NOT IN (:keep)")
    suspend fun deleteRemindersExcept(taskId: Long, keep: List<Long>)

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId")
    suspend fun subtasks(taskId: Long): List<SubtaskEntity>

    @Upsert
    suspend fun upsertSubtask(subtask: SubtaskEntity): Long

    @Query("DELETE FROM subtasks WHERE taskId = :taskId AND id NOT IN (:keep)")
    suspend fun deleteSubtasksExcept(taskId: Long, keep: List<Long>)

    @Query("UPDATE subtasks SET done = :done WHERE id = :id")
    suspend fun setSubtaskDone(id: Long, done: Boolean)

    @Query("SELECT taskId FROM subtasks WHERE id = :id")
    suspend fun taskIdOfSubtask(id: Long): Long?

    @Query("UPDATE tasks SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun clearTags(taskId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TaskTagEntity>)

    @Query("SELECT * FROM occurrence_states WHERE date BETWEEN :from AND :to")
    fun observeStates(from: Long, to: Long): Flow<List<OccurrenceStateEntity>>

    @Query("SELECT * FROM occurrence_states WHERE date BETWEEN :from AND :to")
    suspend fun getStates(from: Long, to: Long): List<OccurrenceStateEntity>

    @Upsert
    suspend fun upsertState(state: OccurrenceStateEntity)

    @Query("DELETE FROM occurrence_states WHERE taskId = :taskId AND date = :date")
    suspend fun clearState(taskId: Long, date: Long)

    @Query("SELECT repeatFrequency IS NOT NULL FROM tasks WHERE id = :id")
    suspend fun isRecurring(id: Long): Boolean?

    @Query("SELECT * FROM categories ORDER BY position")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity): Long

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM scheduled_notifications WHERE occurrenceDate >= :fromDay")
    suspend fun since(fromDay: Long): List<ScheduledNotificationEntity>

    @Query("SELECT * FROM scheduled_notifications WHERE id = :id")
    suspend fun get(id: Long): ScheduledNotificationEntity?

    @Upsert
    suspend fun upsert(row: ScheduledNotificationEntity): Long

    @Query("UPDATE scheduled_notifications SET status = :status, updatedAt = :at WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, at: Long)

    @Query("UPDATE scheduled_notifications SET status = :status, updatedAt = :at, triggerAt = :triggerAt WHERE id = :id")
    suspend fun setStatusAndTrigger(id: Long, status: String, at: Long, triggerAt: Long)

    @Query(
        """
        SELECT * FROM scheduled_notifications WHERE taskId = :taskId
        AND status IN ('SCHEDULED', 'SNOOZED', 'SHOWING')
        """,
    )
    suspend fun activeForTask(taskId: Long): List<ScheduledNotificationEntity>
}
