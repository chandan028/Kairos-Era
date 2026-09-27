package com.kairosera.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Storage conventions:
 * - Calendar dates are stored as epoch days (Long), so date-range queries use indexes.
 * - Local times are minutes of the day (Int), because reminders are floating local times.
 * - Instants are epoch milliseconds (Long).
 * - Enums are stored by name (String), never by ordinal, so reordering an enum can't corrupt data.
 */

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long,
    val icon: String,
    val position: Int,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("date"), Index("categoryId"), Index("deletedAt"), Index("seriesEnd")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val notes: String,
    /** First (or only) occurrence date, epoch day. */
    val date: Long,
    val startMinute: Int?,
    val endMinute: Int?,
    val priority: String,
    val categoryId: Long?,
    /** Recurrence rule, stored with the task definition and never mixed with occurrence state. */
    val repeatFrequency: String?,
    val repeatInterval: Int,
    /** Bit mask, Monday = bit 0 ... Sunday = bit 6. */
    val repeatWeekdays: Int,
    val repeatEndDate: Long?,
    val repeatCount: Int?,
    /** Last possible occurrence (epoch day) or null when unbounded. Derived, used for range queries. */
    val seriesEnd: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Completion time of a one-time task. Recurring tasks keep completion per occurrence. */
    val completedAt: Long?,
    val archivedAt: Long?,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val isSample: Boolean,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    /** AT_TIME or BEFORE_START. */
    val kind: String,
    val minuteOfDay: Int?,
    val dayOffset: Int,
    val minutesBefore: Int?,
    val persistent: Boolean,
    val sound: String,
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean,
    val position: Int,
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tag"],
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tag")],
)
data class TaskTagEntity(
    val taskId: Long,
    val tag: String,
)

@Entity(
    tableName = "occurrence_states",
    primaryKeys = ["taskId", "date"],
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date")],
)
data class OccurrenceStateEntity(
    val taskId: Long,
    val date: Long,
    /** DONE or SKIPPED. Absence of a row means the occurrence is open. */
    val status: String,
    val changedAt: Long,
)

/**
 * Reminder -> notification id -> trigger time -> status. The row id is both the Android
 * notification id and the alarm request code, which is what makes scheduling idempotent.
 * No foreign key on purpose: when a task or reminder disappears, the scheduler must still
 * find these rows to cancel their alarms.
 */
@Entity(
    tableName = "scheduled_notifications",
    indices = [Index(value = ["reminderId", "occurrenceDate"]), Index("occurrenceDate"), Index("taskId")],
)
data class ScheduledNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val taskId: Long,
    val occurrenceDate: Long,
    val triggerAt: Long,
    val status: String,
    val updatedAt: Long,
)

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId") val reminders: List<ReminderEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId") val subtasks: List<SubtaskEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId") val tags: List<TaskTagEntity>,
)
