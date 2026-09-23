package com.kairosera.data.repository

import androidx.room.withTransaction
import com.kairosera.core.database.KairosDatabase
import com.kairosera.core.database.ReadingSessionEntity
import com.kairosera.core.database.TrackerValueEntity
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookNote
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.reading.ReadingMath
import com.kairosera.domain.reading.ReadingSession
import com.kairosera.domain.repository.BookRepository
import com.kairosera.domain.repository.StudyRepository
import com.kairosera.domain.repository.TrackerRepository
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.StudyProgress
import com.kairosera.domain.tracker.StudyTarget
import com.kairosera.domain.tracker.StudyTopic
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class RoomTrackerRepository(private val db: KairosDatabase) : TrackerRepository {
    private val dao = db.trackerDao()

    override fun observeActive(): Flow<List<Tracker>> = dao.observeActive().map { l -> l.map { it.toDomain() } }
    override fun observe(id: Long): Flow<Tracker?> = dao.observe(id).map { it?.toDomain() }
    override fun observeTrash(): Flow<List<Tracker>> = dao.observeTrash().map { l -> l.map { it.toDomain() } }

    override fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<TrackerEntry>> =
        dao.observeValues(from.toEpochDay(), to.toEpochDay()).map { it.toEntries() }

    override fun observeEntriesFor(trackerId: Long, from: LocalDate, to: LocalDate): Flow<List<TrackerEntry>> =
        dao.observeValuesFor(trackerId, from.toEpochDay(), to.toEpochDay()).map { it.toEntries() }

    override suspend fun get(id: Long): Tracker? = dao.get(id)?.toDomain()

    override suspend fun save(tracker: Tracker): Long = db.withTransaction {
        val existing = if (tracker.id != 0L) dao.get(tracker.id) else null
        val id = if (existing == null) {
            dao.insert(tracker.copy(id = 0, position = dao.nextPosition()).toEntity())
        } else {
            dao.update(tracker.copy(createdAt = existing.tracker.createdAt.toInstant(), deletedAt = existing.tracker.deletedAt?.toInstant()).toEntity())
            tracker.id
        }
        val ownFieldIds = existing?.fields?.map { it.id }?.toSet().orEmpty()
        val kept = tracker.fields.mapIndexed { index, field ->
            if (field.id != 0L && field.id in ownFieldIds) {
                dao.updateField(field.toEntity(id, index))
                field.id
            } else {
                dao.insertField(field.copy(id = 0).toEntity(id, index))
            }
        }
        // Fields the user removed are switched off, never deleted: their history stays intact.
        if (existing != null) dao.disableFieldsExcept(id, kept.ifEmpty { listOf(-1L) })
        id
    }

    override suspend fun saveEntry(entry: TrackerEntry) = db.withTransaction {
        val tracker = dao.get(entry.trackerId) ?: return@withTransaction
        val fieldIds = tracker.fields.map { it.id }.toSet()
        val types = tracker.fields.associate { it.id to it.type }
        val day = entry.date.toEpochDay()
        val at = entry.updatedAt.toEpochMilli()
        dao.clearDay(entry.trackerId, day)
        val rows = entry.values.values
            .filter { it.fieldId in fieldIds }
            .filter { v -> v.number != null || !v.text.isNullOrBlank() || v.checked.isNotEmpty() }
            .map { v ->
                TrackerValueEntity(
                    fieldId = v.fieldId, date = day, trackerId = entry.trackerId,
                    number = v.number?.takeIf { !it.isNaN() && !it.isInfinite() },
                    text = v.text?.takeIf { types[v.fieldId] == MeasurementType.TEXT.name }?.take(MAX_TEXT),
                    checked = v.checked.sorted().joinToString(","),
                    updatedAt = at,
                )
            }
        if (rows.isNotEmpty()) dao.upsertValues(rows)
    }

    override suspend fun moveToTrash(id: Long, at: Instant) = dao.setDeleted(id, at.toEpochMilli())

    override suspend fun restore(id: Long, at: Instant) = db.withTransaction {
        val t = dao.get(id) ?: return@withTransaction
        dao.update(t.tracker.copy(deletedAt = null, updatedAt = at.toEpochMilli()))
    }

    override suspend fun deletePermanently(id: Long) = dao.deleteTrashed(id)

    override suspend fun reorder(idsInOrder: List<Long>) = db.withTransaction {
        idsInOrder.forEachIndexed { index, id -> dao.setPosition(id, index) }
    }

    private companion object { const val MAX_TEXT = 4000 }
}

class RoomStudyRepository(private val db: KairosDatabase) : StudyRepository {
    private val dao = db.studyDao()

    override fun observeTopics(trackerId: Long): Flow<List<StudyTopic>> = dao.observeTopics(trackerId).map { l -> l.map { it.toDomain() } }
    override fun observeAllTopics(): Flow<List<StudyTopic>> = dao.observeAllTopics().map { l -> l.map { it.toDomain() } }
    override fun observeTargets(trackerId: Long, date: LocalDate): Flow<List<StudyTarget>> =
        dao.observeTargets(trackerId, date.toEpochDay()).map { l -> l.map { it.toDomain() } }
    override fun observeTargetsBetween(from: LocalDate, to: LocalDate): Flow<List<StudyTarget>> =
        dao.observeTargetsBetween(from.toEpochDay(), to.toEpochDay()).map { l -> l.map { it.toDomain() } }

    override suspend fun saveTopic(topic: StudyTopic): Long = db.withTransaction {
        // A topic can't become its own ancestor; such a parent is dropped rather than creating a loop.
        val safeParent = topic.parentId?.takeIf { p -> topic.id == 0L || !isDescendantOrSelf(p, topic.id) }
        val t = topic.copy(parentId = safeParent)
        if (t.id == 0L) dao.insertTopic(t.copy(position = dao.nextTopicPosition(t.trackerId)).toEntity())
        else { dao.updateTopic(t.toEntity()); t.id }
    }

    private suspend fun isDescendantOrSelf(candidate: Long, of: Long): Boolean {
        var cur: Long? = candidate
        var guard = 0
        while (cur != null && guard++ < 1000) {
            if (cur == of) return true
            cur = dao.getTopic(cur)?.parentId
        }
        return false
    }

    override suspend fun deleteTopic(id: Long) = db.withTransaction {
        val t = dao.getTopic(id) ?: return@withTransaction
        dao.reparent(id, t.parentId)
        dao.deleteTopic(id)
    }

    override suspend fun saveTarget(target: StudyTarget): Long =
        if (target.id == 0L) dao.insertTarget(target.toEntity()) else { dao.updateTarget(target.toEntity()); target.id }

    override suspend fun deleteTarget(id: Long) = dao.deleteTarget(id)

    override suspend fun setTargetDone(id: Long, done: Boolean, at: Instant) = db.withTransaction {
        val target = dao.getTarget(id) ?: return@withTransaction
        if (target.done == done) return@withTransaction
        dao.updateTarget(target.copy(done = done))
        val topic = target.topicId?.let { dao.getTopic(it) }?.toDomain() ?: return@withTransaction
        val updated = if (done) StudyProgress.onTargetCompleted(topic, at) else StudyProgress.onTargetUncompleted(topic, at)
        dao.updateTopic(updated.toEntity())
    }
}

class RoomBookRepository(private val db: KairosDatabase) : BookRepository {
    private val dao = db.bookDao()

    override fun observeBooks(): Flow<List<Book>> = dao.observeBooks().map { l -> l.map { it.toDomain() } }
    override fun observeBook(id: Long): Flow<Book?> = dao.observe(id).map { it?.toDomain() }
    override fun observeTrash(): Flow<List<Book>> = dao.observeTrash().map { l -> l.map { it.toDomain() } }
    override fun observeSessions(bookId: Long): Flow<List<ReadingSession>> = dao.observeSessions(bookId).map { l -> l.map { it.toDomain() } }
    override fun observeSessionsBetween(from: LocalDate, to: LocalDate): Flow<List<ReadingSession>> =
        dao.observeSessionsBetween(from.toEpochDay(), to.toEpochDay()).map { l -> l.map { it.toDomain() } }
    override fun observeNotes(bookId: Long): Flow<List<BookNote>> = dao.observeNotes(bookId).map { l -> l.map { it.toDomain() } }

    override suspend fun get(id: Long): Book? = dao.get(id)?.toDomain()

    override suspend fun save(book: Book): Long = db.withTransaction {
        val clamped = book.copy(
            totalPages = book.totalPages.coerceAtLeast(0),
            currentPage = book.currentPage.coerceAtLeast(0).let { if (book.totalPages > 0) it.coerceAtMost(book.totalPages) else it },
        )
        if (clamped.id == 0L) dao.insert(clamped.toEntity())
        else {
            val existing = dao.get(clamped.id)
            dao.update(clamped.copy(createdAt = existing?.createdAt?.toInstant() ?: clamped.createdAt, deletedAt = existing?.deletedAt?.toInstant()).toEntity())
            clamped.id
        }
    }

    override suspend fun logSession(bookId: Long, pages: Int, minutes: Int, date: LocalDate, at: Instant) = db.withTransaction {
        val book = dao.get(bookId)?.toDomain() ?: return@withTransaction
        val before = book.currentPage
        val after = ReadingMath.afterSession(book, pages, date, at)
        // Store the pages that actually moved the bookmark, so totals never exceed the book.
        val counted = if (book.totalPages > 0) after.currentPage - before else pages.coerceAtLeast(0)
        dao.insertSession(
            ReadingSessionEntity(bookId = bookId, date = date.toEpochDay(), pages = counted, minutes = minutes.coerceIn(0, 24 * 60), createdAt = at.toEpochMilli()),
        )
        dao.update(after.toEntity())
    }

    override suspend fun deleteSession(id: Long) = db.withTransaction {
        val s = dao.getSession(id) ?: return@withTransaction
        dao.deleteSession(id)
        val book = dao.get(s.bookId) ?: return@withTransaction
        dao.update(book.copy(currentPage = (book.currentPage - s.pages).coerceAtLeast(0)))
    }

    override suspend fun saveNote(note: BookNote): Long =
        if (note.id == 0L) dao.insertNote(note.toEntity()) else { dao.updateNote(note.toEntity()); note.id }

    override suspend fun deleteNote(id: Long) = dao.deleteNote(id)

    override suspend fun moveToTrash(id: Long, at: Instant) = dao.setDeleted(id, at.toEpochMilli())

    override suspend fun restore(id: Long, at: Instant) = db.withTransaction {
        val b = dao.get(id) ?: return@withTransaction
        dao.update(b.copy(deletedAt = null, updatedAt = at.toEpochMilli()))
    }

    override suspend fun deletePermanently(id: Long) = dao.deleteTrashed(id)
}

/** Marks a book finished (or reopens it) without touching its notes or insights. */
fun Book.withStatus(status: BookStatus, today: LocalDate, now: Instant): Book = copy(
    status = status,
    finishedDate = if (status == BookStatus.FINISHED) finishedDate ?: today else null,
    currentPage = if (status == BookStatus.FINISHED && totalPages > 0) totalPages else currentPage,
    startDate = if (status != BookStatus.WANT_TO_READ) startDate ?: today else startDate,
    updatedAt = now,
)
