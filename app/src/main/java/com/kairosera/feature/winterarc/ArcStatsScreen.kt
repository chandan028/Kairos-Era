package com.kairosera.feature.winterarc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.currentLocale
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.winterarc.ArcStats
import com.kairosera.domain.winterarc.DayState
import com.kairosera.domain.winterarc.Habit
import com.kairosera.domain.winterarc.HabitType
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.HabitRules
import com.kairosera.domain.winterarc.WinterArc
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Week, month and whole-challenge totals with a habit-by-day consistency grid. */
@Composable
fun ArcStatsScreen(vm: ArcViewModel, onBack: (() -> Unit)?, onOpen90: () -> Unit, onOpenSpeechProgress: () -> Unit = {}) {
    val today by vm.today.collectAsStateWithLifecycle()
    val arc by vm.arc.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val (from, to) = remember(tab, today, arc) { periodFor(tab, today, arc) }
    val range by remember(from, to) { vm.range(from, to) }.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.wa_progress), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally).widthIn(max = ContentMaxWidth),
        ) {
            ArcTabs(
                listOf(stringResource(R.string.wa_tab_week), stringResource(R.string.wa_tab_month), stringResource(R.string.wa_tab_all_time)),
                tab, { tab = it },
            )
            Spacer(Modifier.height(14.dp))
            val r = range
            if (r == null) { LoadingState(Modifier.height(240.dp)); return@Column }
            val totals = remember(r) { Totals.of(r) }
            ArcCard(Modifier.fillMaxWidth(), color = Arc.primarySoft) {
                Text(
                    stringResource(listOf(R.string.wa_this_week, R.string.wa_this_month, R.string.wa_all_time)[tab]),
                    style = MaterialTheme.typography.labelLarge, color = Arc.primary,
                )
                Text(
                    stringResource(R.string.wa_habits_done_of, totals.habitsDone, totals.habitsPossible),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                ArcBar(if (totals.habitsPossible == 0) 0f else totals.habitsDone.toFloat() / totals.habitsPossible, Arc.primary)
            }
            Spacer(Modifier.height(12.dp))
            val locale = currentLocale()
            val cards = buildList {
                val active = r.habits.filter { it.active }
                val kinds = active.map { it.kind }.toSet()
                fun habit(k: HabitKind) = r.habits.first { it.kind == k }
                fun target(k: HabitKind) = r.habits.firstOrNull { it.kind == k }?.target ?: k.defaultTarget
                val n = totals.periodDays
                if (HabitKind.WATER in kinds) add(StatCard(habit(HabitKind.WATER), stringResource(R.string.wa_stat_water, ArcFormat.litres(totals.waterMl, locale), ArcFormat.litres(target(HabitKind.WATER) * n, locale))))
                if (HabitKind.STEPS in kinds) add(StatCard(habit(HabitKind.STEPS), stringResource(R.string.wa_stat_steps, ArcFormat.compact(totals.steps), ArcFormat.compact((target(HabitKind.STEPS) * n).toInt()))))
                if (HabitKind.STUDY in kinds) add(StatCard(habit(HabitKind.STUDY), stringResource(R.string.wa_stat_hours, ArcFormat.hours(totals.studyMin), ArcFormat.hours((target(HabitKind.STUDY) * n).toInt()))))
                if (HabitKind.READING in kinds) add(StatCard(habit(HabitKind.READING), stringResource(R.string.wa_stat_days, totals.doneDays(HabitKind.READING.name), n)))
                if (HabitKind.DEEP_WORK in kinds) add(StatCard(habit(HabitKind.DEEP_WORK), stringResource(R.string.wa_stat_hours, ArcFormat.hours(totals.focusMin), ArcFormat.hours((target(HabitKind.DEEP_WORK) * n).toInt()))))
                if (HabitKind.SPEAK in kinds) add(StatCard(habit(HabitKind.SPEAK), stringResource(R.string.wa_stat_days, totals.doneDays(HabitKind.SPEAK.name), n)))
                // A person's own habits: the total for amounts, days done for checks.
                active.filter { it.isCustom }.sortedBy { it.sortOrder }.forEach { h ->
                    add(
                        StatCard(
                            h,
                            if (h.type == HabitType.AMOUNT) customAmount(totals.amount(h.id), h.target * n, h.unit, locale)
                            else stringResource(R.string.wa_stat_days, totals.doneDays(h.id), n),
                        ),
                    )
                }
            }
            cards.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { card ->
                        val tone = Arc.tone(card.habit)
                        ArcCard(Modifier.weight(1f), color = tone.soft, padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HabitBadge(card.habit.iconVector(), tone, size = 28.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(card.habit.shortName(), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(card.value, style = MaterialTheme.typography.titleMedium, color = tone.strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            ArcSectionTitle(stringResource(R.string.wa_consistency))
            ArcCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                if (tab == 0) WeekGrid(r) else RateList(r, totals)
            }
            Spacer(Modifier.height(12.dp))
            ArcCard(Modifier.fillMaxWidth(), onClick = onOpen90) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.wa_90_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.wa_90_open), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                }
            }
            SpeechProgressCard(onOpenSpeechProgress)
            Spacer(Modifier.height(96.dp))
        }
    }
}

private fun periodFor(tab: Int, today: LocalDate, arc: WinterArc?): Pair<LocalDate, LocalDate> = when (tab) {
    0 -> today.with(DayOfWeek.MONDAY).let { it to it.plusDays(6) }
    1 -> today.withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
    else -> {
        val start = arc?.startDate ?: today.minusDays(29)
        val end = arc?.endDate(today)?.let { minOf(it, today) } ?: today
        if (end.isBefore(start)) start to start else start to end
    }
}

/** Sums over the counting days of a range, up to today. */
private class Totals(
    val days: List<LocalDate>,
    val periodDays: Int,
    val habitsDone: Int,
    val habitsPossible: Int,
    val waterMl: Double,
    val steps: Int,
    val studyMin: Int,
    val focusMin: Int,
    /** Days done, by habit id. */
    private val done: Map<String, Int>,
    /** Summed amounts of custom habits, by habit id. */
    private val amounts: Map<String, Double>,
) {
    fun doneDays(id: String) = done[id] ?: 0
    fun amount(id: String) = amounts[id] ?: 0.0

    companion object {
        fun of(r: ArcRange): Totals {
            val all = generateSequence(r.from) { it.plusDays(1) }.takeWhile { !it.isAfter(r.to) }.toList()
            val inArc = { d: LocalDate -> r.arc == null || r.arc.counts(d, r.today) || (d.isAfter(r.today) && !d.isBefore(r.arc.startDate) && !d.isAfter(r.arc.endDate(r.today))) }
            val period = all.filter(inArc)
            val elapsed = period.filter { !it.isAfter(r.today) }
            val active = r.habits.count { it.active }
            val done = HashMap<String, Int>()
            var habitsDone = 0
            elapsed.forEach { d ->
                r.summary(d).habits.forEach { h -> if (h.done) { habitsDone++; done[h.habitId] = (done[h.habitId] ?: 0) + 1 } }
            }
            return Totals(
                days = elapsed,
                periodDays = period.size,
                habitsDone = habitsDone,
                habitsPossible = active * period.size,
                waterMl = elapsed.sumOf { r.inputsOn(it).logs[HabitKind.WATER.name]?.value ?: 0.0 },
                steps = elapsed.sumOf { (r.inputsOn(it).logs[HabitKind.STEPS.name]?.value ?: 0.0).toInt() },
                studyMin = elapsed.sumOf { r.inputsOn(it).studyMinutes },
                focusMin = elapsed.sumOf { r.inputsOn(it).focusMinutes },
                done = done,
                amounts = r.habits.filter { it.isCustom && it.type == HabitType.AMOUNT }.associate { h ->
                    h.id to elapsed.sumOf { r.inputsOn(it).logs[h.id]?.value ?: 0.0 }
                },
            )
        }
    }
}

private data class StatCard(val habit: Habit, val value: String)

@Composable
private fun WeekGrid(r: ArcRange) {
    val locale = currentLocale()
    val dates = generateSequence(r.from) { it.plusDays(1) }.take(7).toList()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(2.2f))
        dates.forEach { d ->
            Text(
                d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall,
                color = if (d == r.today) Arc.primary else Kairos.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    val doneLabel = stringResource(R.string.wa_done)
    val notLabel = stringResource(R.string.wa_not_yet)
    r.habits.filter { it.active }.sortedBy { it.sortOrder }.forEach { habit ->
        val tone = Arc.tone(habit)
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(2.2f), verticalAlignment = Alignment.CenterVertically) {
                HabitBadge(habit.iconVector(), tone, size = 24.dp)
                Spacer(Modifier.width(6.dp))
                Text(habit.shortName(), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            dates.forEach { d ->
                val future = d.isAfter(r.today)
                val isDone = !future && HabitRules.evaluate(habit, r.inputsOn(d)).done
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DaySquare(
                        when {
                            isDone -> tone.strong
                            future -> Kairos.colors.track.copy(alpha = 0.4f)
                            else -> Kairos.colors.track
                        },
                        size = 18.dp,
                        modifier = Modifier.semantics { contentDescription = if (isDone) doneLabel else notLabel },
                    )
                }
            }
        }
    }
}

@Composable
private fun RateList(r: ArcRange, totals: Totals) {
    val n = totals.days.size
    if (n == 0) { Text(stringResource(R.string.wa_no_data), color = Kairos.colors.muted); return }
    r.habits.filter { it.active }.sortedBy { it.sortOrder }.forEach { habit ->
        val tone = Arc.tone(habit)
        val done = totals.doneDays(habit.id)
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            HabitBadge(habit.iconVector(), tone, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(habit.shortName(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$done / $n", style = MaterialTheme.typography.labelMedium, color = Kairos.colors.muted)
                }
                Spacer(Modifier.height(4.dp))
                ArcBar(done.toFloat() / n, tone.strong, height = 6.dp, track = tone.soft)
            }
        }
    }
}

/** All 90 days at a glance with streaks and averages; tap a day to open it. */
@Composable
fun Arc90Screen(vm: ArcViewModel, onBack: (() -> Unit)?, onOpenDay: (LocalDate) -> Unit) {
    val today by vm.today.collectAsStateWithLifecycle()
    val arc by vm.arc.collectAsStateWithLifecycle()
    val a = arc
    val from = a?.startDate ?: today
    val to = a?.endDate(today) ?: today
    val range by remember(from, to) { vm.range(from, to) }.collectAsStateWithLifecycle(initialValue = null)
    val fmt = rememberMediumDateFormatter()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.wa_90_title), onBack = onBack)
        val r = range
        if (a == null || r == null) { LoadingState(); return@Column }
        val dates = remember(r) { a.countingDates(to, today) }
        val elapsed = dates.filter { !it.isAfter(today) }
        val complete = { d: LocalDate -> HabitRules.isComplete(r.summary(d)) }
        val streaks = remember(r) { ArcStats.streaks(elapsed, complete, today) }
        val completedDays = remember(r) { elapsed.count(complete) }
        val avg = remember(r) { if (elapsed.isEmpty()) 0 else (elapsed.sumOf { r.summary(it).fraction.toDouble() } / elapsed.size * 100).roundToInt() }
        val remaining = (a.durationDays - elapsed.size).coerceAtLeast(0)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally).widthIn(max = ContentMaxWidth),
        ) {
            ArcCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                val perRow = 10
                val slots = (1..a.durationDays).toList()
                slots.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { n ->
                            val d = dates.getOrNull(n - 1)
                            val state = d?.let { r.state(it) } ?: DayState.FUTURE
                            val label = d?.let { stringResource(R.string.wa_day_short, n, a.durationDays) + ", " + fmt(it) } ?: ""
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                DaySquare(
                                    if (state == DayState.FUTURE || state == DayState.OUTSIDE) Kairos.colors.track else Arc.dayColor(state),
                                    size = 30.dp,
                                    ring = if (d == today) Arc.primary else null,
                                    modifier = Modifier
                                        .then(if (d != null && !d.isAfter(today)) Modifier.clickable(role = Role.Button) { onOpenDay(d) } else Modifier)
                                        .semantics { contentDescription = label },
                                )
                                Text(
                                    n.toString(), style = MaterialTheme.typography.labelSmall,
                                    color = if (state == DayState.COMPLETE || state == DayState.MISSED || state == DayState.TODAY) androidx.compose.ui.graphics.Color.White else Kairos.colors.muted,
                                )
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendItem(Arc.dayColor(DayState.COMPLETE), stringResource(R.string.wa_legend_complete))
                    LegendItem(Arc.dayColor(DayState.PARTIAL), stringResource(R.string.wa_legend_partial))
                    LegendItem(Arc.dayColor(DayState.MISSED), stringResource(R.string.wa_legend_missed))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcStat(stringResource(R.string.wa_days_value, streaks.current), stringResource(R.string.wa_current_streak), Arc.tone(HabitKind.STUDY), Modifier.weight(1f))
                ArcStat(stringResource(R.string.wa_days_value, streaks.longest), stringResource(R.string.wa_longest_streak), Arc.tone(HabitKind.READING), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcStat("$completedDays / ${a.durationDays}", stringResource(R.string.wa_days_completed), Arc.tone(HabitKind.STEPS), Modifier.weight(1f))
                ArcStat(remaining.toString(), stringResource(R.string.wa_days_remaining), Arc.tone(HabitKind.WATER), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            ArcStat(stringResource(R.string.wa_percent, avg), stringResource(R.string.wa_avg_completion), Arc.tone(HabitKind.DEEP_WORK), Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.wa_complete_rule, HabitRules.completeThreshold(r.habits.count { it.active }), r.habits.count { it.active }), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** Coach scores at a glance; shown once at least one speech has been analysed. */
@Composable
private fun SpeechProgressCard(onOpen: () -> Unit) {
    val vm = com.kairosera.ui.kairosViewModel("speech_reports") { com.kairosera.feature.speech.SpeechReportViewModel(it.speechSessions) }
    val sessions by vm.all.collectAsStateWithLifecycle(initialValue = emptyList())
    if (sessions.isEmpty()) return
    val p = remember(sessions) { com.kairosera.feature.speech.SpeechProgress.of(sessions) }
    Spacer(Modifier.height(12.dp))
    val tone = Arc.tone(HabitKind.SPEAK)
    ArcCard(Modifier.fillMaxWidth(), color = tone.soft, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sc_progress_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.sc_stats_card_summary, p.count, p.average?.let(ScoreFormat::one) ?: "—"),
                    style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}
