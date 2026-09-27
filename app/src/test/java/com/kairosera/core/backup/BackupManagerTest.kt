package com.kairosera.core.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairosera.AppContainer
import com.kairosera.KairosApp
import com.kairosera.core.database.KairosDatabase
import com.kairosera.core.settings.ThemeMode
import com.kairosera.domain.journal.JournalEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private lateinit var c: AppContainer
    private val pass get() = "a long enough passphrase".toCharArray()

    @Before
    fun setUp() {
        c = ApplicationProvider.getApplicationContext<KairosApp>().container
    }

    private fun counts(): Map<String, Int> {
        val db = c.database.openHelper.readableDatabase
        return listOf("tasks", "subtasks", "reminders", "trackers", "tracker_values", "books", "reading_sessions", "journal_entries").associateWith { t ->
            db.query("SELECT COUNT(*) FROM $t").use { it.moveToFirst(); it.getInt(0) }
        }
    }

    private fun journalTexts(): List<String> = runBlocking {
        c.journal.observeBetween(LocalDate.now().minusDays(400), LocalDate.now()).first().map { it.text }.sorted()
    }

    @Test
    fun backupRestoreAndUndoAreExact(): Unit = runBlocking {
        c.addSampleContent()
        c.journal.save(JournalEntry(date = LocalDate.now(), text = "the original day", createdAt = Instant.now(), updatedAt = Instant.now()))
        c.settings.setThemeMode(ThemeMode.DARK)
        val original = counts()
        val originalJournal = journalTexts()
        assertTrue(original.getValue("tasks") > 0)

        val file = c.backup.create(pass)

        // Change things after the backup.
        c.trashSampleContent()
        c.database.openHelper.writableDatabase.execSQL("DELETE FROM tracker_values")
        c.journal.save(JournalEntry(date = LocalDate.now().minusDays(1), text = "written after the backup", createdAt = Instant.now(), updatedAt = Instant.now()))
        c.settings.setThemeMode(ThemeMode.LIGHT)
        val changed = counts()
        val changedJournal = journalTexts()

        assertThrows(BackupCrypto.OpenError.CannotDecrypt::class.java) { runBlocking { c.backup.open("not the passphrase".toCharArray(), file) } }

        val preview = c.backup.open(pass, file)
        assertEquals(KairosDatabase.VERSION, preview.manifest.schemaVersion)
        assertTrue(preview.count("tasks") > 0)
        assertNull(c.backup.snapshotTakenAt())

        c.backup.restore(preview)
        assertEquals(original, counts())
        assertEquals(originalJournal, journalTexts())
        assertEquals(ThemeMode.DARK, c.settings.settings.first().themeMode)
        assertNotNull(c.backup.snapshotTakenAt())

        c.backup.undoRestore()
        assertEquals(changed, counts())
        assertEquals(changedJournal, journalTexts())
        assertNull(c.backup.snapshotTakenAt())
    }

    @Test
    fun aBackupFromAnOlderVersionIsUpgradedOnRestore(): Unit = runBlocking {
        val v2 = File(ApplicationProvider.getApplicationContext<KairosApp>().cacheDir, "v2.db").also { it.delete() }
        val schema = listOf("schemas", "app/schemas").map { File(it, "com.kairosera.core.database.KairosDatabase/2.json") }.first { it.exists() }
        val db = JSONObject(schema.readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(v2, null).use { sql ->
            val entities = db.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = db.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
            sql.version = 2
            sql.execSQL("INSERT INTO categories (id, name, colorArgb, icon, position) VALUES (900, 'Old', 0, 'work', 0)")
            sql.execSQL(
                "INSERT INTO tasks (id, title, description, notes, date, priority, categoryId, repeatInterval, repeatWeekdays, createdAt, updatedAt, isSample) " +
                    "VALUES (901, 'From version two', '', '', 20000, 'NONE', 900, 1, 0, 0, 0, 0)",
            )
        }
        val manifest = BackupManifest(appVersion = "0.2.0", schemaVersion = 2, createdAt = 0, counts = mapOf("tasks" to 1))
        val file = BackupCrypto.seal(pass, BackupCrypto.Contents(Json.encodeToString(BackupManifest.serializer(), manifest), v2.readBytes()), iterations = 100_000)

        val preview = c.backup.open(pass, file)
        c.backup.restore(preview)
        val titles = c.database.openHelper.readableDatabase.query("SELECT title FROM tasks").use { cur ->
            buildList { while (cur.moveToNext()) add(cur.getString(0)) }
        }
        assertEquals(listOf("From version two"), titles)
        // The journal table added in version 3 exists and is empty.
        assertEquals(0, counts().getValue("journal_entries"))
    }

    @Test
    fun aBackupFromANewerAppIsRefusedWithoutChangingAnything(): Unit = runBlocking {
        c.addSampleContent()
        val before = counts()
        val manifest = BackupManifest(appVersion = "9.0.0", schemaVersion = KairosDatabase.VERSION + 1, createdAt = 0)
        val file = BackupCrypto.seal(pass, BackupCrypto.Contents(Json.encodeToString(BackupManifest.serializer(), manifest), ByteArray(10)), iterations = 100_000)
        assertThrows(BackupManager.Failure.NewerApp::class.java) { runBlocking { c.backup.open(pass, file) } }
        assertEquals(before, counts())
    }

    @Test
    fun aDamagedDatabaseIsRefused(): Unit = runBlocking {
        val manifest = BackupManifest(appVersion = "0.6.0", schemaVersion = KairosDatabase.VERSION, createdAt = 0)
        val file = BackupCrypto.seal(pass, BackupCrypto.Contents(Json.encodeToString(BackupManifest.serializer(), manifest), "not a database".toByteArray()), iterations = 100_000)
        assertThrows(BackupManager.Failure.Damaged::class.java) { runBlocking { c.backup.open(pass, file) } }
    }
}
