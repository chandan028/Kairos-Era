package com.kairosera.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kairosera.MainActivity
import com.kairosera.R
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.domain.model.NotificationStatus
import com.kairosera.domain.model.Reminder
import com.kairosera.domain.model.ScheduledNotification
import com.kairosera.domain.model.Task
import com.kairosera.domain.repository.NotificationRepository
import com.kairosera.domain.repository.TaskRepository
import com.kairosera.domain.time.ReminderPlanner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The single owner of reminder alarms and notifications: schedule, reschedule, cancel,
 * rebuild, deduplicate and recover. Every entry point converges on [rebuild], which asks the
 * pure [ReminderPlanner] what should exist and applies the difference. Because each
 * (reminder, occurrence) maps to one database row whose id is the alarm request code and the
 * notification id, running it any number of times never creates duplicates.
 */
class NotificationScheduler(
    private val context: Context,
    private val tasks: TaskRepository,
    private val rows: NotificationRepository,
    private val clock: Clock,
    private val snoozeMinutes: suspend () -> Int,
) {
    private val mutex = Mutex()
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val nm get() = NotificationManagerCompat.from(context)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    fun canPostNotifications(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && nm.areNotificationsEnabled()
    }

    suspend fun rebuild(reason: String) {
        runCatching {
            mutex.withLock {
                var passes = 0
                while (passes++ < MAX_PASSES && applyPlanOnce()) Unit
            }
        }.onFailure { SafeLog.error("reminder_rebuild_failed", it, "reason" to reason) }
            .onSuccess { SafeLog.event("reminder_rebuild", "reason" to reason) }
    }

    /** Applies one plan. Returns true if it resolved stale rows and another pass is needed. */
    private suspend fun applyPlanOnce(): Boolean {
        val now = Instant.now(clock)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(clock.withZone(zone))
        val withReminders = tasks.getTasksWithReminders()
        val states = tasks.getOccurrenceStates(today.minusDays(HISTORY_DAYS), today.plusDays(FUTURE_DAYS))
            .groupBy { it.taskId }
            .mapValues { (_, list) -> list.associate { it.date to it.status } }
        val existing = rows.getSince(today.minusDays(HISTORY_DAYS))
        val plan = ReminderPlanner.plan(ReminderPlanner.Input(withReminders, states, existing, now, zone))
        val tasksById = withReminders.associateBy { it.id }

        for (row in plan.cancel) {
            cancelAlarm(row.id)
            nm.cancel(row.id.toInt())
            rows.setStatus(row.id, NotificationStatus.CANCELLED, now)
        }
        var stale = false
        for (arm in plan.arm) {
            // A brand-new reminder whose time had already passed when the user saved it is recorded
            // as handled instead of firing at once; the next occurrence is armed on the next pass.
            val task = tasksById[arm.taskId]
            val createdLate = arm.id == null && task != null && arm.triggerAt < now.minus(LATE_TOLERANCE) &&
                task.updatedAt > arm.triggerAt
            val id = rows.upsert(
                ScheduledNotification(
                    id = arm.id ?: 0,
                    reminderId = arm.reminderId,
                    taskId = arm.taskId,
                    occurrenceDate = arm.occurrenceDate,
                    triggerAt = arm.triggerAt,
                    status = if (createdLate) NotificationStatus.DISMISSED else arm.status,
                    updatedAt = now,
                ),
            )
            if (createdLate) stale = true else setAlarm(id, arm.triggerAt)
        }
        val posted = activeIds()
        for (row in plan.ensureVisible) {
            if (row.id.toInt() in posted) continue
            val task = tasksById[row.taskId] ?: continue
            val reminder = task.reminders.firstOrNull { it.id == row.reminderId } ?: continue
            post(row, task, reminder, alertAgain = false)
        }
        return stale
    }

    /** Called when an alarm fires. Validates everything again: the task may have changed since. */
    suspend fun onAlarm(id: Long) {
        runCatching {
            mutex.withLock {
                val row = rows.get(id) ?: return@withLock
                if (!row.isArmed) return@withLock
                val now = Instant.now(clock)
                val task = tasks.getTask(row.taskId)
                val reminder = task?.reminders?.firstOrNull { it.id == row.reminderId }
                val state = task?.let { tasks.getOccurrenceStates(row.occurrenceDate, row.occurrenceDate).firstOrNull { s -> s.taskId == it.id } }
                if (task == null || !task.isActive || reminder == null || state != null) {
                    rows.setStatus(id, NotificationStatus.CANCELLED, now)
                    return@withLock
                }
                post(row, task, reminder, alertAgain = true)
                rows.setStatus(id, NotificationStatus.SHOWING, now)
            }
        }.onFailure { SafeLog.error("reminder_fire_failed", it) }
        // Arm the next occurrence of a recurring reminder.
        rebuild("alarm")
    }

    suspend fun onSnooze(id: Long) {
        val minutes = snoozeMinutes()
        mutex.withLock {
            val row = rows.get(id) ?: return@withLock
            val now = Instant.now(clock)
            val at = now.plus(Duration.ofMinutes(minutes.toLong()))
            rows.setStatus(id, NotificationStatus.SNOOZED, now, triggerAt = at)
            nm.cancel(row.id.toInt())
            setAlarm(id, at)
        }
    }

    suspend fun onDismissed(id: Long) {
        mutex.withLock {
            val row = rows.get(id) ?: return@withLock
            if (row.status == NotificationStatus.SHOWING) rows.setStatus(id, NotificationStatus.DISMISSED, Instant.now(clock))
        }
    }

    /** Marks the row DONE and returns the (task, date) whose occurrence the caller should complete. */
    suspend fun onDone(id: Long): Pair<Long, LocalDate>? = mutex.withLock {
        val row = rows.get(id) ?: return@withLock null
        rows.setStatus(id, NotificationStatus.DONE, Instant.now(clock))
        nm.cancel(row.id.toInt())
        row.taskId to row.occurrenceDate
    }

    suspend fun rowFor(id: Long): ScheduledNotification? = rows.get(id)

    fun postTest() {
        if (!canPostNotifications()) return
        val n = NotificationCompat.Builder(context, NotificationChannels.channelFor(com.kairosera.domain.model.ReminderSound.KAIROS_BELL))
            .setSmallIcon(R.drawable.ic_stat_kairos)
            .setContentTitle(context.getString(R.string.test_notification_title))
            .setContentText(context.getString(R.string.test_notification_text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(TEST_ID.toLong(), null, null))
            .build()
        notifySafely(TEST_ID, n)
    }

    private fun post(row: ScheduledNotification, task: Task, reminder: Reminder, alertAgain: Boolean) {
        if (!canPostNotifications()) return
        val zone = ZoneId.systemDefault()
        val timeText = task.startTime?.let { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(it) }
        val dateText = if (row.occurrenceDate != LocalDate.now(clock.withZone(zone))) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(row.occurrenceDate)
        } else {
            null
        }
        val body = listOfNotNull(dateText, timeText).joinToString(" · ").ifEmpty { context.getString(R.string.reminder_all_day) }
        val public = NotificationCompat.Builder(context, NotificationChannels.channelFor(reminder.sound))
            .setSmallIcon(R.drawable.ic_stat_kairos)
            .setContentTitle(context.getString(R.string.reminder_public_title))
            .build()
        val builder = NotificationCompat.Builder(context, NotificationChannels.channelFor(reminder.sound))
            .setSmallIcon(R.drawable.ic_stat_kairos)
            .setColor(ContextCompat.getColor(context, R.color.brand_sun))
            .setSubText(context.getString(R.string.reminder_label))
            .setContentTitle(task.title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setOnlyAlertOnce(!alertAgain)
            .setWhen(row.triggerAt.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(openAppIntent(row.id, task.id, row.occurrenceDate))
            .setDeleteIntent(receiverIntent(ReminderReceiver.ACTION_DISMISSED, row.id))
            .addAction(0, context.getString(R.string.action_done), receiverIntent(ReminderReceiver.ACTION_DONE, row.id))
            .addAction(0, context.getString(R.string.action_snooze), receiverIntent(ReminderReceiver.ACTION_SNOOZE, row.id))
            .addAction(0, context.getString(R.string.action_reschedule), rescheduleIntent(row.id, task.id, row.occurrenceDate))
        if (reminder.persistent) {
            // Kept until the user acts. Android 14+ still lets users swipe it away, which we respect.
            builder.setOngoing(true).setAutoCancel(false)
        } else {
            builder.setAutoCancel(true)
        }
        notifySafely(row.id.toInt(), builder.build())
    }

    private fun notifySafely(id: Int, notification: Notification) {
        try {
            nm.notify(id, notification)
        } catch (e: SecurityException) {
            SafeLog.error("notify_denied", e)
        }
    }

    private fun activeIds(): Set<Int> = runCatching {
        context.getSystemService(NotificationManager::class.java)?.activeNotifications?.map { it.id }?.toSet()
    }.getOrNull().orEmpty()

    private fun setAlarm(id: Long, at: Instant) {
        val am = alarmManager ?: return
        val pi = receiverIntent(ReminderReceiver.ACTION_FIRE, id)
        try {
            if (canScheduleExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
            } else {
                // Without the exact-alarm permission Android may deliver a few minutes late. Settings explains this.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
            }
        } catch (e: SecurityException) {
            SafeLog.error("alarm_denied", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        }
    }

    private fun cancelAlarm(id: Long) {
        alarmManager?.cancel(receiverIntent(ReminderReceiver.ACTION_FIRE, id))
    }

    private fun receiverIntent(action: String, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(ReminderReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, requestCode(action, id), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openAppIntent(id: Long, taskId: Long?, date: LocalDate?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction(MainActivity.ACTION_OPEN_DAY)
        date?.let { intent.putExtra(MainActivity.EXTRA_DATE, it.toEpochDay()) }
        taskId?.let { intent.putExtra(MainActivity.EXTRA_TASK_ID, it) }
        return PendingIntent.getActivity(context, requestCode("open", id), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun rescheduleIntent(id: Long, taskId: Long, date: LocalDate): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction(MainActivity.ACTION_RESCHEDULE)
            .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            .putExtra(MainActivity.EXTRA_DATE, date.toEpochDay())
            .putExtra(MainActivity.EXTRA_NOTIFICATION_ID, id)
        return PendingIntent.getActivity(context, requestCode("reschedule", id), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Distinct request codes per action so PendingIntents for one row never replace each other. */
    private fun requestCode(action: String, id: Long): Int {
        val slot = when (action) {
            ReminderReceiver.ACTION_FIRE -> 0
            ReminderReceiver.ACTION_DONE -> 1
            ReminderReceiver.ACTION_SNOOZE -> 2
            ReminderReceiver.ACTION_DISMISSED -> 3
            "open" -> 4
            else -> 5
        }
        return id.toInt() * 8 + slot
    }

    companion object {
        private const val MAX_PASSES = 3
        private const val HISTORY_DAYS = 10L
        private const val FUTURE_DAYS = 3660L
        private val LATE_TOLERANCE: Duration = Duration.ofMinutes(1)
        private const val TEST_ID = Int.MAX_VALUE - 1
    }
}
