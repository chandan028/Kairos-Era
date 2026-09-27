package com.kairosera.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.Hairline
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.domain.activity.ActivityType
import com.kairosera.domain.activity.DayDetail
import com.kairosera.domain.stats.StatsSummary
import com.kairosera.feature.journal.MoodIcon
import com.kairosera.feature.journal.moodLabel
import com.kairosera.ui.kairosViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * "What have I actually done with my days?" A month of days, each with a quiet mark for how much
 * happened and a note mark for days with a reflection. Tap a day for its story.
 */
@Composable
fun LifeCalendarScreen(
    initial: LocalDate?,
    onBack: () -> Unit,
    onViewDay: (LocalDate) -> Unit,
    onOpenJournal: (LocalDate) -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenJournalList: () -> Unit,
) {
    val vm = kairosViewModel(key = "calendar-$initial") { LifeCalendarViewModel(it, initial) }
    val state by vm.state.collectAsStateWithLifecycle()
    val yearState by vm.yearState.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val year by vm.year.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val yearMode = mode == LifeCalendarViewModel.Mode.YEAR
    val monthFmt = rememberDateFormatter("MMMM yyyy")
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.life_calendar), onBack = onBack) {
            OverflowMenu(
                listOf(
                    if (yearMode) MenuAction(stringResource(R.string.calendar_view_month), Icons.Outlined.CalendarMonth, vm::showMonth)
                    else MenuAction(stringResource(R.string.calendar_view_year), Icons.Outlined.CalendarViewMonth, vm::showYear),
                    MenuAction(stringResource(R.string.statistics), Icons.Outlined.BarChart, onOpenStatistics),
                    MenuAction(stringResource(R.string.journal), Icons.Outlined.EditNote, onOpenJournalList),
                ),
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 112.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (yearMode) year.value.toString() else monthFmt(month.atDay(1)),
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = { if (yearMode) vm.shiftYear(-1) else vm.shiftMonth(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(if (yearMode) R.string.previous_year else R.string.previous_month))
                    }
                    IconButton(onClick = { if (yearMode) vm.shiftYear(1) else vm.shiftMonth(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(if (yearMode) R.string.next_year else R.string.next_month))
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (yearMode) {
                    when (val s = yearState) {
                        UiState.Loading -> LoadingState(Modifier.height(360.dp))
                        is UiState.Error -> HistoryError(vm::retry)
                        is UiState.Ready -> YearView(s.data, onOpenMonth = vm::openMonth)
                    }
                } else {
                    when (val s = state) {
                        UiState.Loading -> LoadingState(Modifier.height(360.dp))
                        is UiState.Error -> HistoryError(vm::retry)
                        is UiState.Ready -> {
                            MonthGrid(s.data, vm::select)
                            Spacer(Modifier.height(16.dp))
                            s.data.selectedDay?.let { DayStory(it, s.data.today, onViewDay, onOpenJournal) }
                            Spacer(Modifier.height(24.dp))
                            Hairline()
                            Spacer(Modifier.height(20.dp))
                            MonthSummary(s.data.month, s.data.today, s.data.summary)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = vm::showYear, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.calendar_view_year).uppercase(), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.accentText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(d: CalendarData, onSelect: (LocalDate) -> Unit) {
    val weekdayFmt = rememberDateFormatter("EEE")
    val dayA11y = rememberDateFormatter("EEEE d MMMM")
    val first = d.month.atDay(1)
    val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = List(lead) { null } + (1..d.month.lengthOfMonth()).map { d.month.atDay(it) }
    val monday = first.minusDays(lead.toLong())
    val reflection = stringResource(R.string.calendar_has_reflection)
    Column {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            (0 until 7).forEach { i ->
                Text(
                    weekdayFmt(monday.plusDays(i.toLong())).uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = Kairos.colors.muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(0.86f).padding(2.dp)) {
                        if (date != null) DayCell(d, date, dayA11y(date), reflection, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: CalendarData, date: LocalDate, a11yDate: String, reflection: String, onSelect: (LocalDate) -> Unit) {
    val a = d.history.day(date)?.activity
    val future = date.isAfter(d.today)
    val selected = date == d.selected
    val today = date == d.today
    val level = if (future) 0 else a?.level ?: 0
    val shape = RoundedCornerShape(12.dp)
    val actions = stringResource(R.string.stats_day_a11y, a11yDate, if (future) 0 else a?.actions ?: 0)
    val label = if (a?.journaled == true) "$actions, $reflection" else actions
    Column(
        Modifier.fillMaxSize().clip(shape)
            .background(if (selected) Kairos.colors.accentSoft else Color.Transparent)
            .then(if (today) Modifier.border(1.5.dp, Kairos.colors.accent, shape) else Modifier)
            .clickable { onSelect(date) }
            .semantics(mergeDescendants = true) { contentDescription = label; this.selected = selected; role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (today || selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                selected -> MaterialTheme.colorScheme.onSurface
                future || level == 0 -> Kairos.colors.muted
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.height(3.dp))
        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(level) { Dot(if (level == 3) Kairos.colors.accent else Kairos.colors.calm, 4) }
            if (a?.journaled == true) {
                if (level > 0) Spacer(Modifier.width(1.dp))
                Icon(Icons.Outlined.EditNote, contentDescription = null, tint = Kairos.colors.accentText, modifier = Modifier.size(9.dp))
            }
        }
    }
}

@Composable
private fun MonthSummary(month: YearMonth, today: LocalDate, s: StatsSummary?) {
    val monthName = rememberDateFormatter("MMMM")(month.atDay(1))
    val current = YearMonth.from(today) == month
    SectionLabel(if (current) stringResource(R.string.calendar_so_far, monthName) else rememberDateFormatter("MMMM yyyy")(month.atDay(1)))
    Spacer(Modifier.height(10.dp))
    if (s == null) {
        Text(stringResource(R.string.calendar_future_month), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
        return
    }
    if (!s.hasActivity) {
        Text(stringResource(R.string.calendar_quiet_month), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
        return
    }
    Text(pluralStringResource(R.plurals.active_days, s.activeDays, s.activeDays), fontFamily = SerifFamily, fontSize = 26.sp, lineHeight = 32.sp)
    Spacer(Modifier.height(10.dp))
    val metrics = listOfNotNull(
        s.tasksDone.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.tasks_completed, it, it) },
        s.studySessions.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.study_sessions, it, it) },
        s.studyTopics.takeIf { it > 0 && s.studySessions == 0 }?.let { pluralStringResource(R.plurals.study_topics, it, it) },
        s.readingSessions.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.reading_sessions, it, it) },
        s.workouts.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.workouts, it, it) },
        s.reflections.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.reflections, it, it) },
    )
    metrics.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            row.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** The selected day's story: what was done, in a few lines, and what was written about it. */
@Composable
private fun DayStory(day: DayDetail, today: LocalDate, onViewDay: (LocalDate) -> Unit, onOpenJournal: (LocalDate) -> Unit) {
    val dateFmt = rememberDateFormatter("EEEE, d MMMM")
    val number = rememberNumberText()
    val future = day.date.isAfter(today)
    val a = day.activity
    KCard(padding = 20.dp) {
        SectionLabel(dateFmt(day.date), color = Kairos.colors.accentText)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                when {
                    future -> R.string.calendar_planned
                    day.date == today -> R.string.calendar_todays_story
                    else -> R.string.calendar_days_story
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(14.dp))
        val lines = storyLines(day, future, number)
        if (lines.isEmpty()) {
            Text(
                stringResource(if (future) R.string.calendar_nothing_planned else R.string.calendar_quiet_day),
                style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted,
            )
        }
        lines.forEach { (lead, text) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.widthIn(min = 30.dp).padding(end = 6.dp), contentAlignment = Alignment.CenterStart) {
                    when (lead) {
                        is String -> Text(lead, fontSize = 17.sp)
                        is androidx.compose.ui.graphics.vector.ImageVector -> Icon(lead, contentDescription = null, tint = Kairos.colors.accentText, modifier = Modifier.size(20.dp))
                    }
                }
                Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        day.journal?.let { entry ->
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.calendar_reflection), modifier = Modifier.weight(1f))
                entry.mood?.let { MoodIcon(it, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(moodLabel(it), style = MaterialTheme.typography.labelMedium, color = Kairos.colors.muted) }
            }
            val ex = entry.excerpt(180)
            if (ex.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("“$ex”", fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 25.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onViewDay(day.date) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.calendar_view_full_day))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            if (!future) {
                TextButton(onClick = { onOpenJournal(day.date) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (day.journal != null) R.string.calendar_read_journal else R.string.calendar_write), color = Kairos.colors.accentText)
                }
            }
        }
    }
}

/** Summary lines for a day: counts for tasks and reflection, one line per tracker, reading and study item. */
@Composable
private fun storyLines(day: DayDetail, future: Boolean, number: (Double) -> String): List<Pair<Any, String>> {
    val a = day.activity
    val out = mutableListOf<Pair<Any, String>>()
    if (future) {
        if (a.tasksTotal > 0) out += Icons.Outlined.CheckCircle to pluralStringResource(R.plurals.tasks_planned, a.tasksTotal, a.tasksTotal)
        return out
    }
    if (a.tasksDone > 0) {
        val text = pluralStringResource(R.plurals.tasks_completed, a.tasksDone, a.tasksDone)
        out += Icons.Outlined.CheckCircle to if (a.tasksTotal > a.tasksDone) stringResource(R.string.calendar_of_planned, text, a.tasksTotal) else text
    }
    day.items.filter { it.type != ActivityType.TASK_COMPLETED && it.type != ActivityType.JOURNAL_ENTRY && it.type != ActivityType.STUDY_TOPIC }.forEach { item ->
        val t = itemText(item, number)
        out += (t.emoji ?: t.icon ?: Icons.Outlined.CheckCircle) to listOf(t.title, t.detail).filter { it.isNotBlank() }.joinToString(" · ")
    }
    if (a.studyTopics > 0) out += Icons.Outlined.CheckCircle to pluralStringResource(R.plurals.study_topics, a.studyTopics, a.studyTopics)
    if (a.journaled) out += Icons.Outlined.EditNote to pluralStringResource(R.plurals.reflections, 1, 1)
    return out
}

@Composable
private fun YearView(d: YearData, onOpenMonth: (YearMonth) -> Unit) {
    val monthFmt = rememberDateFormatter("MMMM")
    val shortMonth = rememberDateFormatter("MMM")
    Text(pluralStringResource(R.plurals.active_days_in_year, d.activeDays, d.activeDays, d.year.value), style = MaterialTheme.typography.bodyLarge, color = Kairos.colors.muted)
    Spacer(Modifier.height(16.dp))
    (1..12).chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { m ->
                val ym = d.year.atMonth(m)
                val active = (1..ym.lengthOfMonth()).count { d.days[ym.atDay(it)]?.isActive == true }
                val label = "${monthFmt(ym.atDay(1))}, ${pluralStringResource(R.plurals.active_days, active, active)}"
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onOpenMonth(ym) }.padding(4.dp)
                        .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button },
                ) {
                    Text(shortMonth(ym.atDay(1)), style = MaterialTheme.typography.labelLarge, color = if (active > 0) MaterialTheme.colorScheme.onSurface else Kairos.colors.muted)
                    Spacer(Modifier.height(6.dp))
                    MiniMonth(ym, d)
                }
            }
        }
    }
    HeatLegend(Modifier.padding(top = 4.dp))
}

@Composable
private fun MiniMonth(ym: YearMonth, d: YearData) {
    val lead = ym.atDay(1).dayOfWeek.value - 1
    val cells = List(lead) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
    Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (0 until 7).forEach { i ->
                    val date = week.getOrNull(i)
                    val a = date?.let { d.days[it] }
                    val color = when {
                        date == null || date.isAfter(d.today) -> Color.Transparent
                        else -> Kairos.colors.heat[a?.level ?: 0]
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(color)
                            .then(if (date == d.today) Modifier.border(1.dp, Kairos.colors.accent, RoundedCornerShape(3.dp)) else Modifier),
                    )
                }
            }
        }
    }
}
