package com.kairosera.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Transaction
    @Query("SELECT * FROM trackers WHERE deletedAt IS NULL AND archivedAt IS NULL ORDER BY position, id")
    fun observeActive(): Flow<List<TrackerWithFields>>

    @Transaction
    @Query("SELECT * FROM trackers WHERE id = :id")
    fun observe(id: Long): Flow<TrackerWithFields?>

    @Transaction
    @Query("SELECT * FROM trackers WHERE id = :id")
    suspend fun get(id: Long): TrackerWithFields?

    @Transaction
    @Query("SELECT * FROM trackers WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<TrackerWithFields>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM trackers")
    suspend fun nextPosition(): Int

    @Insert suspend fun insert(tracker: TrackerEntity): Long
    @Update suspend fun update(tracker: TrackerEntity)
    @Insert suspend fun insertField(field: TrackerFieldEntity): Long
    @Update suspend fun updateField(field: TrackerFieldEntity)

    @Query("UPDATE tracker_fields SET enabled = 0 WHERE trackerId = :trackerId AND id NOT IN (:keep)")
    suspend fun disableFieldsExcept(trackerId: Long, keep: List<Long>)

    @Query("UPDATE trackers SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: Long, at: Long?)

    @Query("UPDATE trackers SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM trackers WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun deleteTrashed(id: Long)

    @Query("SELECT * FROM tracker_values WHERE date BETWEEN :from AND :to")
    fun observeValues(from: Long, to: Long): Flow<List<TrackerValueEntity>>

    @Query("SELECT * FROM tracker_values WHERE trackerId = :trackerId AND date BETWEEN :from AND :to")
    fun observeValuesFor(trackerId: Long, from: Long, to: Long): Flow<List<TrackerValueEntity>>

    @Query("DELETE FROM tracker_values WHERE trackerId = :trackerId AND date = :date")
    suspend fun clearDay(trackerId: Long, date: Long)

    @Upsert suspend fun upsertValues(values: List<TrackerValueEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM trackers WHERE isSample = 1 AND deletedAt IS NULL)")
    suspend fun hasSamples(): Boolean

    @Query("UPDATE trackers SET deletedAt = :at, updatedAt = :at WHERE isSample = 1 AND deletedAt IS NULL")
    suspend fun trashSamples(at: Long)
}

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_topics WHERE trackerId = :trackerId ORDER BY position, id")
    fun observeTopics(trackerId: Long): Flow<List<StudyTopicEntity>>

    @Query("SELECT * FROM study_topics ORDER BY position, id")
    fun observeAllTopics(): Flow<List<StudyTopicEntity>>

    @Query("SELECT * FROM study_topics WHERE id = :id")
    suspend fun getTopic(id: Long): StudyTopicEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM study_topics WHERE trackerId = :trackerId")
    suspend fun nextTopicPosition(trackerId: Long): Int

    @Insert suspend fun insertTopic(topic: StudyTopicEntity): Long
    @Update suspend fun updateTopic(topic: StudyTopicEntity)

    @Query("UPDATE study_topics SET parentId = :newParent WHERE parentId = :oldParent")
    suspend fun reparent(oldParent: Long, newParent: Long?)

    @Query("DELETE FROM study_topics WHERE id = :id")
    suspend fun deleteTopic(id: Long)

    @Query("SELECT * FROM study_targets WHERE trackerId = :trackerId AND date = :date ORDER BY position, id")
    fun observeTargets(trackerId: Long, date: Long): Flow<List<StudyTargetEntity>>

    @Query("SELECT * FROM study_targets WHERE date BETWEEN :from AND :to")
    fun observeTargetsBetween(from: Long, to: Long): Flow<List<StudyTargetEntity>>

    @Query("SELECT * FROM study_targets WHERE id = :id")
    suspend fun getTarget(id: Long): StudyTargetEntity?

    @Insert suspend fun insertTarget(target: StudyTargetEntity): Long
    @Update suspend fun updateTarget(target: StudyTargetEntity)

    @Query("DELETE FROM study_targets WHERE id = :id")
    suspend fun deleteTarget(id: Long)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<BookEntity>>

    @Insert suspend fun insert(book: BookEntity): Long
    @Update suspend fun update(book: BookEntity)

    @Query("UPDATE books SET deletedAt = :at, updatedAt = COALESCE(:at, updatedAt) WHERE id = :id")
    suspend fun setDeleted(id: Long, at: Long?)

    @Query("DELETE FROM books WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun deleteTrashed(id: Long)

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY date DESC, id DESC")
    fun observeSessions(bookId: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE date BETWEEN :from AND :to")
    fun observeSessionsBetween(from: Long, to: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE id = :id")
    suspend fun getSession(id: Long): ReadingSessionEntity?

    @Insert suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM book_notes WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeNotes(bookId: Long): Flow<List<BookNoteEntity>>

    @Insert suspend fun insertNote(note: BookNoteEntity): Long
    @Update suspend fun updateNote(note: BookNoteEntity)

    @Query("DELETE FROM book_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE isSample = 1 AND deletedAt IS NULL)")
    suspend fun hasSamples(): Boolean

    @Query("UPDATE books SET deletedAt = :at, updatedAt = :at WHERE isSample = 1 AND deletedAt IS NULL")
    suspend fun trashSamples(at: Long)
}
