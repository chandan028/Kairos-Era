package com.kairosera.data.repository

import androidx.room.withTransaction
import com.kairosera.core.database.JournalEntryEntity
import com.kairosera.core.database.KairosDatabase
import com.kairosera.domain.journal.JournalEntry
import com.kairosera.domain.journal.Mood
import com.kairosera.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class RoomJournalRepository(private val db: KairosDatabase) : JournalRepository {
    private val dao = db.journalDao()

    override fun observeEntries(): Flow<List<JournalEntry>> = dao.observeActive().map { l -> l.map { it.toDomain() } }
    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<JournalEntry>> =
        dao.observeBetween(from.toEpochDay(), to.toEpochDay()).map { l -> l.map { it.toDomain() } }
    override fun observeTrash(): Flow<List<JournalEntry>> = dao.observeTrash().map { l -> l.map { it.toDomain() } }
    override suspend fun get(id: Long): JournalEntry? = dao.get(id)?.toDomain()
    override suspend fun forDate(date: LocalDate): JournalEntry? = dao.forDate(date.toEpochDay())?.toDomain()

    /**
     * Saving a new entry for a day that already has one updates that entry instead, so two
     * screens (or a widget and the app) can never split one day's reflection in two.
     */
    override suspend fun save(entry: JournalEntry): Long = db.withTransaction {
        val clean = entry.copy(
            text = entry.text.take(JournalEntry.MAX_TEXT),
            win = entry.win.take(JournalEntry.MAX_LINE),
            lesson = entry.lesson.take(JournalEntry.MAX_LINE),
            gratitude = entry.gratitude.take(JournalEntry.MAX_LINE),
            tomorrow = entry.tomorrow.take(JournalEntry.MAX_LINE),
        )
        val existing = if (clean.id != 0L) dao.get(clean.id) else dao.forDate(clean.date.toEpochDay())
        if (existing == null) {
            dao.insert(clean.copy(id = 0, deletedAt = null).toEntity())
        } else {
            dao.update(clean.copy(id = existing.id, createdAt = existing.createdAt.toInstant(), deletedAt = existing.deletedAt?.toInstant(), isSample = existing.isSample && clean.isSample).toEntity())
            existing.id
        }
    }

    override suspend fun moveToTrash(id: Long, at: Instant) = dao.setDeleted(id, at.toEpochMilli())
    override suspend fun restore(id: Long, at: Instant) = dao.setDeleted(id, null)
    override suspend fun deletePermanently(id: Long) = dao.deleteTrashed(id)
    override suspend fun trashSamples(at: Instant) = dao.trashSamples(at.toEpochMilli())
    suspend fun hasSamples(): Boolean = dao.hasSamples()
}

internal fun JournalEntryEntity.toDomain() = JournalEntry(
    id = id, date = date.toDate(), mood = mood?.let { m -> Mood.entries.firstOrNull { it.name == m } },
    text = text, win = win, lesson = lesson, gratitude = gratitude, tomorrow = tomorrow,
    createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(), deletedAt = deletedAt?.toInstant(), isSample = isSample,
)

internal fun JournalEntry.toEntity() = JournalEntryEntity(
    id = id, date = date.toEpochDay(), mood = mood?.name, text = text, win = win, lesson = lesson, gratitude = gratitude,
    tomorrow = tomorrow, createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(), isSample = isSample,
)
