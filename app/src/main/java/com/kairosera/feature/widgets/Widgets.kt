package com.kairosera.feature.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatDelegate
import com.kairosera.AppContainer
import com.kairosera.KairosApp
import com.kairosera.MainActivity
import com.kairosera.R
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.data.quotes.QuoteRepository
import com.kairosera.domain.model.TaskOccurrence
import com.kairosera.domain.quote.DailyQuote
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.tracker.StudyProgress
import com.kairosera.domain.tracker.TrackerScoring
import com.kairosera.domain.tracker.TrackerStatsCalculator
import com.kairosera.domain.tracker.TrackerTemplate
import com.kairosera.domain.usecase.DaySummary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Everything the four widgets show, read once per refresh. Nothing here is logged. */
data class WidgetSnapshot(
    val date: LocalDate,
    val quote: DailyQuote,
    val tasks: List<TaskOccurrence>,
    val done: Int,
    val total: Int,
    /** Null when the person has nothing of that kind, so the row is hidden rather than shown empty. */
    val study: Float?,
    val reading: Float?,
    val fitness: Float?,
) {
    val tasksFraction: Float? get() = if (total == 0) null else done.toFloat() / total
    val overall: Float get() = listOfNotNull(tasksFraction, study, reading, fitness).let { if (it.isEmpty()) 0f else it.average().toFloat() }

    companion object {
        suspend fun load(c: AppContainer, date: LocalDate): WidgetSnapshot {
            val day = c.observeDayPlan(date).first()[date].orEmpty().filterNot { it.isSkipped }
            val progress = DaySummary.progress(day)
            val trackers = c.trackers.observeActive().first()
            val entries = c.trackers.observeEntries(date, date).first()
            val studyIds = trackers.filter { it.template == TrackerTemplate.STUDY || it.template == TrackerTemplate.LEARNING }.map { it.id }.toSet()
            val topics = if (studyIds.isEmpty()) emptyList() else c.study.observeAllTopics().first().filter { it.trackerId in studyIds }
            val study = StudyProgress.summarize(topics).takeIf { it.leafTopics > 0 }?.fraction?.toFloat()
            val book = c.books.observeBooks().first().firstOrNull { it.status == BookStatus.READING && it.totalPages > 0 }
            val fitness = trackers
                .filter { (it.template == TrackerTemplate.FITNESS || it.template == TrackerTemplate.HEALTH) && TrackerStatsCalculator.isDue(it.frequency, date) }
                .map { t -> TrackerScoring.score(t, entries.firstOrNull { it.trackerId == t.id }).fraction }
                .takeIf { it.isNotEmpty() }?.average()?.toFloat()
            return WidgetSnapshot(
                date = date,
                quote = runCatching { c.quotes.forDate(date) }.getOrDefault(QuoteRepository.FALLBACK),
                // Timed tasks in time order, then any-time ones; open before done within each.
                tasks = day.sortedWith(compareBy<TaskOccurrence>({ it.task.startTime == null }, { it.task.startTime }, { it.isDone })),
                done = progress.done,
                total = progress.total,
                study = study,
                reading = book?.progress?.toFloat(),
                fitness = fitness,
            )
        }
    }
}

/** Base for the four home-screen widgets: loads one snapshot and renders plain RemoteViews (no extra library). */
abstract class KairosWidget : AppWidgetProvider() {
    abstract fun render(context: Context, s: WidgetSnapshot): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val container = (context.applicationContext as KairosApp).container
        container.appScope.launch {
            try {
                Widgets.update(context, container)
                Widgets.startSync(context, container)
            } finally {
                pending.finish()
            }
        }
    }
}

class MotivationWidget : KairosWidget() {
    override fun render(context: Context, s: WidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_motivation).apply {
        val ctx = Widgets.localized(context)
        setTextViewText(R.id.brand, ctx.getString(R.string.widget_brand))
        setTextViewText(R.id.date, DateTimeFormatter.ofPattern("EEE d MMM", Widgets.locale(ctx)).format(s.date))
        setTextViewText(R.id.quote, s.quote.quote)
        setTextViewText(R.id.action, ctx.getString(R.string.quote_action, s.quote.actionPrompt))
        setOnClickPendingIntent(R.id.widget_root, Widgets.openApp(context, MainActivity.ACTION_OPEN_QUOTE))
    }
}

class TasksWidget : KairosWidget() {
    override fun render(context: Context, s: WidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_tasks).apply {
        val ctx = Widgets.localized(context)
        val timeFmt = DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Widgets.locale(ctx))
        setTextViewText(R.id.title, ctx.getString(R.string.today))
        setTextViewText(R.id.count, if (s.total == 0) "" else ctx.getString(R.string.widget_done_count, s.done, s.total))
        // Rows beyond what fits are summarised in the header; the whole card opens Today.
        val extra = s.tasks.size - minOf(s.tasks.size, ROWS.size)
        setViewVisibility(R.id.more, if (extra > 0) View.VISIBLE else View.GONE)
        if (extra > 0) setTextViewText(R.id.more, ctx.getString(R.string.widget_more_tasks, extra))
        setTextViewText(R.id.add, ctx.getString(R.string.widget_add))
        setOnClickPendingIntent(R.id.add, Widgets.openApp(context, MainActivity.ACTION_NEW_TASK))
        setOnClickPendingIntent(R.id.widget_root, Widgets.openApp(context, MainActivity.ACTION_OPEN_DAY))
        val shown = s.tasks.take(ROWS.size)
        setViewVisibility(R.id.empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)
        setTextViewText(R.id.empty, ctx.getString(R.string.widget_empty))
        ROWS.forEachIndexed { i, row ->
            val o = shown.getOrNull(i)
            setViewVisibility(row.row, if (o == null) View.GONE else View.VISIBLE)
            if (o == null) return@forEachIndexed
            setTextViewText(row.title, o.task.title)
            setTextColor(row.title, context.getColor(if (o.isDone) R.color.widget_muted else R.color.widget_ink))
            setTextViewText(row.time, o.task.startTime?.let(timeFmt::format) ?: ctx.getString(R.string.widget_all_day))
            setImageViewResource(row.check, if (o.isDone) R.drawable.widget_check_on else R.drawable.widget_check_off)
            setContentDescription(row.check, ctx.getString(if (o.isDone) R.string.widget_mark_open else R.string.widget_mark_done, o.task.title))
            setOnClickPendingIntent(row.check, WidgetActionReceiver.toggle(context, o.task.id, o.date, !o.isDone))
            setOnClickPendingIntent(row.row, Widgets.openApp(context, MainActivity.ACTION_OPEN_DAY))
        }
    }

    private class Row(val row: Int, val check: Int, val title: Int, val time: Int)

    private companion object {
        val ROWS = listOf(
            Row(R.id.row0, R.id.check0, R.id.title0, R.id.time0),
            Row(R.id.row1, R.id.check1, R.id.title1, R.id.time1),
            Row(R.id.row2, R.id.check2, R.id.title2, R.id.time2),
            Row(R.id.row3, R.id.check3, R.id.title3, R.id.time3),
            Row(R.id.row4, R.id.check4, R.id.title4, R.id.time4),
        )
    }
}

class ProgressWidget : KairosWidget() {
    override fun render(context: Context, s: WidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_progress).apply {
        val ctx = Widgets.localized(context)
        setTextViewText(R.id.title, ctx.getString(R.string.widget_progress_title))
        setTextViewText(R.id.overall, percent(s.overall))
        setTextViewText(R.id.footer, ctx.getString(R.string.widget_keep_going))
        bar(R.id.row_tasks, R.id.label_tasks, R.id.value_tasks, R.id.bar_tasks, ctx.getString(R.string.widget_tasks), s.tasksFraction,
            if (s.total > 0) "${s.done}/${s.total}" else null)
        bar(R.id.row_study, R.id.label_study, R.id.value_study, R.id.bar_study, ctx.getString(R.string.widget_study), s.study)
        bar(R.id.row_reading, R.id.label_reading, R.id.value_reading, R.id.bar_reading, ctx.getString(R.string.widget_reading), s.reading)
        bar(R.id.row_fitness, R.id.label_fitness, R.id.value_fitness, R.id.bar_fitness, ctx.getString(R.string.widget_fitness), s.fitness)
        setOnClickPendingIntent(R.id.widget_root, Widgets.openApp(context, MainActivity.ACTION_OPEN_TRACK))
    }

    private fun RemoteViews.bar(row: Int, label: Int, value: Int, bar: Int, text: String, fraction: Float?, valueText: String? = null) {
        setViewVisibility(row, if (fraction == null) View.GONE else View.VISIBLE)
        if (fraction == null) return
        setTextViewText(label, text)
        setTextViewText(value, valueText ?: percent(fraction))
        setProgressBar(bar, 100, (fraction * 100).roundToInt().coerceIn(0, 100), false)
    }

    private fun percent(f: Float) = "${(f * 100).roundToInt().coerceIn(0, 100)}%"
}

class QuickAddWidget : KairosWidget() {
    override fun render(context: Context, s: WidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_quick_add).apply {
        val ctx = Widgets.localized(context)
        setTextViewText(R.id.label_task, ctx.getString(R.string.widget_task))
        setTextViewText(R.id.label_log, ctx.getString(R.string.widget_log))
        setTextViewText(R.id.label_more, ctx.getString(R.string.widget_note))
        setOnClickPendingIntent(R.id.btn_task, Widgets.openApp(context, MainActivity.ACTION_NEW_TASK))
        setOnClickPendingIntent(R.id.btn_log, Widgets.openApp(context, MainActivity.ACTION_OPEN_TRACK))
        setOnClickPendingIntent(R.id.btn_more, Widgets.openApp(context, MainActivity.ACTION_NEW_JOURNAL))
    }
}

/** Checkbox taps from the Today's Tasks widget. Not exported: only our own PendingIntents reach it. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val taskId = intent.getLongExtra(EXTRA_TASK, -1L)
        val day = intent.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
        if (taskId <= 0 || day !in MIN_DAY..MAX_DAY) return
        val done = intent.getBooleanExtra(EXTRA_DONE, true)
        val container = (context.applicationContext as KairosApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.setOccurrenceDone(taskId, LocalDate.ofEpochDay(day), done)
                Widgets.update(context, container)
            } catch (e: Exception) {
                SafeLog.error("widget_toggle_failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.kairosera.action.WIDGET_TOGGLE"
        private const val EXTRA_TASK = "taskId"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_DONE = "done"
        private val MIN_DAY = LocalDate.of(2000, 1, 1).toEpochDay()
        private val MAX_DAY = LocalDate.of(2200, 1, 1).toEpochDay()

        fun toggle(context: Context, taskId: Long, date: LocalDate, done: Boolean): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java)
                .setAction(ACTION_TOGGLE)
                // A distinct data URI keeps one PendingIntent per row; it carries only numbers.
                .setData(Uri.parse("kairos://widget/task/$taskId/${date.toEpochDay()}"))
                .putExtra(EXTRA_TASK, taskId)
                .putExtra(EXTRA_DATE, date.toEpochDay())
                .putExtra(EXTRA_DONE, done)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}

object Widgets {
    private val PROVIDERS = listOf(MotivationWidget::class.java, TasksWidget::class.java, ProgressWidget::class.java, QuickAddWidget::class.java)
    private var sync: Job? = null

    /** Redraws every placed widget from one fresh snapshot. Cheap no-op when none are placed. */
    suspend fun update(context: Context, c: AppContainer) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val placed = PROVIDERS.associateWith { manager.getAppWidgetIds(ComponentName(context, it)) }.filterValues { it.isNotEmpty() }
        if (placed.isEmpty()) return
        val snapshot = try {
            WidgetSnapshot.load(c, LocalDate.now())
        } catch (e: Exception) {
            SafeLog.error("widget_load_failed", e)
            return
        }
        placed.forEach { (cls, ids) ->
            val provider = cls.getDeclaredConstructor().newInstance()
            ids.forEach { id -> runCatching { manager.updateAppWidget(id, provider.render(context, snapshot)) }.onFailure { SafeLog.error("widget_render_failed", it) } }
        }
    }

    /**
     * While the app process is alive and at least one widget is placed, follow the database so
     * widgets change the moment something is checked off, logged or read in the app.
     */
    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Synchronized
    fun startSync(context: Context, c: AppContainer) {
        if (sync?.isActive == true || !anyPlaced(context)) return
        val app = context.applicationContext
        val days = flow { while (true) { emit(LocalDate.now()); delay(60_000) } }.distinctUntilChanged()
        sync = c.appScope.launch {
            days.flatMapLatest { date ->
                combine(
                    c.observeDayPlan(date),
                    c.trackers.observeActive(),
                    c.trackers.observeEntries(date, date),
                    c.study.observeAllTopics(),
                    c.books.observeBooks(),
                ) { _, _, _, _, _ -> date }
            }.debounce(400).collect {
                if (!anyPlaced(app)) { sync?.cancel(); return@collect }
                update(app, c)
            }
        }
    }

    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return PROVIDERS.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    fun openApp(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction(action)
        return PendingIntent.getActivity(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Widget text follows the in-app language choice (Settings), not only the phone's language. */
    fun localized(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(locales[0])
        return context.createConfigurationContext(config)
    }

    fun locale(context: Context) = context.resources.configuration.locales[0]
}
