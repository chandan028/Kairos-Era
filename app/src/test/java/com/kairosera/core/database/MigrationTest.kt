package com.kairosera.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairosera.data.repository.RoomBookRepository
import com.kairosera.data.repository.RoomJournalRepository
import com.kairosera.domain.journal.JournalEntry
import com.kairosera.domain.journal.Mood
import com.kairosera.data.repository.RoomStudyRepository
import com.kairosera.data.repository.RoomTrackerRepository
import com.kairosera.domain.reading.Book
import com.kairosera.domain.tracker.FieldValue
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.StudyTarget
import com.kairosera.domain.tracker.StudyTopic
import com.kairosera.domain.tracker.TopicStatus
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerEntry
import com.kairosera.domain.tracker.TrackerField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Builds a real version-1 database from the exported schema (app/schemas/.../1.json), fills it with
 * phase 1 data, then lets Room open it at the current version. Room runs the migrations and then
 * validates every table, column, index and foreign key against the compiled schema, so a
 * migration that drifts from the entities fails here instead of on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var room: KairosDatabase? = null

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    private fun createV1(fill: SQLiteDatabase.() -> Unit = {}) = createAt(1, fill)

    private fun createAt(version: Int, fill: SQLiteDatabase.() -> Unit = {}) {
        val schema = listOf("schemas", "app/schemas").map { File(it, "com.kairosera.core.database.KairosDatabase/$version.json") }.first { it.exists() }
        val db = JSONObject(schema.readText()).getJSONObject("database")
        context.deleteDatabase(dbName)
        val file = context.getDatabasePath(dbName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sql ->
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
            sql.version = version
            sql.fill()
        }
    }

    private fun openCurrent(): KairosDatabase =
        Room.databaseBuilder(context, KairosDatabase::class.java, dbName)
            .addMigrations(*KairosDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { room = it }

    @Test
    fun v1ToV2KeepsTasksAndAddsTrackerTables() {
        createV1 {
            execSQL("INSERT INTO categories (id, name, colorArgb, icon, position) VALUES (1, 'Study', 4280000000, 'school', 0)")
            execSQL(
                """INSERT INTO tasks (id, title, description, notes, date, startMinute, endMinute, priority, categoryId,
                repeatFrequency, repeatInterval, repeatWeekdays, repeatEndDate, repeatCount, seriesEnd, createdAt, updatedAt,
                completedAt, archivedAt, deletedAt, isSample)
                VALUES (7, 'Revise Java', '', '', 20000, 540, 600, 'HIGH', 1, 'DAILY', 1, 0, NULL, NULL, NULL, 1, 1, NULL, NULL, NULL, 0)""",
            )
            execSQL("INSERT INTO reminders (id, taskId, kind, minuteOfDay, dayOffset, minutesBefore, persistent, sound) VALUES (3, 7, 'AT_TIME', 540, 0, NULL, 1, 'KAIROS_BELL')")
            execSQL("INSERT INTO occurrence_states (taskId, date, status, changedAt) VALUES (7, 20000, 'DONE', 5)")
        }

        val db = openCurrent().openHelper.writableDatabase
        assertEquals(KairosDatabase.VERSION, db.version)
        db.query("SELECT title, priority FROM tasks WHERE id = 7").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Revise Java", c.getString(0))
            assertEquals("HIGH", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM reminders").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM occurrence_states").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM trackers").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }

    @Test
    fun migratedDatabaseWorksWithRepositories() = runBlocking {
        createV1()
        val room = openCurrent()
        val trackers = RoomTrackerRepository(room)
        val study = RoomStudyRepository(room)
        val books = RoomBookRepository(room)
        val now = Instant.parse("2026-09-23T10:00:00Z")
        val today = LocalDate.of(2026, 9, 23)

        val id = trackers.save(
            Tracker(
                name = "Fitness",
                fields = listOf(
                    TrackerField(label = "Steps", type = MeasurementType.NUMBER, target = 8000.0),
                    TrackerField(label = "Stretch", type = MeasurementType.CHECKBOX),
                ),
                createdAt = now, updatedAt = now,
            ),
        )
        val saved = trackers.get(id)!!
        val steps = saved.fields[0].id
        trackers.saveEntry(TrackerEntry(id, today, mapOf(steps to FieldValue(steps, number = 5000.0)), now))
        trackers.saveEntry(TrackerEntry(id, today, mapOf(steps to FieldValue(steps, number = 9000.0)), now))
        val entries = trackers.observeEntries(today, today).first()
        assertEquals(1, entries.size)
        assertEquals(9000.0, entries[0].values[steps]!!.number!!, 0.0)

        // Removing a field keeps its history: it is disabled, not deleted.
        trackers.save(saved.copy(fields = saved.fields.take(1)))
        val edited = trackers.get(id)!!
        assertEquals(2, edited.fields.size)
        assertFalse(edited.fields[1].enabled)

        // Trash is reversible, and permanent delete only works from Trash.
        trackers.deletePermanently(id)
        assertEquals(1, trackers.observeActive().first().size)
        trackers.moveToTrash(id, now)
        assertTrue(trackers.observeActive().first().isEmpty())
        trackers.restore(id, now)
        assertEquals(1, trackers.observeActive().first().size)

        // Completing a study target updates its topic but never marks it done.
        val topic = study.saveTopic(StudyTopic(trackerId = id, title = "HashMap internals"))
        val target = study.saveTarget(StudyTarget(trackerId = id, date = today, topicId = topic, title = "Read chapter"))
        study.setTargetDone(target, true, now)
        val t = study.observeTopics(id).first().single()
        assertEquals(TopicStatus.LEARNING, t.status)
        assertEquals(1, t.practiceDone)

        // A reading session moves the bookmark and never past the last page.
        val bookId = books.save(Book(title = "Deep Work", totalPages = 100, currentPage = 90, createdAt = now, updatedAt = now))
        books.logSession(bookId, pages = 30, minutes = 20, date = today, at = now)
        assertEquals(100, books.get(bookId)!!.currentPage)
        val session = books.observeSessions(bookId).first().single()
        assertEquals(10, session.pages)
        books.deleteSession(session.id)
        assertEquals(90, books.get(bookId)!!.currentPage)
        books.moveToTrash(bookId, now)
        assertTrue(books.observeBooks().first().isEmpty())
        assertNull(books.observeBooks().first().firstOrNull())
    }

    @Test
    fun v2ToV3AddsTheJournalAndKeepsTrackers() = runBlocking {
        createAt(2) {
            execSQL("INSERT INTO trackers (id, name, icon, colorArgb, description, why, gain, template, frequency, frequencyDays, frequencyTimes, position, createdAt, updatedAt, archivedAt, deletedAt, isSample) VALUES (4, 'Reading', 'b', 1, '', '', '', 'READING', 'DAILY', 0, 0, 0, 1, 1, NULL, NULL, 0)")
        }
        val room = openCurrent()
        val db = room.openHelper.writableDatabase
        assertEquals(KairosDatabase.VERSION, db.version)
        db.query("SELECT name FROM trackers WHERE id = 4").use { c -> assertTrue(c.moveToFirst()); assertEquals("Reading", c.getString(0)) }

        val journal = RoomJournalRepository(room)
        val day = LocalDate.of(2026, 9, 24)
        val now = Instant.parse("2026-09-24T20:00:00Z")
        val id = journal.save(JournalEntry(date = day, mood = Mood.GOOD, text = "First", createdAt = now, updatedAt = now))
        // A second save for the same day continues that day's entry rather than adding another.
        journal.save(JournalEntry(date = day, text = "Second", createdAt = now, updatedAt = now))
        val all = journal.observeEntries().first()
        assertEquals(1, all.size)
        assertEquals(id, all.single().id)
        assertEquals("Second", all.single().text)
        assertEquals(now, all.single().createdAt)

        journal.moveToTrash(id, now)
        assertTrue(journal.observeEntries().first().isEmpty())
        assertEquals(1, journal.observeTrash().first().size)
        journal.restore(id, now)
        assertEquals(1, journal.observeBetween(day, day).first().size)
    }
}
