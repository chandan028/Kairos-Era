package com.kairosera.feature.winterarc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.KairosLogo
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.currentLocale
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.domain.winterarc.HabitDay
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.HabitType
import com.kairosera.domain.winterarc.WakeTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Home in Winter Arc Mode: the hero, today's day and score, and the colorful habit grid. */
@Composable
fun ArcHomeScreen(
    vm: ArcViewModel,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStudy: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenSpeak: () -> Unit,
) {
    val day = vm.home.collectAsStateWithLifecycle().value
    if (day == null) { LoadingState(); return }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val locale = currentLocale()
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Hero(date = dateFmt.format(day.date), onOpenCalendar = onOpenCalendar, onOpenSettings = onOpenSettings)
            Column(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = ContentMaxWidth).fillMaxWidth().offset(y = (-44).dp).padding(horizontal = 16.dp),
            ) {
                DayCard(day)
                Spacer(Modifier.height(16.dp))
                HabitGrid(
                    day = day,
                    onOpen = { h ->
                        when (h.kind) {
                            HabitKind.STUDY -> onOpenStudy()
                            HabitKind.SPEAK -> onOpenSpeak()
                            HabitKind.DEEP_WORK -> onOpenFocus()
                            else -> sheet = h.habitId
                        }
                    },
                    onToggle = { h -> vm.toggle(h, day.date) },
                    onQuickWater = { vm.addWater(day.date, 500) },
                    onAdd = { adding = true },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.wa_complete_rule, com.kairosera.domain.winterarc.HabitRules.completeThreshold(day.summary.totalHabits), day.summary.totalHabits),
                    style = MaterialTheme.typography.bodySmall,
                    color = Kairos.colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(72.dp))
            }
        }
    }
    sheet?.let { id ->
        HabitSheet(habitId = id, date = day.date, vm = vm, onDismiss = { sheet = null }, onOpenFocus = onOpenFocus)
    }
    if (adding) CustomHabitDialog(existing = null, onDismiss = { adding = false }, onSave = { vm.addCustomHabit(it); adding = false })
}

@Composable
private fun Hero(date: String, onOpenCalendar: () -> Unit, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 330.dp)) {
        Image(
            painter = painterResource(R.drawable.arc_winter_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, 0.25f),
            modifier = Modifier.matchParentSize(),
        )
        // Polar night at the top for the header, the snowy valley in the middle, and a frosty fade into the page.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color(0xCC071426),
                    0.45f to Color(0x44071426),
                    0.8f to Color(0x1A071426),
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 64.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KairosLogo(Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onOpenCalendar) { Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.wa_calendar), tint = Color.White) }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.wa_settings), tint = Color.White) }
            }
            Spacer(Modifier.height(18.dp))
            Text(date, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(44.dp))
            Text(
                stringResource(R.string.wa_statement),
                fontFamily = SerifFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                color = Color.White,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

@Composable
private fun DayCard(day: ArcDay) {
    val arc = day.arc
    val s = day.summary
    ArcCard(Modifier.fillMaxWidth(), padding = 18.dp) {
        val title = when {
            arc != null && !arc.hasStarted(day.today) -> {
                val n = ChronoUnit.DAYS.between(day.today, arc.startDate).toInt()
                if (n == 1) stringResource(R.string.wa_starts_tomorrow) else stringResource(R.string.wa_starts_in, n)
            }
            else -> stringResource(R.string.wa_day_of, day.dayNumber, day.duration)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (arc?.isPaused == true) Icon(Icons.Outlined.PauseCircle, contentDescription = null, tint = Kairos.colors.muted)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcBar(s.fraction, if (s.fraction >= 0.8f) Arc.dayColor(com.kairosera.domain.winterarc.DayState.COMPLETE) else Arc.primary, Modifier.weight(1f), height = 10.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.wa_percent, s.completionPercentage), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.wa_completed_of, s.completedHabits, s.totalHabits), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        when {
            arc?.isPaused == true -> Note(stringResource(R.string.wa_paused_banner))
            arc != null && arc.isFinished(day.today) -> Note(stringResource(R.string.wa_finished_banner))
        }
    }
}

@Composable
private fun Note(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Arc.primary)
}

@Composable
private fun HabitGrid(day: ArcDay, onOpen: (HabitDay) -> Unit, onToggle: (HabitDay) -> Unit, onQuickWater: () -> Unit, onAdd: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 600.dp -> 5
            maxWidth >= 440.dp -> 4
            else -> 3
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The last cell is always "Add habit", so a person's own habits sit right beside the built-in ones.
            (day.summary.habits.map<HabitDay, HabitDay?> { it } + null).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    row.forEach { h ->
                        if (h == null) AddHabitTile(Modifier.weight(1f).fillMaxHeight(), onAdd)
                        else HabitTile(h, day, Modifier.weight(1f), onOpen = { onOpen(h) }, onToggle = { onToggle(h) }, onQuickWater = onQuickWater)
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HabitTile(h: HabitDay, day: ArcDay, modifier: Modifier, onOpen: () -> Unit, onToggle: () -> Unit, onQuickWater: () -> Unit) {
    val tone = Arc.tone(h)
    val label = h.shortName()
    val value = habitValue(h, day)
    Box(modifier) {
        ArcCard(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
            color = tone.soft,
            onClick = onOpen,
            padding = 12.dp,
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(4.dp))
                if (h.type == HabitType.AMOUNT && h.kind != HabitKind.STUDY && h.kind != HabitKind.DEEP_WORK && h.kind != HabitKind.READING) {
                    ProgressRing(h.progress, tone.strong, size = 44.dp, stroke = 4.dp, track = tone.strong.copy(alpha = 0.15f)) {
                        Icon(h.iconVector(), contentDescription = null, tint = tone.strong, modifier = Modifier.size(22.dp))
                    }
                } else {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(h.iconVector(), contentDescription = null, tint = tone.strong, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tone.strong, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(2.dp))
            }
        }
        // One-tap controls sit in the corner: a check for yes/no habits, +500 ml for water.
        if (day.editable) {
            when {
                h.type == HabitType.CHECK && h.kind != HabitKind.SPEAK ->
                    CheckDot(h.done, tone, label, onToggle, Modifier.align(Alignment.TopEnd), size = 22.dp)
                h.kind == HabitKind.WATER ->
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(androidx.compose.foundation.shape.CircleShape)) {
                        androidx.compose.material3.Surface(onClick = onQuickWater, color = tone.strong, contentColor = Arc.onPrimary, shape = androidx.compose.foundation.shape.CircleShape) {
                            Text(stringResource(R.string.wa_water_add, 500).replace(" ml", "").replace(" ಮಿಲೀ", ""), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp).semantics { contentDescription = "" })
                        }
                    }
            }
        }
    }
}

/** "1.5 / 3 L", "Not yet", "✓ 4:32 AM": the line under each habit's name. */
@Composable
fun habitValue(h: HabitDay, day: ArcDay): String {
    val locale = currentLocale()
    val time = rememberTimeFormatter()
    return when (h.kind) {
        HabitKind.WATER -> stringResource(R.string.wa_target_l, ArcFormat.litres(h.value, locale), ArcFormat.litres(h.target, locale))
        HabitKind.STEPS -> "${ArcFormat.compact(h.value.toInt())} / ${ArcFormat.compact(h.target.toInt())}"
        HabitKind.READING, HabitKind.DEEP_WORK -> stringResource(R.string.wa_target_min, h.value.toInt(), h.target.toInt())
        HabitKind.STUDY -> if (day.inputs.studyTasksTotal > 0) stringResource(R.string.wa_target_tasks, day.inputs.studyTasksDone, day.inputs.studyTasksTotal)
            else stringResource(R.string.wa_target_min, h.value.toInt(), h.target.toInt())
        HabitKind.WAKE_EARLY -> if (!h.logged) stringResource(R.string.wa_not_yet) else {
            val t = time(WakeTime.toTime(h.value.toInt()))
            stringResource(if (h.done) R.string.wa_wake_ok else R.string.wa_wake_late, t)
        }
        HabitKind.CUSTOM -> if (h.type == HabitType.AMOUNT) customAmount(h.value, h.target, h.habit.unit, locale)
            else stringResource(if (h.done) R.string.wa_done else R.string.wa_not_yet)
        else -> stringResource(if (h.done) R.string.wa_done else R.string.wa_not_yet)
    }
}

/** "12 / 50 reps" for a custom amount habit. */
fun customAmount(value: Double, target: Double, unit: String, locale: java.util.Locale): String {
    val nf = java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    return "${nf.format(value)} / ${nf.format(target)}" + if (unit.isNotBlank()) " $unit" else ""
}

/** The last tile of the grid: a quiet dashed "+" that opens the add-habit dialog. */
@Composable
private fun AddHabitTile(modifier: Modifier, onAdd: () -> Unit) {
    val label = stringResource(R.string.wa_add_habit)
    val stroke = Kairos.colors.muted.copy(alpha = 0.5f)
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .drawBehind {
                drawRoundRect(
                    color = stroke,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .clickable(onClickLabel = label, role = androidx.compose.ui.semantics.Role.Button, onClick = onAdd)
            .heightIn(min = 112.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = Arc.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Arc.primary, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
