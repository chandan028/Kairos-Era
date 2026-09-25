package com.kairosera.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 * Schema v3: the journal. One reflection per day (kept by the repository, not a unique index, so
 * a restored entry can never collide with a newer one). Added in MIGRATION_2_3.
 */

@Entity(tableName = "journal_entries", indices = [Index("date"), Index("deletedAt")])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    /** Mood by name, or null when none was chosen. */
    val mood: String?,
    val text: String,
    val win: String,
    val lesson: String,
    val gratitude: String,
    val tomorrow: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val isSample: Boolean,
)

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NULL ORDER BY date DESC, id DESC")
    fun observeActive(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NULL AND date BETWEEN :from AND :to ORDER BY date, id")
    fun observeBetween(from: Long, to: Long): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun get(id: Long): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NULL AND date = :date ORDER BY updatedAt DESC LIMIT 1")
    suspend fun forDate(date: Long): JournalEntryEntity?

    @Insert suspend fun insert(entry: JournalEntryEntity): Long
    @Update suspend fun update(entry: JournalEntryEntity)

    @Query("UPDATE journal_entries SET deletedAt = :at, updatedAt = COALESCE(:at, updatedAt) WHERE id = :id")
    suspend fun setDeleted(id: Long, at: Long?)

    @Query("DELETE FROM journal_entries WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun deleteTrashed(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM journal_entries WHERE isSample = 1 AND deletedAt IS NULL)")
    suspend fun hasSamples(): Boolean

    @Query("UPDATE journal_entries SET deletedAt = :at, updatedAt = :at WHERE isSample = 1 AND deletedAt IS NULL")
    suspend fun trashSamples(at: Long)
}
