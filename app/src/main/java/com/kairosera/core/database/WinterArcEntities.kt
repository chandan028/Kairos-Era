package com.kairosera.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Schema v4: Winter Arc. New tables only (MIGRATION_3_4), so nothing that existed is touched.
 * Schema v5 adds name, icon and color to wa_habits for custom habits (MIGRATION_4_5).
 * Reading is not duplicated here: Winter Arc reads and writes the Read section's books and
 * reading sessions. Daily summaries are always derived, never stored.
 */

@Entity(tableName = "winter_arcs")
data class WinterArcEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: Long,
    val endDate: Long,
    val durationDays: Int,
    /** ArcStatus by name. */
    val status: String,
    /** Encoded pauses, see WinterArc.encodePauses. */
    val pauses: String,
    /** The book read for the reading habit (books.id), or null. Not a foreign key: a deleted book just unlinks. */
    val bookId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "wa_habits")
data class WaHabitEntity(
    /** HabitKind name, or "CUSTOM_…" for a habit the person added. */
    @PrimaryKey val id: String,
    val category: String,
    val type: String,
    val target: Double,
    val unit: String,
    val active: Boolean,
    val sortOrder: Int,
    /** Custom habits only (v5): the name, icon key and color index. Empty for built-in habits. */
    @ColumnInfo(defaultValue = "") val name: String = "",
    @ColumnInfo(defaultValue = "") val icon: String = "",
    @ColumnInfo(defaultValue = "0") val color: Int = 0,
)

@Entity(tableName = "wa_habit_logs", primaryKeys = ["habitId", "date"], indices = [Index("date")])
data class WaHabitLogEntity(
    val habitId: String,
    val date: Long,
    val value: Double,
    val completed: Boolean,
    val notes: String,
    val extra: Double,
    val updatedAt: Long,
)

@Entity(tableName = "wa_study_tasks", indices = [Index("date")])
data class WaStudyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val title: String,
    val category: String,
    val durationMinutes: Int,
    val completed: Boolean,
    val notes: String,
    val position: Int,
    val source: String,
    val spentMinutes: Int,
    /** Minute of day, or null for any time. */
    val startMinute: Int?,
    val updatedAt: Long,
)

@Entity(tableName = "wa_focus_sessions", indices = [Index("date")])
data class WaFocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val durationSeconds: Int,
    val completed: Boolean,
    val interruptions: Int,
    val task: String,
    val kind: String,
    val startedAt: Long,
    val studyTaskId: Long?,
)

@Entity(tableName = "wa_speaking_sessions", indices = [Index("date")])
data class WaSpeakingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val topic: String,
    val durationSeconds: Int,
    val completed: Boolean,
    val rating: Int?,
    val updatedAt: Long,
)

@Dao
interface WinterArcDao {
    @Query("SELECT * FROM winter_arcs ORDER BY id DESC LIMIT 1")
    fun observeLatestArc(): Flow<WinterArcEntity?>

    @Query("SELECT * FROM winter_arcs ORDER BY id DESC LIMIT 1")
    suspend fun latestArc(): WinterArcEntity?

    @Insert suspend fun insertArc(arc: WinterArcEntity): Long
    @androidx.room.Update suspend fun updateArc(arc: WinterArcEntity)

    @Query("SELECT * FROM wa_habits ORDER BY sortOrder")
    fun observeHabits(): Flow<List<WaHabitEntity>>

    @Query("SELECT * FROM wa_habits ORDER BY sortOrder")
    suspend fun habits(): List<WaHabitEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertHabits(habits: List<WaHabitEntity>)
    @Upsert suspend fun upsertHabit(habit: WaHabitEntity)

    @Query("SELECT * FROM wa_habit_logs WHERE date BETWEEN :from AND :to")
    fun observeLogs(from: Long, to: Long): Flow<List<WaHabitLogEntity>>

    @Query("SELECT * FROM wa_habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun log(habitId: String, date: Long): WaHabitLogEntity?

    @Upsert suspend fun upsertLog(log: WaHabitLogEntity)

    @Query("DELETE FROM wa_habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun deleteLog(habitId: String, date: Long)

    @Query("DELETE FROM wa_habits WHERE id = :id")
    suspend fun deleteHabit(id: String)

    @Query("DELETE FROM wa_habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogs(habitId: String)

    @Query("SELECT * FROM wa_study_tasks WHERE date BETWEEN :from AND :to ORDER BY date, position, id")
    fun observeStudy(from: Long, to: Long): Flow<List<WaStudyTaskEntity>>

    @Query("SELECT * FROM wa_study_tasks ORDER BY date, position, id")
    fun observeAllStudy(): Flow<List<WaStudyTaskEntity>>

    @Query("SELECT * FROM wa_study_tasks WHERE id = :id")
    suspend fun studyTask(id: Long): WaStudyTaskEntity?

    @Query("SELECT COUNT(*) FROM wa_study_tasks")
    suspend fun studyCount(): Int

    @Insert suspend fun insertStudy(tasks: List<WaStudyTaskEntity>)
    @Insert suspend fun insertStudyTask(task: WaStudyTaskEntity): Long
    @androidx.room.Update suspend fun updateStudy(task: WaStudyTaskEntity)

    @Query("DELETE FROM wa_study_tasks WHERE id = :id")
    suspend fun deleteStudy(id: Long)

    @Query("SELECT * FROM wa_study_tasks WHERE source = 'plan' AND date >= :from")
    suspend fun planTasksFrom(from: Long): List<WaStudyTaskEntity>

    @Query("DELETE FROM wa_study_tasks WHERE source = 'plan' AND date >= :from")
    suspend fun deletePlanFrom(from: Long)

    /** Replaces imported plan rows from [from] onward, keeping what was already ticked off. */
    @Transaction
    suspend fun replacePlan(from: Long, tasks: List<WaStudyTaskEntity>) {
        val old = planTasksFrom(from).associateBy { it.date to it.title }
        deletePlanFrom(from)
        insertStudy(tasks.map { t ->
            val prev = old[t.date to t.title]
            if (prev == null) t else t.copy(completed = t.completed || prev.completed, spentMinutes = prev.spentMinutes, notes = t.notes.ifEmpty { prev.notes })
        })
    }

    @Query("SELECT * FROM wa_focus_sessions WHERE date BETWEEN :from AND :to ORDER BY startedAt")
    fun observeFocus(from: Long, to: Long): Flow<List<WaFocusSessionEntity>>

    @Insert suspend fun insertFocus(session: WaFocusSessionEntity): Long

    @Query("SELECT * FROM wa_speaking_sessions WHERE date BETWEEN :from AND :to ORDER BY date, id")
    fun observeSpeaking(from: Long, to: Long): Flow<List<WaSpeakingSessionEntity>>

    @Query("SELECT * FROM wa_speaking_sessions ORDER BY date DESC, id DESC LIMIT :limit")
    fun observeRecentSpeaking(limit: Int): Flow<List<WaSpeakingSessionEntity>>

    @Query("SELECT topic FROM wa_speaking_sessions ORDER BY date, id")
    suspend fun usedTopics(): List<String>

    @Query("SELECT * FROM wa_speaking_sessions WHERE date = :date ORDER BY id DESC LIMIT 1")
    suspend fun speakingOn(date: Long): WaSpeakingSessionEntity?

    @Query("SELECT * FROM wa_speaking_sessions WHERE id = :id")
    suspend fun speakingById(id: Long): WaSpeakingSessionEntity?

    @Insert suspend fun insertSpeaking(s: WaSpeakingSessionEntity): Long
    @androidx.room.Update suspend fun updateSpeaking(s: WaSpeakingSessionEntity)
}
