package com.kairosera.core.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kairosera.KairosApp
import com.kairosera.core.diagnostics.SafeLog
import kotlinx.coroutines.launch

/** Receives our own alarms and notification actions. Not exported: only our PendingIntents reach it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val action = intent.action
        if (id <= 0 || action !in ACTIONS) return
        val container = (context.applicationContext as KairosApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val scheduler = container.scheduler
                when (action) {
                    ACTION_FIRE -> scheduler.onAlarm(id)
                    ACTION_SNOOZE -> scheduler.onSnooze(id)
                    ACTION_DISMISSED -> scheduler.onDismissed(id)
                    ACTION_DONE -> scheduler.onDone(id)?.let { (taskId, date) -> container.setOccurrenceDone(taskId, date, true) }
                }
            } catch (e: Exception) {
                SafeLog.error("reminder_action_failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.kairosera.action.REMINDER_FIRE"
        const val ACTION_DONE = "com.kairosera.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.kairosera.action.REMINDER_SNOOZE"
        const val ACTION_DISMISSED = "com.kairosera.action.REMINDER_DISMISSED"
        const val EXTRA_ID = "id"
        private val ACTIONS = setOf(ACTION_FIRE, ACTION_DONE, ACTION_SNOOZE, ACTION_DISMISSED)
    }
}

/**
 * System events after which the alarm schedule must be rebuilt from the database: reboot,
 * app update, wall-clock or time-zone change, and exact-alarm permission changes.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val container = (context.applicationContext as KairosApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (action == Intent.ACTION_LOCALE_CHANGED) NotificationChannels.ensure(context)
                container.scheduler.rebuild(action.substringAfterLast('.'))
                // A new day, time zone or language changes what every widget should say.
                com.kairosera.feature.widgets.Widgets.update(context, container)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
