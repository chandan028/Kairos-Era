package com.kairosera.domain.repository

import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookNote
import com.kairosera.domain.reading.ReadingSession
import com.kairosera.domain.tracker.StudyTarget
import com.kairosera.domain.tracker.StudyTopic
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerEntry
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface TrackerRepository {
    fun observeActive(): Flow<List<Tracker>>
    fun observe(id: Long): Flow<Tracker?>
    fun observeTrash(): Flow<List<Tracker>>
    fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<TrackerEntry>>
    fun observeEntriesFor(trackerId: Long, from: LocalDate, to: LocalDate): Flow<List<TrackerEntry>>
    suspend fun get(id: Long): Tracker?
    /** Saves the tracker and its fields in one transaction. Removed fields are disabled, not deleted, so history survives. */
    suspend fun save(tracker: Tracker): Long
    /** Replaces one day's values for a tracker atomically. */
    suspend fun saveEntry(entry: TrackerEntry)
    suspend fun moveToTrash(id: Long, at: Instant)
    suspend fun restore(id: Long, at: Instant)
    suspend fun deletePermanently(id: Long)
    suspend fun reorder(idsInOrder: List<Long>)
}

interface StudyRepository {
    fun observeTopics(trackerId: Long): Flow<List<StudyTopic>>
    fun observeAllTopics(): Flow<List<StudyTopic>>
    fun observeTargets(trackerId: Long, date: LocalDate): Flow<List<StudyTarget>>
    fun observeTargetsBetween(from: LocalDate, to: LocalDate): Flow<List<StudyTarget>>
    suspend fun saveTopic(topic: StudyTopic): Long
    /** Deletes a topic; its children move up to its parent so nothing is lost silently. */
    suspend fun deleteTopic(id: Long)
    suspend fun saveTarget(target: StudyTarget): Long
    suspend fun deleteTarget(id: Long)
    /** Marks a target done/undone and applies [com.kairosera.domain.tracker.StudyProgress] to its topic, in one transaction. */
    suspend fun setTargetDone(id: Long, done: Boolean, at: Instant)
}

interface BookRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeBook(id: Long): Flow<Book?>
    fun observeTrash(): Flow<List<Book>>
    fun observeSessions(bookId: Long): Flow<List<ReadingSession>>
    fun observeSessionsBetween(from: LocalDate, to: LocalDate): Flow<List<ReadingSession>>
    fun observeNotes(bookId: Long): Flow<List<BookNote>>
    suspend fun get(id: Long): Book?
    suspend fun save(book: Book): Long
    /** Records a session and moves the bookmark in one transaction. */
    suspend fun logSession(bookId: Long, pages: Int, minutes: Int, date: LocalDate, at: Instant)
    suspend fun deleteSession(id: Long)
    suspend fun saveNote(note: BookNote): Long
    suspend fun deleteNote(id: Long)
    suspend fun moveToTrash(id: Long, at: Instant)
    suspend fun restore(id: Long, at: Instant)
    suspend fun deletePermanently(id: Long)
}
