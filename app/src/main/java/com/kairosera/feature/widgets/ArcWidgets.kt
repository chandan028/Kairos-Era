package com.kairosera.feature.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.kairosera.AppContainer
import com.kairosera.KairosApp
import com.kairosera.MainActivity
import com.kairosera.R
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.data.quotes.QuoteRepository
import com.kairosera.data.winterarc.WinterArcRepository
import com.kairosera.domain.quote.DailyQuote
import com.kairosera.domain.winterarc.ArcStats
import com.kairosera.domain.winterarc.DailySummary
import com.kairosera.domain.winterarc.DayInputs
import com.kairosera.domain.winterarc.DayState
import com.kairosera.domain.winterarc.Habit
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.HabitRules
import com.kairosera.domain.winterarc.StudyTask
import com.kairosera.domain.winterarc.WakeTime
import com.kairosera.domain.winterarc.WinterArc
import com.kairosera.feature.winterarc.ArcFormat
import com.kairosera.feature.winterarc.gridLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Everything the Winter Arc widgets show, read once per refresh. */
data class ArcWidgetSnapshot(
    val date: LocalDate,
    val on: Boolean,
    val arc: WinterArc?,
    val habits: List<Habit>,
    val inputs: DayInputs,
    val summary: DailySummary,
    val study: List<StudyTask>,
    val bookTitle: String,
    val bookAuthor: String,
    val readingStreak: Int,
    val topic: String?,
    val spoke: Boolean,
    val quote: DailyQuote,
    /** Day colours for this month and this week. */
    val month: Map<LocalDate, DayState>,
    val streak: Int,
    val best: Int,
) {
    fun target(k: HabitKind) = habits.firstOrNull { it.kind == k }?.target ?: k.defaultTarget
    val dayNumber get() = arc?.dayNumber(date, date) ?: 1
    val duration get() = arc?.durationDays ?: WinterArc.DEFAULT_DURATION

    companion object {
        suspend fun load(c: AppContainer, date: LocalDate): ArcWidgetSnapshot {
            val repo = c.winterArc
            val prefs = c.winterPrefs.current()
            val arc = repo.currentArc()
            val habits = repo.habits.first()
            val month = YearMonth.from(date)
            // A window wide enough for this month, this week and a long streak.
            val from = minOf(month.atDay(1), date.minusDays(120), arc?.startDate ?: date)
            val days = repo.observeDays(minOf(from, date.with(DayOfWeek.MONDAY)), maxOf(month.atEndOfMonth(), date.with(DayOfWeek.SUNDAY))).first()
            val inputs = days[date] ?: DayInputs(date)
            val summaryOf = { d: LocalDate -> HabitRules.summarize(habits, days[d] ?: DayInputs(d)) }
            // This month for the calendar, plus this week (which can start in the previous month).
            val monday = date.with(DayOfWeek.MONDAY)
            val stateDates = ((1..month.lengthOfMonth()).map { month.atDay(it) } + (0L..6L).map { monday.plusDays(it) }).distinct()
            val states = stateDates.associateWith { d -> HabitRules.stateOf(arc, d, date, if (d.isAfter(date)) null else summaryOf(d)) }
            val counting = arc?.countingDates(date, date).orEmpty()
            val streaks = ArcStats.streaks(counting, { HabitRules.isComplete(summaryOf(it)) }, date)
            val readingHabit = habits.firstOrNull { it.kind == HabitKind.READING }
            val readingDates = generateSequence(date.minusDays(120)) { it.plusDays(1) }.takeWhile { !it.isAfter(date) }.toList()
            val readingStreak = readingHabit?.let { h -> ArcStats.habitStreak(readingDates, { d -> HabitRules.evaluate(h, days[d] ?: DayInputs(d)).done }, date) } ?: 0
            val bookId = runCatching { repo.ensureCurrentBook() }.getOrNull()
            val book = bookId?.let { c.books.get(it) }
            val speaking = if (prefs.enabled && arc != null) runCatching { repo.topicFor(date) }.getOrNull() else null
            return ArcWidgetSnapshot(
                date = date,
                on = prefs.enabled && prefs.onboarded && arc != null,
                arc = arc,
                habits = habits,
                inputs = inputs,
                summary = HabitRules.summarize(habits, inputs),
                study = repo.observeStudy(date, date).first(),
                bookTitle = book?.title ?: WinterArcRepository.DEFAULT_BOOK_TITLE,
                bookAuthor = book?.author ?: WinterArcRepository.DEFAULT_BOOK_AUTHOR,
                readingStreak = readingStreak,
                topic = speaking?.topic,
                spoke = speaking?.completed == true,
                quote = runCatching { c.quotes.forDate(date) }.getOrDefault(QuoteRepository.FALLBACK),
                month = states,
                streak = streaks.current,
                best = streaks.longest,
            )
        }
    }
}

/** Base for the Winter Arc widgets; shares one refresh path with the everyday widgets. */
abstract class ArcWidget : AppWidgetProvider() {
    abstract fun render(context: Context, s: ArcWidgetSnapshot): RemoteViews

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

    protected fun open(context: Context, target: String): PendingIntent = ArcWidgets.open(context, target)
}

class ArcDayWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc).apply {
        val ctx = Widgets.localized(context)
        setTextViewText(R.id.label, ctx.getString(R.string.wa_ob_title))
        setTextViewText(R.id.day, if (s.on) ctx.getString(R.string.wa_day_short, s.dayNumber, s.duration) else ctx.getString(R.string.wa_widget_off))
        setTextViewText(R.id.habits, ctx.getString(R.string.wa_completed_of, s.summary.completedHabits, s.summary.totalHabits))
        setTextViewText(R.id.percent, ctx.getString(R.string.wa_percent, s.summary.completionPercentage))
        setProgressBar(R.id.bar, 100, s.summary.completionPercentage, false)
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_HOME))
    }
}

class ArcHabitsWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_large).apply {
        val ctx = Widgets.localized(context)
        setTextViewText(R.id.label, ctx.getString(R.string.wa_widget_today))
        setTextViewText(R.id.percent, ctx.getString(R.string.wa_percent, s.summary.completionPercentage))
        setTextViewText(R.id.day, if (s.on) ctx.getString(R.string.wa_day_of, s.dayNumber, s.duration) else ctx.getString(R.string.wa_widget_off))
        setProgressBar(R.id.bar, 100, s.summary.completionPercentage, false)
        val cells = listOf(R.id.h0, R.id.h1, R.id.h2, R.id.h3, R.id.h4, R.id.h5, R.id.h6, R.id.h7, R.id.h8, R.id.h9)
        cells.forEachIndexed { i, id ->
            val h = s.summary.habits.getOrNull(i)
            setViewVisibility(id, if (h == null) View.INVISIBLE else View.VISIBLE)
            if (h == null) return@forEachIndexed
            val label = ctx.getString(h.kind.gridLabel())
            setTextViewText(id, (if (h.done) "✓\n" else "") + label)
            setInt(id, "setBackgroundResource", if (h.done) R.drawable.arc_w_chip_on else R.drawable.arc_w_chip_off)
            setTextColor(id, context.getColor(if (h.done) R.color.arc_w_blue else R.color.arc_w_muted))
            setContentDescription(id, ctx.getString(R.string.wa_a11y_toggle, label, ctx.getString(if (h.done) R.string.wa_done else R.string.wa_not_yet)))
        }
        setViewVisibility(R.id.hrow1, if (s.summary.habits.size > 5) View.VISIBLE else View.GONE)
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_HOME))
    }
}

class ArcQuoteWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_quote).apply {
        val ctx = Widgets.localized(context)
        setTextViewText(R.id.label, ctx.getString(R.string.wa_ob_title))
        setTextViewText(R.id.quote, s.quote.quote)
        setTextViewText(R.id.author, ctx.getString(R.string.wa_statement).replace('\n', ' '))
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_HOME))
    }
}

class ArcClockWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_clock).apply {
        val ctx = Widgets.localized(context)
        val wake = s.summary.habits.firstOrNull { it.kind == HabitKind.WAKE_EARLY }
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Widgets.locale(ctx))
        val wakeText = when {
            wake == null || !wake.logged -> ctx.getString(R.string.wa_wake_rule)
            wake.done -> ctx.getString(R.string.wa_wake_ok, timeFmt.format(WakeTime.toTime(wake.value.toInt())))
            else -> ctx.getString(R.string.wa_wake_late, timeFmt.format(WakeTime.toTime(wake.value.toInt())))
        }
        setTextViewText(R.id.wake, wakeText)
        setTextViewText(R.id.line, ctx.getString(R.string.wa_widget_clock_line))
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_HOME))
    }
}

class ArcWaterWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_water).apply {
        val ctx = Widgets.localized(context)
        val locale = Widgets.locale(ctx)
        val ml = s.inputs.logs[HabitKind.WATER.name]?.value ?: 0.0
        val target = s.target(HabitKind.WATER)
        setTextViewText(R.id.label, ctx.getString(R.string.wa_h_water))
        setTextViewText(R.id.value, ctx.getString(R.string.wa_target_l, ArcFormat.litres(ml, locale), ArcFormat.litres(target, locale)))
        setProgressBar(R.id.bar, 100, fraction(ml, target), false)
        listOf(R.id.add250 to 250, R.id.add500 to 500, R.id.add1000 to 1000).forEach { (id, amount) ->
            val label = if (amount == 1000) ctx.getString(R.string.wa_water_add_l) else ctx.getString(R.string.wa_water_add, amount)
            setTextViewText(id, label)
            setContentDescription(id, label)
            setOnClickPendingIntent(id, ArcWidgetReceiver.addWater(context, amount))
        }
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_WATER))
    }
}

class ArcStepsWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_steps).apply {
        val ctx = Widgets.localized(context)
        val locale = Widgets.locale(ctx)
        val steps = (s.inputs.logs[HabitKind.STEPS.name]?.value ?: 0.0).toInt()
        val target = s.target(HabitKind.STEPS).toInt()
        setTextViewText(R.id.label, ctx.getString(R.string.wa_h_steps))
        setTextViewText(R.id.value, ArcFormat.count(steps, locale))
        setTextViewText(R.id.target, "/ " + ArcFormat.count(target, locale))
        setProgressBar(R.id.bar, 100, fraction(steps.toDouble(), target.toDouble()), false)
        val left = (target - steps).coerceAtLeast(0)
        setTextViewText(R.id.left, if (left == 0) "✓ " + ctx.getString(R.string.wa_done) else ctx.getString(R.string.wa_widget_steps_left, ArcFormat.count(left, locale)))
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_WATER))
    }
}

class ArcStudyWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_study).apply {
        val ctx = Widgets.localized(context)
        setTextViewText(R.id.label, ctx.getString(R.string.wa_widget_todays_study))
        val done = s.study.count { it.completed }
        setTextViewText(R.id.count, if (s.study.isEmpty()) "" else "$done/${s.study.size}")
        setViewVisibility(R.id.empty, if (s.study.isEmpty()) View.VISIBLE else View.GONE)
        setTextViewText(R.id.empty, ctx.getString(R.string.wa_widget_no_study))
        val rows = listOf(Triple(R.id.row0, R.id.check0, R.id.title0), Triple(R.id.row1, R.id.check1, R.id.title1), Triple(R.id.row2, R.id.check2, R.id.title2), Triple(R.id.row3, R.id.check3, R.id.title3))
        val shown = s.study.sortedBy { it.completed }.take(rows.size)
        rows.forEachIndexed { i, (row, check, title) ->
            val t = shown.getOrNull(i)
            setViewVisibility(row, if (t == null) View.GONE else View.VISIBLE)
            if (t == null) return@forEachIndexed
            setTextViewText(check, if (t.completed) "✓" else "○")
            setTextViewText(title, t.title)
            setTextColor(title, context.getColor(if (t.completed) R.color.arc_w_muted else R.color.arc_w_ink))
        }
        setTextViewText(R.id.focus, ctx.getString(R.string.wa_start_focus))
        setOnClickPendingIntent(R.id.focus, open(context, MainActivity.ARC_FOCUS))
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_STUDY))
    }
}

class ArcCalendarWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_calendar).apply {
        val ctx = Widgets.localized(context)
        val locale = Widgets.locale(ctx)
        val month = YearMonth.from(s.date)
        setTextViewText(R.id.label, DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month))
        val complete = s.month.filterKeys { YearMonth.from(it) == month }.values.count { it == DayState.COMPLETE }
        setTextViewText(R.id.summary, "✓ $complete")
        val heads = listOf(R.id.wd0, R.id.wd1, R.id.wd2, R.id.wd3, R.id.wd4, R.id.wd5, R.id.wd6)
        DayOfWeek.entries.forEachIndexed { i, d -> setTextViewText(heads[i], d.getDisplayName(TextStyle.NARROW, locale)) }
        val lead = month.atDay(1).dayOfWeek.value - 1
        val rows = (lead + month.lengthOfMonth() + 6) / 7
        listOf(R.id.crow0, R.id.crow1, R.id.crow2, R.id.crow3, R.id.crow4, R.id.crow5).forEachIndexed { r, id -> setViewVisibility(id, if (r < rows) View.VISIBLE else View.GONE) }
        CELLS.forEachIndexed { i, id ->
            val n = i - lead + 1
            if (n !in 1..month.lengthOfMonth()) {
                setTextViewText(id, "")
                setInt(id, "setBackgroundResource", R.drawable.arc_w_bg_none)
                return@forEachIndexed
            }
            val state = s.month[month.atDay(n)] ?: DayState.OUTSIDE
            setTextViewText(id, n.toString())
            setInt(id, "setBackgroundResource", squareFor(state))
            val onColor = state == DayState.COMPLETE || state == DayState.MISSED || state == DayState.TODAY
            setTextColor(id, if (onColor) 0xFFFFFFFF.toInt() else context.getColor(R.color.arc_w_ink))
        }
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_CALENDAR))
    }

    private companion object {
        val CELLS = listOf(
            R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6, R.id.c7, R.id.c8, R.id.c9,
            R.id.c10, R.id.c11, R.id.c12, R.id.c13, R.id.c14, R.id.c15, R.id.c16, R.id.c17, R.id.c18, R.id.c19,
            R.id.c20, R.id.c21, R.id.c22, R.id.c23, R.id.c24, R.id.c25, R.id.c26, R.id.c27, R.id.c28, R.id.c29,
            R.id.c30, R.id.c31, R.id.c32, R.id.c33, R.id.c34, R.id.c35, R.id.c36, R.id.c37, R.id.c38, R.id.c39,
            R.id.c40, R.id.c41,
        )
    }
}

class ArcSpeakWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_speak).apply {
        val ctx = Widgets.localized(context)
        setTextViewText(R.id.label, ctx.getString(R.string.wa_speak_topic))
        setTextViewText(R.id.status, if (s.spoke) "✓" else "")
        setTextViewText(R.id.topic, s.topic ?: ctx.getString(R.string.wa_widget_off))
        setTextViewText(R.id.start, ctx.getString(if (s.spoke) R.string.wa_speak_again else R.string.wa_speak_start))
        setOnClickPendingIntent(R.id.start, open(context, MainActivity.ARC_SPEAK))
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_SPEAK))
    }
}

class ArcBookWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_book).apply {
        val ctx = Widgets.localized(context)
        val target = s.target(HabitKind.READING).toInt()
        setTextViewText(R.id.label, ctx.getString(R.string.wa_h_reading))
        setTextViewText(R.id.streak, if (s.readingStreak > 0) ctx.getString(R.string.wa_streak_days, s.readingStreak) else "")
        setTextViewText(R.id.title, s.bookTitle)
        setTextViewText(R.id.author, s.bookAuthor)
        setTextViewText(R.id.value, ctx.getString(R.string.wa_widget_book_today, s.inputs.readingMinutes, target))
        setProgressBar(R.id.bar, 100, fraction(s.inputs.readingMinutes.toDouble(), target.toDouble()), false)
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_READING))
    }
}

class ArcStreakWidget : ArcWidget() {
    override fun render(context: Context, s: ArcWidgetSnapshot) = RemoteViews(context.packageName, R.layout.widget_arc_week).apply {
        val ctx = Widgets.localized(context)
        val locale = Widgets.locale(ctx)
        setTextViewText(R.id.label, ctx.getString(R.string.wa_current_streak))
        setTextViewText(R.id.best, ctx.getString(R.string.wa_best_streak, s.best))
        setTextViewText(R.id.streak, ctx.getString(R.string.wa_days_value, s.streak))
        val monday = s.date.with(DayOfWeek.MONDAY)
        val squares = listOf(R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6)
        val labels = listOf(R.id.l0, R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5, R.id.l6)
        (0 until 7).forEach { i ->
            val d = monday.plusDays(i.toLong())
            // The week can cross into another month; those days fall back to a plain square.
            val state = s.month[d] ?: if (d.isAfter(s.date)) DayState.FUTURE else DayState.OUTSIDE
            setInt(squares[i], "setBackgroundResource", squareFor(state))
            setTextViewText(labels[i], d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale))
        }
        setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.ARC_CALENDAR))
    }
}

private fun fraction(v: Double, target: Double) = if (target <= 0) 0 else (v / target * 100).roundToInt().coerceIn(0, 100)

private fun squareFor(state: DayState) = when (state) {
    DayState.COMPLETE -> R.drawable.arc_w_sq_complete
    DayState.PARTIAL -> R.drawable.arc_w_sq_partial
    DayState.MISSED -> R.drawable.arc_w_sq_missed
    DayState.TODAY -> R.drawable.arc_w_sq_today
    DayState.PAUSED -> R.drawable.arc_w_sq_paused
    DayState.FUTURE, DayState.OUTSIDE -> R.drawable.arc_w_sq_empty
}

/** Water quick-adds from the widget. Not exported: only our own PendingIntents reach it. */
class ArcWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD_WATER) return
        val ml = intent.getIntExtra(EXTRA_ML, 0)
        if (ml !in ALLOWED) return
        val container = (context.applicationContext as KairosApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.winterArc.addWater(LocalDate.now(), ml)
                Widgets.update(context, container)
            } catch (e: Exception) {
                SafeLog.error("arc_widget_water_failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_ADD_WATER = "com.kairosera.action.ARC_ADD_WATER"
        private const val EXTRA_ML = "ml"
        private val ALLOWED = setOf(250, 500, 1000)

        fun addWater(context: Context, ml: Int): PendingIntent {
            val intent = Intent(context, ArcWidgetReceiver::class.java)
                .setAction(ACTION_ADD_WATER)
                .setData(Uri.parse("kairos://widget/water/$ml"))
                .putExtra(EXTRA_ML, ml)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}

object ArcWidgets {
    val PROVIDERS = listOf(
        ArcDayWidget::class.java, ArcHabitsWidget::class.java, ArcQuoteWidget::class.java, ArcClockWidget::class.java,
        ArcWaterWidget::class.java, ArcStepsWidget::class.java, ArcStudyWidget::class.java, ArcCalendarWidget::class.java,
        ArcSpeakWidget::class.java, ArcBookWidget::class.java, ArcStreakWidget::class.java,
    )

    fun open(context: Context, target: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction(MainActivity.ACTION_OPEN_ARC)
            .setData(Uri.parse("kairos://arc/$target"))
            .putExtra(MainActivity.EXTRA_ARC_TARGET, target)
        return PendingIntent.getActivity(context, ("arc_$target").hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
