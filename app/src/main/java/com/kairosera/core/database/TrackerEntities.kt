package com.kairosera.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Schema v2: trackers, study plans and reading. Same conventions as Entities.kt
 * (epoch days, epoch millis, enums by name). Added in MIGRATION_1_2.
 */

@Entity(tableName = "trackers", indices = [Index("deletedAt")])
data class TrackerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val colorArgb: Long,
    val description: String,
    val why: String,
    val gain: String,
    val template: String,
    /** DAILY, SELECTED_DAYS, TIMES_PER_WEEK or TIMES_PER_MONTH. */
    val frequency: String,
    /** Weekday bit mask for SELECTED_DAYS (Monday = bit 0). */
    val frequencyDays: Int,
    /** Target count for TIMES_PER_WEEK / TIMES_PER_MONTH. */
    val frequencyTimes: Int,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val isSample: Boolean,
)

@Entity(
    tableName = "tracker_fields",
    foreignKeys = [ForeignKey(entity = TrackerEntity::class, parentColumns = ["id"], childColumns = ["trackerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackerId")],
)
data class TrackerFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val label: String,
    val type: String,
    val unit: String,
    val target: Double?,
    /** CHECKLIST items, one per line. */
    val options: String,
    val enabled: Boolean,
    val position: Int,
)

/** One value per field per day. A day's entry is the set of rows for (trackerId, date). */
@Entity(
    tableName = "tracker_values",
    primaryKeys = ["fieldId", "date"],
    foreignKeys = [
        ForeignKey(entity = TrackerFieldEntity::class, parentColumns = ["id"], childColumns = ["fieldId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrackerEntity::class, parentColumns = ["id"], childColumns = ["trackerId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["trackerId", "date"]), Index("date")],
)
data class TrackerValueEntity(
    val fieldId: Long,
    val date: Long,
    val trackerId: Long,
    val number: Double?,
    val text: String?,
    /** Checked CHECKLIST indexes, comma separated. */
    val checked: String,
    val updatedAt: Long,
)

data class TrackerWithFields(
    @Embedded val tracker: TrackerEntity,
    @Relation(parentColumn = "id", entityColumn = "trackerId") val fields: List<TrackerFieldEntity>,
)

@Entity(
    tableName = "study_topics",
    foreignKeys = [ForeignKey(entity = TrackerEntity::class, parentColumns = ["id"], childColumns = ["trackerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackerId"), Index("parentId")],
)
data class StudyTopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val parentId: Long?,
    val title: String,
    val status: String,
    val notes: String,
    val resources: String,
    val questions: String,
    val practiceDone: Int,
    val confidence: Int,
    val position: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "study_targets",
    foreignKeys = [
        ForeignKey(entity = TrackerEntity::class, parentColumns = ["id"], childColumns = ["trackerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StudyTopicEntity::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["trackerId", "date"]), Index("topicId"), Index("date")],
)
data class StudyTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val date: Long,
    val topicId: Long?,
    val title: String,
    val done: Boolean,
    val position: Int,
)

@Entity(tableName = "books", indices = [Index("deletedAt"), Index("status")])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val totalPages: Int,
    val currentPage: Int,
    val status: String,
    val startDate: Long?,
    val targetDate: Long?,
    val finishedDate: Long?,
    val coverColor: Long,
    val whyStarted: String,
    val expected: String,
    val learned: String,
    val applied: String,
    val changed: String,
    val skills: String,
    val keyIdeas: String,
    val finalTakeaway: String,
    val recommendAgain: Boolean?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val isSample: Boolean,
)

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId"), Index("date")],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val date: Long,
    val pages: Int,
    val minutes: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "book_notes",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class BookNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val kind: String,
    val text: String,
    val page: Int?,
    val createdAt: Long,
)
