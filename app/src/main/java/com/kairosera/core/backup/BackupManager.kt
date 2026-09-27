package com.kairosera.core.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kairosera.BuildConfig
import com.kairosera.core.database.KairosDatabase
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.Instant

@Serializable
data class BackupManifest(
    val backupVersion: Int = 1,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val counts: Map<String, Int> = emptyMap(),
    val settings: Map<String, String> = emptyMap(),
)

/** A backup that opened correctly and is ready to restore. Holds the decrypted data in memory only. */
class BackupPreview internal constructor(val manifest: BackupManifest, internal val database: ByteArray) {
    val createdAt: Instant get() = Instant.ofEpochMilli(manifest.createdAt)
    fun count(table: String): Int = manifest.counts[table] ?: 0
}

/**
 * Creates and restores `.kairos` backups. The database travels as a real SQLite file, so a backup
 * from an older version is upgraded by the same tested Room migrations as the app itself.
 *
 * Restore never overwrites silently: the current data is first saved as a private safety snapshot
 * (see [undoRestore]), then every table is replaced inside one transaction, so a failure part-way
 * leaves the current data untouched.
 */
class BackupManager(
    private val context: Context,
    private val database: KairosDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val afterRestore: suspend () -> Unit,
) {
    sealed class Failure(message: String) : Exception(message) {
        class NewerApp : Failure("newer_app")
        class Damaged : Failure("damaged")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val work get() = File(context.cacheDir, "backup").apply { mkdirs() }
    private val snapshotFile get() = File(File(context.filesDir, "safety").apply { mkdirs() }, "before-restore.kairosdb")

    /** Builds an encrypted backup of everything the person has, ready to write wherever they chose. */
    suspend fun create(passphrase: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val db = exportDatabase()
        val manifest = BackupManifest(
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = KairosDatabase.VERSION,
            createdAt = clock.millis(),
            counts = counts(database.openHelper.readableDatabase),
            settings = settings.exportForBackup(),
        )
        BackupCrypto.seal(passphrase, BackupCrypto.Contents(json.encodeToString(BackupManifest.serializer(), manifest), db))
    }

    suspend fun markBackedUp() = settings.setLastBackupAt(clock.millis())

    /** Decrypts and checks a backup without changing anything. Throws [BackupCrypto.OpenError] or [Failure]. */
    suspend fun open(passphrase: CharArray, file: ByteArray): BackupPreview = withContext(Dispatchers.Default) {
        val contents = BackupCrypto.open(passphrase, file)
        val manifest = runCatching { json.decodeFromString(BackupManifest.serializer(), contents.manifest) }.getOrElse { throw Failure.Damaged() }
        if (manifest.schemaVersion > KairosDatabase.VERSION || manifest.backupVersion > 1) throw Failure.NewerApp()
        withContext(Dispatchers.IO) { withUpgradedCopy(contents.database) { } }
        BackupPreview(manifest, contents.database)
    }

    /** Replaces all current data with [preview], after saving the current data so it can be undone. */
    suspend fun restore(preview: BackupPreview) = withContext(Dispatchers.IO) {
        val snapshot = exportDatabase()
        val tmp = File(snapshotFile.path + ".tmp")
        tmp.writeBytes(snapshot)
        try {
            withUpgradedCopy(preview.database) { source -> replaceAll(source) }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        if (!tmp.renameTo(snapshotFile)) { snapshotFile.delete(); tmp.renameTo(snapshotFile) }
        settings.importFromBackup(preview.manifest.settings)
        SafeLog.event("backup_restored", "schema" to preview.manifest.schemaVersion)
        afterRestore()
    }

    /** When the data was replaced by the last restore, or null if there is nothing to undo. */
    fun snapshotTakenAt(): Instant? = snapshotFile.takeIf { it.exists() }?.let { Instant.ofEpochMilli(it.lastModified()) }

    /** Puts back the data from just before the last restore, then forgets the snapshot. */
    suspend fun undoRestore() = withContext(Dispatchers.IO) {
        val bytes = snapshotFile.takeIf { it.exists() }?.readBytes() ?: return@withContext
        withUpgradedCopy(bytes) { source -> replaceAll(source) }
        snapshotFile.delete()
        SafeLog.event("backup_restore_undone")
        afterRestore()
    }

    // ---- Internals ----

    /** Copies the live data into a fresh database file of the current schema and returns its bytes. */
    private fun exportDatabase(): ByteArray {
        val file = File(work, "export-${System.nanoTime()}.db")
        try {
            val copy = Room.databaseBuilder(context, KairosDatabase::class.java, file.absolutePath)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
            try {
                val target = copy.openHelper.writableDatabase
                val live = database.openHelper.readableDatabase
                live.beginTransactionNonExclusive()
                try {
                    target.beginTransaction()
                    try {
                        target.execSQL("PRAGMA defer_foreign_keys = ON")
                        tables(live).forEach { t -> copyRows(live, t, columns(live, t), target) }
                        target.setTransactionSuccessful()
                    } finally {
                        target.endTransaction()
                    }
                } finally {
                    live.endTransaction()
                }
            } finally {
                copy.close()
            }
            return file.readBytes()
        } finally {
            cleanUp(file)
        }
    }

    /**
     * Writes [bytes] to a private temporary file, lets Room upgrade it with the app's own migrations
     * and checks its integrity, then hands [block] a read-only connection to it.
     */
    private fun <T> withUpgradedCopy(bytes: ByteArray, block: (SQLiteDatabase) -> T): T {
        val file = File(work, "restore-${System.nanoTime()}.db")
        try {
            file.writeBytes(bytes)
            val header = bytes.copyOfRange(0, minOf(16, bytes.size)).toString(Charsets.US_ASCII)
            if (!header.startsWith("SQLite format 3")) throw Failure.Damaged()
            val upgraded = Room.databaseBuilder(context, KairosDatabase::class.java, file.absolutePath)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(*KairosDatabase.MIGRATIONS)
                .build()
            try {
                val db = upgraded.openHelper.writableDatabase
                db.query("PRAGMA integrity_check").use { c -> if (!c.moveToFirst() || c.getString(0) != "ok") throw Failure.Damaged() }
                db.query("PRAGMA foreign_key_check").use { c -> if (c.moveToFirst()) throw Failure.Damaged() }
            } catch (e: Failure) {
                throw e
            } catch (e: Exception) {
                SafeLog.error("backup_upgrade_failed", e)
                throw Failure.Damaged()
            } finally {
                upgraded.close()
            }
            val source = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                return block(source)
            } finally {
                source.close()
            }
        } finally {
            cleanUp(file)
        }
    }

    /** Replaces every data table in the live database with the rows of [source], all or nothing. */
    private fun replaceAll(source: SQLiteDatabase) {
        val live = database.openHelper.writableDatabase
        val names = tables(live)
        database.runInTransaction {
            // Checked once at commit, so the order tables are refilled in does not matter.
            live.execSQL("PRAGMA defer_foreign_keys = ON")
            names.forEach { t -> live.execSQL("DELETE FROM `$t`") }
            names.forEach { t ->
                val cols = columns(live, t)
                source.rawQuery("SELECT ${cols.joinToString(",") { "`$it`" }} FROM `$t`", null).use { c -> insertAll(c, t, cols, live) }
            }
            live.query("PRAGMA foreign_key_check").use { c -> if (c.moveToFirst()) throw Failure.Damaged() }
        }
    }

    private fun copyRows(from: SupportSQLiteDatabase, table: String, cols: List<String>, to: SupportSQLiteDatabase) {
        from.query("SELECT ${cols.joinToString(",") { "`$it`" }} FROM `$table`").use { c -> insertAll(c, table, cols, to) }
    }

    private fun insertAll(c: Cursor, table: String, cols: List<String>, to: SupportSQLiteDatabase) {
        while (c.moveToNext()) {
            val v = ContentValues(cols.size)
            cols.forEachIndexed { i, name ->
                when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> v.putNull(name)
                    Cursor.FIELD_TYPE_INTEGER -> v.put(name, c.getLong(i))
                    Cursor.FIELD_TYPE_FLOAT -> v.put(name, c.getDouble(i))
                    Cursor.FIELD_TYPE_BLOB -> v.put(name, c.getBlob(i))
                    else -> v.put(name, c.getString(i))
                }
            }
            to.insert(table, SQLiteDatabase.CONFLICT_ABORT, v)
        }
    }

    /** Data tables only. Scheduled alarms are derived state and are rebuilt after a restore. */
    private fun tables(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }.filter { it !in EXCLUDED && !it.startsWith("sqlite_") }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            buildList { while (c.moveToNext()) add(c.getString(name)) }
        }

    private fun counts(db: SupportSQLiteDatabase): Map<String, Int> = COUNTED.associateWith { t ->
        runCatching {
            db.query("SELECT COUNT(*) FROM `$t` WHERE deletedAt IS NULL").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        }.getOrDefault(0)
    }

    private fun cleanUp(file: File) {
        listOf("", "-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
    }

    companion object {
        private val EXCLUDED = setOf("android_metadata", "room_master_table", "scheduled_notifications")
        val COUNTED = listOf("tasks", "trackers", "books", "journal_entries")
    }
}
