package com.kairosera.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.domain.activity.ActivityItem
import com.kairosera.domain.activity.ActivityType
import com.kairosera.domain.activity.DayDetail
import com.kairosera.feature.journal.MoodIcon
import com.kairosera.feature.journal.moodLabel
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One day as a personal timeline: the plan, then what happened in the order it happened, then the reflection. */
@Composable
fun DayDetailScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onOpenTask: (Long, LocalDate) -> Unit,
    onOpenJournal: (LocalDate) -> Unit,
    onOpenToday: (LocalDate) -> Unit,
) {
    val vm = kairosViewModel(key = "day-$date") { DayDetailViewModel(it, date) }
    val state by vm.state.collectAsStateWithLifecycle()
    val titleFmt = rememberDateFormatter("d MMMM yyyy")
    val dayFmt = rememberDateFormatter("EEEE")
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(titleFmt(date), subtitle = dayFmt(date), onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                when (val s = state) {
                    UiState.Loading -> LoadingState(Modifier.height(320.dp))
                    is UiState.Error -> HistoryError(vm::retry)
                    is UiState.Ready -> DayBody(s.data.first, s.data.second, onOpenTask, onOpenJournal, onOpenToday)
                }
            }
        }
    }
}

@Composable
private fun DayBody(
    day: DayDetail,
    today: LocalDate,
    onOpenTask: (Long, LocalDate) -> Unit,
    onOpenJournal: (LocalDate) -> Unit,
    onOpenToday: (LocalDate) -> Unit,
) {
    if (day.occurrences.isNotEmpty()) {
        Column {
            SectionLabel(stringResource(R.string.day_plan))
            Spacer(Modifier.height(10.dp))
            KCard(padding = 6.dp) {
                day.occurrences.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenTask(o.task.id, o.date) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (o.isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null,
                            tint = if (o.isDone) Kairos.colors.accentText else Kairos.colors.muted, modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            o.task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (o.isSkipped) Kairos.colors.muted else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (o.isSkipped) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
        }
    }

    val timeline = day.items.filter { it.type != ActivityType.JOURNAL_ENTRY }
    Column {
        SectionLabel(stringResource(R.string.day_timeline))
        Spacer(Modifier.height(12.dp))
        if (timeline.isEmpty()) {
            Text(
                stringResource(if (day.date.isAfter(today)) R.string.calendar_nothing_planned else R.string.calendar_quiet_day),
                style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted,
            )
        } else {
            Timeline(day.date, timeline)
        }
    }

    day.journal?.let { entry ->
        Column {
            SectionLabel(stringResource(R.string.calendar_reflection))
            Spacer(Modifier.height(10.dp))
            KCard(onClick = { onOpenJournal(day.date) }) {
                entry.mood?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoodIcon(it, Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(moodLabel(it), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                val ex = entry.excerpt(400)
                if (ex.isNotBlank()) Text("“$ex”", fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 26.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.calendar_read_journal), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.accentText)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onOpenToday(day.date) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Today, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.calendar_open_day))
        }
        if (day.journal == null && !day.date.isAfter(today)) {
            TextButton(onClick = { onOpenJournal(day.date) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.calendar_write), color = Kairos.colors.accentText)
            }
        }
    }
}

/** Timed items first, in the order they happened; anything without a time on this day follows under "During the day". */
@Composable
private fun Timeline(date: LocalDate, items: List<ActivityItem>) {
    val zone = ZoneId.systemDefault()
    val timeFmt = rememberTimeFormatter()
    val number = rememberNumberText()
    fun timeOf(i: ActivityItem): LocalTime? = i.completedAt?.atZone(zone)?.takeIf { it.toLocalDate() == date }?.toLocalTime()
    val timed = items.mapNotNull { i -> timeOf(i)?.let { it to i } }.sortedBy { it.first }
    val untimed = items.filter { timeOf(it) == null }
    val rows = timed.map { timeFmt(it.first) to it.second } + untimed.mapIndexed { i, it -> (if (i == 0) stringResource(R.string.day_during) else "") to it }
    Column {
        rows.forEachIndexed { index, (time, item) ->
            val t = itemText(item, number)
            val last = index == rows.lastIndex
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) {}) {
                Text(time, style = MaterialTheme.typography.labelMedium, color = Kairos.colors.muted, modifier = Modifier.width(64.dp).padding(top = 2.dp), maxLines = 2)
                Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (item.type == ActivityType.TASK_COMPLETED) Kairos.colors.accent else Kairos.colors.calm))
                    if (!last) Box(Modifier.width(1.5.dp).weight(1f).background(Kairos.colors.line))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        t.emoji?.let { Text(it, fontSize = 16.sp); Spacer(Modifier.width(6.dp)) }
                        Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (t.detail.isNotBlank()) Text(t.detail, style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
                }
            }
        }
    }
}
