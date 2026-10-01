package com.kairosera.feature.winterarc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.currentLocale
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.winterarc.DayState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@OptIn(ExperimentalLayoutApi::class)
/** Month view coloured by how each day went, with the selected day's details underneath. */
@Composable
fun ArcCalendarScreen(vm: ArcViewModel, initialDate: LocalDate?, onBack: (() -> Unit)?, onEditDay: (LocalDate) -> Unit) {
    val today by vm.today.collectAsStateWithLifecycle()
    var selectedEpoch by rememberSaveable { mutableLongStateOf((initialDate ?: today).toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    var monthEpoch by rememberSaveable { mutableLongStateOf(selected.withDayOfMonth(1).toEpochDay()) }
    val month = YearMonth.from(LocalDate.ofEpochDay(monthEpoch))
    val range by remember(month) { vm.range(month.atDay(1), month.atEndOfMonth()) }.collectAsStateWithLifecycle(initialValue = null)
    val day by remember(selectedEpoch) { vm.day(selected) }.collectAsStateWithLifecycle(initialValue = null)
    val locale = currentLocale()
    val monthFmt = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    val dateFmt = rememberMediumDateFormatter()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.wa_calendar), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally).widthIn(max = ContentMaxWidth),
        ) {
            ArcCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthEpoch = month.minusMonths(1).atDay(1).toEpochDay() }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous))
                    }
                    Text(monthFmt.format(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { monthEpoch = month.plusMonths(1).atDay(1).toEpochDay() }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.next))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    DayOfWeek.entries.forEach { d ->
                        Text(
                            d.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelMedium, color = Kairos.colors.muted,
                            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val lead = month.atDay(1).dayOfWeek.value - 1
                val cells = lead + month.lengthOfMonth()
                val rows = (cells + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val n = r * 7 + col - lead + 1
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
                                if (n in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(n)
                                    val state = range?.state(date) ?: DayState.OUTSIDE
                                    DayCell(date, state, date == today, date == selected, dateFmt(date)) { selectedEpoch = date.toEpochDay() }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    LegendItem(Arc.dayColor(DayState.COMPLETE), stringResource(R.string.wa_legend_complete))
                    LegendItem(Arc.dayColor(DayState.PARTIAL), stringResource(R.string.wa_legend_partial))
                    LegendItem(Arc.dayColor(DayState.MISSED), stringResource(R.string.wa_legend_missed))
                    LegendItem(Arc.dayColor(DayState.TODAY), stringResource(R.string.wa_legend_today))
                    LegendItem(Arc.dayColor(DayState.PAUSED), stringResource(R.string.wa_legend_paused))
                }
            }
            Spacer(Modifier.height(16.dp))
            val d = day
            ArcSectionTitle(
                if (selected == today) stringResource(R.string.wa_todays_progress) else stringResource(R.string.wa_day_progress, dateFmt(selected)),
                trailing = d?.let { stringResource(R.string.wa_percent, it.summary.completionPercentage) },
            )
            if (d != null) {
                val inArc = d.arc?.counts(selected, today) == true || (d.arc != null && selected.isAfter(today) && !selected.isBefore(d.arc.startDate))
                if (!inArc) {
                    Text(stringResource(R.string.wa_outside_arc), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (selected.isAfter(today)) {
                    Text(stringResource(R.string.wa_future_locked), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
                } else {
                    ArcCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                        d.summary.habits.forEach { h ->
                            val tone = Arc.tone(h)
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                HabitBadge(h.iconVector(), tone, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(h.longName(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(habitValue(h, d), style = MaterialTheme.typography.labelLarge, color = if (h.done) tone.strong else Kairos.colors.muted, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                Text(if (h.done) "✓" else "—", color = if (h.done) tone.strong else Kairos.colors.muted, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.wa_completed_of, d.summary.completedHabits, d.summary.totalHabits), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    ArcButton(stringResource(R.string.wa_edit_day), { onEditDay(selected) }, icon = Icons.Outlined.Edit)
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, state: DayState, isToday: Boolean, isSelected: Boolean, a11yDate: String, onClick: () -> Unit) {
    val bg = when (state) {
        DayState.OUTSIDE, DayState.FUTURE -> Color.Transparent
        else -> Arc.dayColor(state)
    }
    val strong = state == DayState.COMPLETE || state == DayState.MISSED || state == DayState.TODAY
    val fg = when {
        bg == Color.Transparent -> MaterialTheme.colorScheme.onSurface
        strong -> Color.White
        else -> Color(0xFF1B1B1B)
    }
    val stateLabel = when (state) {
        DayState.COMPLETE -> stringResource(R.string.wa_legend_complete)
        DayState.PARTIAL -> stringResource(R.string.wa_legend_partial)
        DayState.MISSED -> stringResource(R.string.wa_legend_missed)
        DayState.PAUSED -> stringResource(R.string.wa_legend_paused)
        DayState.TODAY -> stringResource(R.string.wa_legend_today)
        else -> ""
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxSize().clip(shape)
            .then(if (bg != Color.Transparent) Modifier.background(bg, shape) else Modifier)
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else if (isToday && state != DayState.TODAY) Modifier.border(2.dp, Arc.primary, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (stateLabel.isEmpty()) a11yDate else "$a11yDate, $stateLabel"
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
    }
}
