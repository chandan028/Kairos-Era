package com.kairosera

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairosera.core.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeleteEverythingTest {
    @Test
    fun removesAllDataAndSettingsAndReturnsToFirstRun(): Unit = runBlocking {
        val c = ApplicationProvider.getApplicationContext<KairosApp>().container
        c.addSampleContent()
        c.settings.completeOnboarding("Chan", setOf("LEARN"))
        c.settings.setThemeMode(ThemeMode.DARK)
        val preview = c.backup.open("passphrase!".toCharArray(), c.backup.create("passphrase!".toCharArray()))
        c.backup.restore(preview)
        assertTrue(c.backup.snapshotTakenAt() != null)

        c.deleteEverything()

        val db = c.database.openHelper.readableDatabase
        listOf("tasks", "trackers", "tracker_values", "books", "reading_sessions", "journal_entries", "scheduled_notifications").forEach { t ->
            assertEquals(t, 0, db.query("SELECT COUNT(*) FROM $t").use { it.moveToFirst(); it.getInt(0) })
        }
        // Default categories are seeded again so the app works straight away.
        assertTrue(db.query("SELECT COUNT(*) FROM categories").use { it.moveToFirst(); it.getInt(0) } > 0)
        val s = c.settings.settings.first()
        assertFalse(s.onboardingDone)
        assertEquals("", s.name)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertNull(c.backup.snapshotTakenAt())
    }
}
