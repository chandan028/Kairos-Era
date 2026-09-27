package com.kairosera.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.KProgressBar
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.domain.stats.DayActivity
import com.kairosera.domain.stats.Momentum
import com.kairosera.domain.stats.PersonalRecords
import com.kairosera.domain.stats.StatsSummary
import com.kairosera.ui.kairosViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Am I actually making progress?" One honest number (consistency), where the activity happened,
 * each area the person really has, what got completed, a plain reading of the last two weeks and
 * their own bests. Everything is computed from the local records of the chosen range.
 */
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenArea: (AreaStat) -> Unit,
    onCreate: () -> Unit,
) {
    val vm = kairosViewModel(key = "statistics") { StatisticsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.statistics), onBack = onBack) {
            OverflowMenu(
                listOf(
                    MenuAction(stringResource(R.string.life_calendar), Icons.Outlined.CalendarMonth, onOpenCalendar),
                    MenuAction(stringResource(R.string.journal), Icons.Outlined.EditNote, onOpenJournal),
                ),
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 112.dp),
            ) {
                val data = (state as? UiState.Ready)?.data
                SectionLabel(rangeLong(range), color = Kairos.colors.accentText)
                Spacer(Modifier.height(2.dp))
                if (data != null) {
                    val fmt = rememberDateFormatter("d MMM yyyy")
                    val start = data.today.minusDays(range.days - 1L)
                    Text("${fmt(start)} – ${fmt(data.today)}", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(16.dp))
                RangeSelector(range, vm::setRange)
                Spacer(Modifier.height(24.dp))
                when (val s = state) {
                    UiState.Loading -> LoadingState(Modifier.height(320.dp))
                    is UiState.Error -> HistoryError(vm::retry)
                    is UiState.Ready -> if (s.data.isEmpty) EmptyStory(onCreate) else StatsBody(s.data, onOpenDay, onOpenArea)
                }
                Spacer(Modifier.height(24.dp))
                PrivacyFooter()
            }
        }
    }
}

@Composable
fun HistoryError(onRetry: () -> Unit) {
    MessageState(
        icon = Icons.Outlined.CalendarMonth,
        title = stringResource(R.string.history_error_title),
        body = stringResource(R.string.history_error_body),
        action = { androidx.compose.material3.OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) } },
    )
}

@Composable
private fun StatsBody(d: StatsData, onOpenDay: (LocalDate) -> Unit, onOpenArea: (AreaStat) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        ConsistencyCard(d.summary, d.previous)
        Column {
            SectionLabel(stringResource(R.string.stats_activity))
            Spacer(Modifier.height(12.dp))
            KCard(padding = 16.dp) {
                ActivityMap(d.days, d.today, onOpenDay)
                Spacer(Modifier.height(12.dp))
                HeatLegend(Modifier.align(Alignment.End))
            }
        }
        if (d.areas.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.stats_your_areas))
                Spacer(Modifier.height(2.dp))
                d.areas.forEach { AreaRow(it, onOpenArea) }
            }
        }
        CompletedGrid(d.summary)
        MomentumCard(d.momentum, d.summary.currentStreak)
        if (!d.records.isEmpty) Milestones(d.records, onOpenDay)
    }
}

@Composable
private fun ConsistencyCard(s: StatsSummary, previous: StatsSummary?) {
    val pct = ((s.consistency ?: 0.0) * 100).roundToInt()
    val a11y = stringResource(R.string.stats_consistency_a11y, pct, s.activeDays, s.daysCounted)
    KCard(padding = 24.dp, color = Kairos.colors.brand) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = a11y }, horizontalAlignment = Alignment.CenterHorizontally) {
            SectionLabel(stringResource(R.string.stats_consistency_title), color = Kairos.colors.onBrand.copy(alpha = 0.75f))
            Spacer(Modifier.height(10.dp))
            Text("$pct%", fontFamily = SerifFamily, fontSize = 60.sp, lineHeight = 64.sp, color = Kairos.colors.accent)
            Spacer(Modifier.height(14.dp))
            KProgressBar(pct / 100f, color = Kairos.colors.accent, track = Kairos.colors.onBrand.copy(alpha = 0.14f), height = 10.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.stats_active_of, s.activeDays, s.daysCounted),
                style = MaterialTheme.typography.titleMedium, color = Kairos.colors.onBrand, textAlign = TextAlign.Center,
            )
            val prevPct = previous?.consistency?.let { (it * 100).roundToInt() }
            if (prevPct != null && previous.daysCounted >= 3) {
                Spacer(Modifier.height(4.dp))
                val diff = pct - prevPct
                val (text, color) = when {
                    diff > 0 -> stringResource(R.string.stats_change_up, diff) to Color(0xFF9FD0AE) // soft green, readable on the navy card in both themes
                    diff < 0 -> stringResource(R.string.stats_change_down, abs(diff)) to Kairos.colors.onBrand.copy(alpha = 0.75f)
                    else -> stringResource(R.string.stats_change_same) to Kairos.colors.onBrand.copy(alpha = 0.75f)
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.stats_active_day_explain), style = MaterialTheme.typography.bodySmall,
                color = Kairos.colors.onBrand.copy(alpha = 0.6f), textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The range as a heat map. Up to five weeks it reads like a calendar (a row per week); longer
 * ranges turn sideways (a column per week) so a whole year still fits the screen.
 */
@Composable
private fun ActivityMap(days: List<DayActivity>, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    if (days.isEmpty()) return
    val byDate = days.associateBy { it.date }
    val first = days.first().date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = (ChronoUnit.DAYS.between(first, today) / 7 + 1).toInt()
    val weekday = rememberDateFormatter("EEEEE")
    val dayA11y = rememberDateFormatter("EEEE d MMMM")
    val heat = Kairos.colors.heat
    val accent = Kairos.colors.accent
    if (weeks <= 6) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                (0 until 7).forEach { i ->
                    Text(
                        weekday(first.plusDays(i.toLong())), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            (0 until weeks).forEach { w ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 7).forEach { i ->
                        val date = first.plusDays(w * 7L + i)
                        val a = byDate[date]
                        val label = stringResource(R.string.stats_day_a11y, dayA11y(date), a?.actions ?: 0)
                        Box(
                            Modifier.weight(1f).aspectRatio(1.6f).clip(RoundedCornerShape(6.dp))
                                .background(if (a == null) Color.Transparent else heat[a.level])
                                .then(if (date == today) Modifier.border(1.5.dp, accent, RoundedCornerShape(6.dp)) else Modifier)
                                .then(
                                    if (a != null) Modifier.clickable { onOpenDay(date) }.semantics { contentDescription = label; role = Role.Button } else Modifier,
                                ),
                        )
                    }
                }
            }
        }
    } else {
        val active = days.count { it.isActive }
        val summary = stringResource(R.string.stats_map_a11y, active, days.size)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val gap = 2.dp
            val cell = ((maxWidth - gap * (weeks - 1)) / weeks).coerceAtMost(22.dp)
            val height = cell * 7 + gap * 6
            Canvas(
                Modifier.fillMaxWidth().height(height).semantics { contentDescription = summary }
                    .pointerInput(days) {
                        detectTapGestures { o ->
                            val step = with(density) { (cell + gap).toPx() }
                            val w = (o.x / step).toInt()
                            val d = (o.y / step).toInt()
                            val date = first.plusDays(w * 7L + d)
                            if (byDate[date] != null) onOpenDay(date)
                        }
                    },
            ) {
                val c = cell.toPx()
                val g = gap.toPx()
                val r = CornerRadius(c * 0.22f)
                for (w in 0 until weeks) for (d in 0 until 7) {
                    val a = byDate[first.plusDays(w * 7L + d)] ?: continue
                    val tl = Offset(w * (c + g), d * (c + g))
                    drawRoundRect(heat[a.level], tl, Size(c, c), r)
                    if (a.date == today) drawRoundRect(accent, tl, Size(c, c), r, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun AreaRow(a: AreaStat, onOpen: (AreaStat) -> Unit) {
    val pct = (a.fraction * 100).roundToInt()
    val (title, detail) = when (a.kind) {
        AreaStat.Kind.TASKS -> stringResource(R.string.stats_area_tasks) to pluralStringResource(R.plurals.tasks_completed, a.count, a.count)
        AreaStat.Kind.TRACKER -> a.tracker!!.name to pluralStringResource(R.plurals.active_days, a.count, a.count)
        AreaStat.Kind.READING -> stringResource(R.string.stats_area_reading) to pluralStringResource(R.plurals.reading_days, a.count, a.count)
        AreaStat.Kind.JOURNAL -> stringResource(R.string.stats_area_journal) to pluralStringResource(R.plurals.reflections, a.count, a.count)
    }
    KCard(onClick = { onOpen(a) }, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AreaIcon(a)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
            }
            Text("$pct%", style = MaterialTheme.typography.titleMedium, color = Kairos.colors.accentText)
        }
        Spacer(Modifier.height(10.dp))
        KProgressBar(a.fraction.toFloat(), color = Kairos.colors.accent, height = 6.dp)
    }
}

@Composable
private fun AreaIcon(a: AreaStat) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Kairos.colors.accentSoft), contentAlignment = Alignment.Center) {
        when (a.kind) {
            AreaStat.Kind.TRACKER -> Text(a.tracker!!.icon, fontSize = 20.sp)
            else -> {
                val icon = when (a.kind) {
                    AreaStat.Kind.TASKS -> Icons.Outlined.CheckCircle
                    AreaStat.Kind.READING -> Icons.AutoMirrored.Outlined.MenuBook
                    else -> Icons.Outlined.EditNote
                }
                Icon(icon, contentDescription = null, tint = Kairos.colors.accentText, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CompletedGrid(s: StatsSummary) {
    val tiles = buildList {
        if (s.tasksDone > 0) add(s.tasksDone.toString() to stringResource(R.string.stats_tile_tasks))
        if (s.studySessions > 0) add(s.studySessions.toString() to stringResource(R.string.stats_tile_study))
        else if (s.studyTopics > 0) add(s.studyTopics.toString() to stringResource(R.string.stats_tile_topics))
        if (s.pages > 0) add(s.pages.toString() to stringResource(R.string.stats_tile_pages))
        if (s.activeMinutes > 0) add(s.activeMinutes.toString() to stringResource(R.string.stats_tile_active_minutes))
        else if (s.workouts > 0) add(s.workouts.toString() to stringResource(R.string.stats_tile_workouts))
        if (s.reflections > 0) add(s.reflections.toString() to stringResource(R.string.stats_tile_reflections))
    }
    if (tiles.isEmpty()) return
    Column {
        SectionLabel(stringResource(R.string.stats_completed))
        Spacer(Modifier.height(12.dp))
        KCard(padding = 0.dp) {
            tiles.chunked(2).forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Kairos.colors.line))
                Row(Modifier.height(IntrinsicSize.Min)) {
                    row.forEachIndexed { j, (value, label) ->
                        if (j > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Kairos.colors.line))
                        Column(Modifier.weight(1f).padding(18.dp).semantics(mergeDescendants = true) {}) {
                            Text(value, fontFamily = SerifFamily, fontSize = 28.sp, lineHeight = 34.sp)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MomentumCard(m: Momentum, streak: Int) {
    val text = when (m) {
        is Momentum.Rising -> stringResource(R.string.momentum_rising, m.lastWeek)
        is Momentum.Steady -> stringResource(R.string.momentum_steady, m.lastWeek)
        is Momentum.Easing -> stringResource(R.string.momentum_easing, m.lastWeek)
        is Momentum.JustStarted -> stringResource(R.string.momentum_started, m.activeDays, m.days)
        Momentum.Quiet -> stringResource(R.string.momentum_quiet)
    }
    Column {
        SectionLabel(stringResource(R.string.stats_momentum))
        Spacer(Modifier.height(12.dp))
        KCard(padding = 20.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, tint = Kairos.colors.accentText, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("“$text”", fontFamily = SerifFamily, fontSize = 18.sp, lineHeight = 26.sp)
                    if (streak >= 2) {
                        Spacer(Modifier.height(8.dp))
                        Text(pluralStringResource(R.plurals.days_in_a_row, streak, streak), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Milestones(r: PersonalRecords, onOpenDay: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.stats_milestones))
        Text(stringResource(R.string.stats_milestones_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        Spacer(Modifier.height(2.dp))
        KCard(padding = 4.dp) {
            val rows = listOfNotNull(
                r.longestStreak?.let { Triple(Icons.Outlined.LocalFireDepartment, R.string.record_streak, it) },
                r.mostTasksInDay?.let { Triple(Icons.Outlined.CheckCircle, R.string.record_tasks, it) },
                r.mostPagesInWeek?.let { Triple(Icons.AutoMirrored.Outlined.MenuBook, R.string.record_pages, it) },
                r.longestSession?.let { Triple(Icons.Outlined.Timer, R.string.record_session, it) },
            )
            rows.forEachIndexed { i, (icon, labelRes, rec) ->
                if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Kairos.colors.line))
                val label = stringResource(labelRes)
                val value = when (labelRes) {
                    R.string.record_streak -> pluralStringResource(R.plurals.day_count, rec.value, rec.value)
                    R.string.record_tasks -> pluralStringResource(R.plurals.task_count, rec.value, rec.value)
                    R.string.record_pages -> pagesText(rec.value)
                    else -> formatMinutes(rec.value)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpenDay(rec.date) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = Kairos.colors.accentText, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(value, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun EmptyStory(onCreate: () -> Unit) {
    KCard(padding = 28.dp, color = Kairos.colors.brand) {
        SectionLabel(stringResource(R.string.stats_empty_title), color = Kairos.colors.accent)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.stats_empty_body), fontFamily = SerifFamily, fontSize = 19.sp, lineHeight = 28.sp, color = Kairos.colors.onBrand)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onCreate,
            colors = ButtonDefaults.buttonColors(containerColor = Kairos.colors.accent, contentColor = Color(0xFF1A2233)),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.stats_empty_action) + "  →") }
    }
}
