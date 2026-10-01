package com.kairosera.feature.winterarc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.currentLocale
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.winterarc.HabitDay
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.HabitType
import java.time.LocalDate

/** Every habit as a row with its target, today's progress and a one-tap control. */
@Composable
fun ArcHabitsScreen(
    vm: ArcViewModel,
    initialDate: LocalDate?,
    onBack: (() -> Unit)?,
    onOpenStudy: (LocalDate) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenSpeak: () -> Unit,
) {
    val today by vm.today.collectAsStateWithLifecycle()
    var date by rememberSaveable { mutableStateOf((initialDate ?: today).toEpochDay()) }
    val selected = LocalDate.ofEpochDay(date)
    val day by remember(date) { vm.day(selected) }.collectAsStateWithLifecycle(initialValue = null)
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val book by vm.book.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.wa_nav_habits), onBack = onBack) {
            DateMenu(selected, today, onPick = { date = it.toEpochDay() })
        }
        val d = day
        if (d == null) { LoadingState(); return@Column }
        LazyColumn(
            Modifier.fillMaxSize().align(Alignment.CenterHorizontally).widthIn(max = ContentMaxWidth),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val s = d.summary
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.wa_completed_of, s.completedHabits, s.totalHabits), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.wa_percent, s.completionPercentage), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                }
                ArcBar(s.fraction, Arc.primary, Modifier.padding(horizontal = 4.dp))
            }
            items(d.summary.habits, key = { it.habitId }) { h ->
                HabitRow(
                    h, d, book?.title,
                    onOpen = {
                        when (h.kind) {
                            HabitKind.STUDY -> onOpenStudy(selected)
                            HabitKind.SPEAK -> if (selected == today) onOpenSpeak() else sheet = h.habitId
                            else -> sheet = h.habitId
                        }
                    },
                    onToggle = { vm.toggle(h, selected) },
                    onAddOne = { vm.addCustomValue(h.habitId, selected, customStep(h.target)) },
                    onWater = { vm.addWater(selected, 500) },
                    onFocus = onOpenFocus,
                    onSpeak = onOpenSpeak,
                    onWakeNow = { vm.setWake(selected, java.time.LocalTime.now().withSecond(0).withNano(0)) },
                )
            }
            item(key = "add") {
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Arc.primary),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.wa_add_habit))
                }
            }
        }
    }
    sheet?.let { id -> HabitSheet(habitId = id, date = selected, vm = vm, onDismiss = { sheet = null }, onOpenFocus = onOpenFocus) }
    if (adding) CustomHabitDialog(existing = null, onDismiss = { adding = false }, onSave = { vm.addCustomHabit(it); adding = false })
}

@Composable
private fun DateMenu(selected: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val fmt = rememberMediumDateFormatter()
    val label = when (selected) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> fmt(selected)
    }
    Box {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(12.dp), color = Arc.primarySoft, contentColor = Arc.primary) {
            Row(Modifier.heightIn(min = 40.dp).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Outlined.ExpandMore, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.today)) }, onClick = { open = false; onPick(today) })
            DropdownMenuItem(text = { Text(stringResource(R.string.yesterday)) }, onClick = { open = false; onPick(today.minusDays(1)) })
            DropdownMenuItem(text = { Text(stringResource(R.string.pick_date)) }, onClick = { open = false; picking = true })
        }
    }
    if (picking) {
        KDatePickerDialog(initial = selected, onDismiss = { picking = false }, onPick = { picking = false; onPick(if (it.isAfter(today)) today else it) })
    }
}

@Composable
private fun HabitRow(
    h: HabitDay,
    day: ArcDay,
    bookTitle: String?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onWater: () -> Unit,
    onFocus: () -> Unit,
    onSpeak: () -> Unit,
    onWakeNow: () -> Unit,
    onAddOne: () -> Unit,
) {
    val tone = Arc.tone(h)
    val locale = currentLocale()
    val label = h.longName()
    val subtitle = when (h.kind) {
        HabitKind.WATER -> stringResource(R.string.wa_sub_water, ArcFormat.litres(day.target(h.kind), locale) + " L")
        HabitKind.STEPS -> stringResource(R.string.wa_sub_steps, ArcFormat.count(day.target(h.kind).toInt(), locale))
        HabitKind.READING -> bookTitle ?: com.kairosera.data.winterarc.WinterArcRepository.DEFAULT_BOOK_TITLE
        HabitKind.STUDY -> stringResource(R.string.wa_sub_study)
        HabitKind.DEEP_WORK -> stringResource(R.string.wa_sub_deep_work)
        HabitKind.WAKE_EARLY -> stringResource(R.string.wa_sub_wake)
        HabitKind.SPEAK -> stringResource(R.string.wa_sub_speak)
        HabitKind.CUSTOM -> stringResource(R.string.wa_sub_custom)
        else -> stringResource(R.string.wa_sub_daily)
    }
    ArcCard(Modifier.fillMaxWidth(), color = tone.soft.copy(alpha = if (Arc.isDark) 1f else 0.7f), onClick = onOpen, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitBadge(h.iconVector(), tone, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (h.type == HabitType.AMOUNT) {
                    Spacer(Modifier.height(6.dp))
                    ArcBar(h.progress, tone.strong, height = 5.dp, track = tone.strong.copy(alpha = 0.15f))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(habitValue(h, day), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tone.strong, maxLines = 1)
            if (!day.editable) return@Row
            when {
                h.kind == HabitKind.WATER -> RoundAction("+500", tone, onWater)
                h.kind == HabitKind.DEEP_WORK -> PlayAction(tone, stringResource(R.string.wa_start_focus), onFocus)
                h.kind == HabitKind.SPEAK && day.date == day.today && !h.done -> PlayAction(tone, stringResource(R.string.wa_speak_start), onSpeak)
                h.kind == HabitKind.WAKE_EARLY && !h.logged && day.date == day.today -> RoundAction("⏰", tone, onWakeNow)
                h.kind == HabitKind.CUSTOM && h.type == HabitType.AMOUNT -> RoundAction("+" + plainNumber(customStep(h.target), locale), tone, onAddOne)
                h.type == HabitType.CHECK && h.kind != HabitKind.SPEAK -> CheckDot(h.done, tone, label, onToggle)
                else -> CheckDot(h.done, tone, label, null)
            }
        }
    }
}

@Composable
private fun RoundAction(text: String, tone: com.kairosera.core.ui.theme.Tone, onClick: () -> Unit) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = tone.strong, contentColor = Arc.onPrimary) {
            Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun PlayAction(tone: com.kairosera.core.ui.theme.Tone, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = tone.strong, contentColor = Arc.onPrimary)) {
        Icon(Icons.Outlined.PlayArrow, contentDescription = description)
    }
}

