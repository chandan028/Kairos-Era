package com.kairosera

import android.content.Context
import com.kairosera.core.database.KairosDatabase
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.notifications.NotificationScheduler
import com.kairosera.core.settings.OnboardingRepository
import com.kairosera.core.settings.SettingsRepository
import com.kairosera.data.quotes.QuoteRepository
import com.kairosera.data.repository.RoomBookRepository
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
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> SafeLog.error("app_scope_failure", e) },
    )
    val clock: Clock = Clock.systemUTC()

    val database: KairosDatabase by lazy { KairosDatabase.build(appContext) }
    val tasks: RoomTaskRepository by lazy { RoomTaskRepository(database) }
    val trackers: RoomTrackerRepository by lazy { RoomTrackerRepository(database) }
    val study: RoomStudyRepository by lazy { RoomStudyRepository(database) }
    val books: RoomBookRepository by lazy { RoomBookRepository(database) }
    val settings = SettingsRepository(appContext)
    val onboarding = OnboardingRepository(appContext)
    val quotes = QuoteRepository(appContext)

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
        runCatching { SampleData.insertExamples(appContext, tasks, LocalDate.now(), clock) }
            .onFailure { SafeLog.error("samples_failed", it) }
        runCatching { SampleData.insertTrackerExamples(appContext, trackers, study, books, LocalDate.now(), clock) }
            .onFailure { SafeLog.error("tracker_samples_failed", it) }
    }

    /** Moves every example (tasks, trackers, books) to Trash, where it can still be restored. */
    suspend fun trashSampleContent() {
        val now = java.time.Instant.now(clock)
        tasks.trashSampleData(now)
        trackers.trashSamples(now)
        books.trashSamples(now)
        scheduler.rebuild("samples_removed")
    }
}
