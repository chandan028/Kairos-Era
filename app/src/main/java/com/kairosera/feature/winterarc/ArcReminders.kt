package com.kairosera.feature.winterarc

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kairosera.KairosApp
import com.kairosera.MainActivity
import com.kairosera.R
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.settings.ArcReminder
import com.kairosera.core.settings.ReminderConfig
import com.kairosera.core.settings.WinterArcPrefs
import com.kairosera.domain.winterarc.DayInputs
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.HabitRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Winter Arc reminders: at most one a day per kind, at the time the person chose, only on their
 * chosen weekdays, and skipped entirely when that habit is already done. Inexact alarms (a few
 * minutes' window) are enough here and need no special permission.
 */
class ArcReminders(private val context: Context, private val prefs: WinterArcPrefs) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    fun habitFor(r: ArcReminder): HabitKind? = when (r) {
        ArcReminder.WATER -> HabitKind.WATER
        ArcReminder.READING -> HabitKind.READING
        ArcReminder.STUDY -> HabitKind.STUDY
        ArcReminder.COLD_SHOWER -> HabitKind.COLD_SHOWER
        ArcReminder.DEEP_WORK -> HabitKind.DEEP_WORK
        ArcReminder.WAKE_PREP -> null
        ArcReminder.SPEAKING -> HabitKind.SPEAK
    }

    /** Re-plans every reminder from the saved settings. Safe to call any time. */
    suspend fun rebuild() {
        val s = prefs.current()
        val arcOn = s.enabled && runCatching { appContainer().winterArc.currentArc() }.getOrNull()?.let { !it.isPaused } == true
        ArcReminder.entries.forEach { r ->
            val cfg = s.reminders.getValue(r)
            if (arcOn && cfg.enabled && cfg.days.isNotEmpty()) schedule(r, cfg) else cancel(r)
        }
    }

    fun cancelAll() = ArcReminder.entries.forEach(::cancel)

    private fun appContainer() = (context.applicationContext as KairosApp).container

    private fun schedule(r: ArcReminder, cfg: ReminderConfig) {
        val at = nextTime(cfg, LocalDateTime.now()) ?: return cancel(r)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { alarms?.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MS, pending(r)) }
            .onFailure { SafeLog.error("arc_alarm_failed", it) }
    }

    private fun cancel(r: ArcReminder) { alarms?.cancel(pending(r)) }

    private fun pending(r: ArcReminder): PendingIntent {
        val intent = Intent(context, ArcReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_KIND, r.name)
        return PendingIntent.getBroadcast(context, r.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Called when an alarm goes off: post (unless already done), then plan tomorrow's. */
    suspend fun fire(r: ArcReminder) {
        val s = prefs.current()
        val cfg = s.reminders.getValue(r)
        if (s.enabled && cfg.enabled) {
            runCatching { maybePost(r) }.onFailure { SafeLog.error("arc_notify_failed", it) }
        }
        rebuild()
    }

    private suspend fun maybePost(r: ArcReminder) {
        val c = appContainer()
        val arc = c.winterArc.currentArc() ?: return
        val today = LocalDate.now()
        if (arc.isPaused || !arc.counts(today, today)) return
        val habits = c.winterArc.habits.first()
        val inputs = c.winterArc.observeDays(today, today).first()[today] ?: DayInputs(today)
        val kind = habitFor(r)
        if (kind != null) {
            val habit = habits.firstOrNull { it.kind == kind } ?: return
            if (!habit.active) return
            if (HabitRules.evaluate(habit, inputs).done) return
        } else if (habits.none { it.kind == HabitKind.WAKE_EARLY && it.active }) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val ctx = com.kairosera.feature.widgets.Widgets.localized(context)
        val target = { k: HabitKind -> habits.firstOrNull { it.kind == k }?.target ?: k.defaultTarget }
        val (title, body) = when (r) {
            ArcReminder.WATER -> ctx.getString(R.string.wa_n_water_title) to ctx.getString(
                R.string.wa_n_water_body,
                ArcFormat.litres(inputs.logs[HabitKind.WATER.name]?.value ?: 0.0),
                ArcFormat.litres(target(HabitKind.WATER)),
            )
            ArcReminder.READING -> {
                val book = c.winterArc.ensureCurrentBook().let { c.books.get(it)?.title }.orEmpty()
                ctx.getString(R.string.wa_n_reading_title) to ctx.getString(R.string.wa_n_reading_body, inputs.readingMinutes, target(HabitKind.READING).toInt(), book)
            }
            ArcReminder.STUDY -> ctx.getString(R.string.wa_n_study_title) to ctx.getString(R.string.wa_n_study_body, inputs.studyTasksDone, inputs.studyTasksTotal)
            ArcReminder.COLD_SHOWER -> ctx.getString(R.string.wa_n_cold_title) to ctx.getString(R.string.wa_n_cold_body)
            ArcReminder.DEEP_WORK -> ctx.getString(R.string.wa_n_focus_title) to ctx.getString(R.string.wa_n_focus_body)
            ArcReminder.WAKE_PREP -> ctx.getString(R.string.wa_n_wake_title) to ctx.getString(R.string.wa_n_wake_body)
            ArcReminder.SPEAKING -> ctx.getString(R.string.wa_n_speak_title) to ctx.getString(R.string.wa_n_speak_body)
        }
        val open = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction(MainActivity.ACTION_OPEN_ARC)
            .putExtra(MainActivity.EXTRA_ARC_TARGET, targetFor(r))
        val pi = PendingIntent.getActivity(context, 9000 + r.ordinal, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kairos)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_BASE + r.ordinal, n) }
    }

    private fun targetFor(r: ArcReminder) = when (r) {
        ArcReminder.STUDY -> MainActivity.ARC_STUDY
        ArcReminder.DEEP_WORK -> MainActivity.ARC_FOCUS
        ArcReminder.SPEAKING -> MainActivity.ARC_SPEAK
        else -> MainActivity.ARC_HOME
    }

    companion object {
        const val CHANNEL = "winter_arc"
        const val ACTION_FIRE = "com.kairosera.action.ARC_REMINDER"
        const val EXTRA_KIND = "kind"
        private const val WINDOW_MS = 10 * 60 * 1000L
        private const val NOTIFY_BASE = 7_000_000

        /** The next moment on or after [now] that matches the time and weekdays, within a week. */
        fun nextTime(cfg: ReminderConfig, now: LocalDateTime): LocalDateTime? {
            for (i in 0..7) {
                val d = now.toLocalDate().plusDays(i.toLong())
                val at = d.atTime(cfg.time)
                if (at.isAfter(now) && d.dayOfWeek.value in cfg.days) return at
            }
            return null
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.wa_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.wa_channel_description)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }
}

/** Our own Winter Arc alarms only (not exported). */
class ArcReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ArcReminders.ACTION_FIRE) return
        val r = ArcReminder.entries.firstOrNull { it.name == intent.getStringExtra(ArcReminders.EXTRA_KIND) } ?: return
        val c = (context.applicationContext as KairosApp).container
        val pending = goAsync()
        c.appScope.launch {
            try { c.winterReminders.fire(r) } finally { pending.finish() }
        }
    }
}
