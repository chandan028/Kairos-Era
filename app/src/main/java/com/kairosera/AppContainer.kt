package com.kairosera

import android.content.Context
import com.kairosera.core.database.KairosDatabase
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.notifications.NotificationScheduler
import com.kairosera.core.settings.OnboardingRepository
import com.kairosera.core.settings.SettingsRepository
import com.kairosera.data.quotes.QuoteRepository
import com.kairosera.data.repository.RoomBookRepository
import com.kairosera.data.repository.RoomJournalRepository
import com.kairosera.data.repository.RoomNotificationRepository
import com.kairosera.data.repository.RoomStudyRepository
import com.kairosera.data.repository.RoomTaskRepository
import com.kairosera.data.repository.RoomTrackerRepository
import com.kairosera.data.sample.SampleData
import com.kairosera.domain.usecase.MoveTaskToTrash
import com.kairosera.domain.usecase.ObserveDayPlan
import com.kairosera.domain.usecase.RescheduleOccurrence
import com.kairosera.domain.usecase.RestoreTask
import com.kairosera.domain.usecase.SaveTask
import com.kairosera.domain.usecase.SetOccurrenceDone
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/** Manual dependency wiring. Small enough that a DI framework would add more than it saves. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> SafeLog.error("app_scope_failure", e) },
    )
    val clock: Clock = Clock.systemUTC()

    val database: KairosDatabase by lazy { KairosDatabase.build(appContext) }
    val tasks: RoomTaskRepository by lazy { RoomTaskRepository(database) }
    val trackers: RoomTrackerRepository by lazy { RoomTrackerRepository(database) }
    val study: RoomStudyRepository by lazy { RoomStudyRepository(database) }
    val books: RoomBookRepository by lazy { RoomBookRepository(database) }
    val journal: RoomJournalRepository by lazy { RoomJournalRepository(database) }
    val settings = SettingsRepository(appContext)
    val onboarding = OnboardingRepository(appContext)
    val quotes = QuoteRepository(appContext)

    /** The day Kairos Era arrived on this phone. Statistics never count days before it against the person. */
    val installedOn: LocalDate by lazy {
        runCatching {
            val t = appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime
            java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        }.getOrElse { LocalDate.now() }
    }

    val scheduler: NotificationScheduler by lazy {
        NotificationScheduler(
            context = appContext,
            tasks = tasks,
            rows = RoomNotificationRepository(database.notificationDao()),
            clock = clock,
            snoozeMinutes = { settings.settings.first().snoozeMinutes },
        )
    }

    private val remindersChanged: suspend () -> Unit = { scheduler.rebuild("data_changed") }

    val backup by lazy { com.kairosera.core.backup.BackupManager(appContext, database, settings, clock) { scheduler.rebuild("restored") } }
    val lock = com.kairosera.core.security.AppLock()

    val observeDayPlan by lazy { ObserveDayPlan(tasks) }
    val saveTask by lazy { SaveTask(tasks, clock, remindersChanged) }
    val setOccurrenceDone by lazy { SetOccurrenceDone(tasks, clock, remindersChanged) }
    val moveToTrash by lazy { MoveTaskToTrash(tasks, clock, remindersChanged) }
    val restoreTask by lazy { RestoreTask(tasks, clock, remindersChanged) }
    val reschedule by lazy { RescheduleOccurrence(tasks, clock, remindersChanged) }

    suspend fun seedDefaults() {
        runCatching { SampleData.ensureCategories(appContext, database) }
            .onFailure { SafeLog.error("seed_failed", it) }
    }

    suspend fun addSampleContent() {
        // Examples use the default categories, which startup seeds in the background; make sure they exist first.
        seedDefaults()
        runCatching { SampleData.insertExamples(appContext, tasks, LocalDate.now(), clock) }
            .onFailure { SafeLog.error("samples_failed", it) }
        runCatching { SampleData.insertTrackerExamples(appContext, trackers, study, books, LocalDate.now(), clock) }
            .onFailure { SafeLog.error("tracker_samples_failed", it) }
        runCatching { SampleData.insertJournalExamples(appContext, journal, LocalDate.now(), clock) }
            .onFailure { SafeLog.error("journal_samples_failed", it) }
    }

    /**
     * Permanently deletes everything: all data, settings, the restore snapshot and any crash report.
     * Only reachable after two confirmations in Settings. Alarms are cancelled first by rebuilding
     * the reminder schedule against the now-empty database.
     */
    suspend fun deleteEverything() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }.filter { it !in setOf("android_metadata", "room_master_table", "scheduled_notifications") && !it.startsWith("sqlite_") }
        database.runInTransaction {
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            tables.forEach { db.execSQL("DELETE FROM `$it`") }
        }
        scheduler.rebuild("delete_everything")
        db.execSQL("DELETE FROM scheduled_notifications")
        java.io.File(appContext.filesDir, "safety").deleteRecursively()
        appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        com.kairosera.core.diagnostics.CrashReports.clear(appContext)
        onboarding.clear()
        settings.clearAll()
        lock.unlock()
        seedDefaults()
        SafeLog.event("delete_everything")
    }

    /** Moves every example (tasks, trackers, books, journal entries) to Trash, where it can still be restored. */
    suspend fun trashSampleContent() {
        val now = java.time.Instant.now(clock)
        tasks.trashSampleData(now)
        trackers.trashSamples(now)
        books.trashSamples(now)
        journal.trashSamples(now)
        scheduler.rebuild("samples_removed")
    }
}
