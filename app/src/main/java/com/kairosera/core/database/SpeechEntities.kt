package com.kairosera.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Schema v6: the speaking coach's reports. One new table (MIGRATION_5_6), nothing existing changes.
 * Scores are null when Gemma said "not_available". Lists are stored as JSON arrays of strings.
 */
@Entity(tableName = "speech_sessions", indices = [Index("date")])
data class SpeechSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch day the speech was recorded on. */
    val date: Long,
    val createdAt: Long,
    val topic: String,
    /** The Winter Arc speaking session (daily topic) this belongs to, if any. */
    val speakingSessionId: Long?,
    val durationSeconds: Int,
    /** The headline score shown in the report (mean of the category scores), 1.0 to 10.0. */
    val overallScore: Double?,
    /** Gemma's own overall_score. */
    val modelOverallScore: Int?,
    val clarityScore: Int?,
    val structureScore: Int?,
    val vocabularyScore: Int?,
    val grammarScore: Int?,
    val concisenessScore: Int?,
    /** JSON array, or null when Gemma could not tell. */
    val fillerWords: String?,
    val strengths: String,
    val improvements: String,
    val nextExercise: String,
    val summary: String,
    val transcript: String,
    val wordsPerMinute: Int?,
    val fillerSounds: Int?,
    val longPauses: Int?,
    val voicedSeconds: Double?,
    /** "GPU" or "CPU": where Gemma actually ran. */
    val backend: String,
    /** Set only if the person chose to keep recordings. App-private path. */
    val audioPath: String?,
)

@Dao
interface SpeechDao {
    @Insert suspend fun insert(s: SpeechSessionEntity): Long

    @Query("SELECT * FROM speech_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SpeechSessionEntity>>

    @Query("SELECT * FROM speech_sessions WHERE id = :id")
    fun observe(id: Long): Flow<SpeechSessionEntity?>

    @Query("SELECT * FROM speech_sessions WHERE id = :id")
    suspend fun byId(id: Long): SpeechSessionEntity?

    @Query("UPDATE speech_sessions SET audioPath = :path WHERE id = :id")
    suspend fun setAudioPath(id: Long, path: String?)

    @Query("DELETE FROM speech_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}
