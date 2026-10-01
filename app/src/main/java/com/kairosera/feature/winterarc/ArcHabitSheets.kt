package com.kairosera.feature.winterarc

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.KTimePickerDialog
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.currentLocale
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.HabitType
import com.kairosera.domain.winterarc.WakeTime
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/**
 * The detail tracker for one habit, as a bottom sheet over the screen it came from. Every
 * common action is one tap here; the sheet also shows the habit's last two weeks and streak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitSheet(habitId: String, date: LocalDate, vm: ArcViewModel, onDismiss: () -> Unit, onOpenFocus: () -> Unit) {
    val day by remember(date) { vm.day(date) }.collectAsStateWithLifecycle(initialValue = null)
    val streaks by vm.habitStreaks.collectAsStateWithLifecycle()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.background) {
        val d = day ?: return@ModalBottomSheet
        val h = d.habit(habitId) ?: return@ModalBottomSheet
        val kind = h.kind
        val tone = Arc.tone(h)
        var editing by rememberSaveable { mutableStateOf(false) }
        if (editing) {
            CustomHabitDialog(
                existing = h.habit,
                onDismiss = { editing = false },
                onSave = { vm.updateCustomHabit(habitId, it); editing = false },
                onDelete = { editing = false; vm.deleteCustomHabit(habitId); onDismiss() },
            )
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (kind == HabitKind.WATER || kind == HabitKind.STEPS || kind == HabitKind.READING || (h.habit.isCustom && h.type == HabitType.AMOUNT)) {
                    ProgressRing(h.progress, tone.strong, size = 56.dp, stroke = 5.dp, track = tone.soft) {
                        androidx.compose.material3.Icon(h.iconVector(), contentDescription = null, tint = tone.strong)
                    }
                } else HabitBadge(h.iconVector(), tone, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.longName(), style = MaterialTheme.typography.titleLarge)
                    Text(habitValue(h, d), style = MaterialTheme.typography.bodyLarge, color = tone.strong, fontWeight = FontWeight.SemiBold)
                }
                if (h.habit.isCustom) {
                    androidx.compose.material3.IconButton(onClick = { editing = true }) {
                        androidx.compose.material3.Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.wa_edit_habit_title), tint = Kairos.colors.muted)
                    }
                }
            }
            val streak = streaks[habitId] ?: 0
            if (streak > 0) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.wa_streak_days, streak), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
            }
            Spacer(Modifier.height(18.dp))
            if (!d.editable) {
                Text(stringResource(R.string.wa_future_locked), color = Kairos.colors.muted)
                return@Column
            }
            when (kind) {
                HabitKind.CUSTOM -> CustomControls(vm, date, h)
                HabitKind.WATER -> WaterControls(vm, date, h.value, h.target)
                HabitKind.STEPS -> StepsControls(vm, date, h.value.toInt(), h.target.toInt(), d.inputs.logs[kind.name]?.extra ?: 0.0, d.inputs.logs[kind.name]?.completed == true)
                HabitKind.READING -> ReadingControls(vm, date, d.target(kind).toInt(), d.inputs.readingMinutes)
                HabitKind.WAKE_EARLY -> WakeControls(vm, date, if (h.logged) h.value.toInt() else null)
                HabitKind.DEEP_WORK -> ArcButton(stringResource(R.string.wa_start_focus), { onDismiss(); onOpenFocus() }, icon = Icons.Outlined.PlayArrow, color = tone.strong)
                else -> {
                    Text(
                        stringResource(if (kind == HabitKind.DIGITAL_DETOX) R.string.wa_detox_hint else R.string.wa_binary_hint),
                        style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted,
                    )
                    Spacer(Modifier.height(14.dp))
                    ArcButton(
                        stringResource(if (h.done) R.string.wa_mark_not_done else R.string.wa_mark_done),
                        { vm.setChecked(kind, date, !h.done) },
                        color = if (h.done) Kairos.colors.track else tone.strong,
                        contentColor = if (h.done) MaterialTheme.colorScheme.onSurface else Arc.onPrimary,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            LastDays(vm, habitId, tone, date)
        }
    }
}

@Composable
private fun LastDays(vm: ArcViewModel, habitId: String, tone: com.kairosera.core.ui.theme.Tone, date: LocalDate) {
    val from = date.minusDays(13)
    val days = remember(date) { (0L..13L).map { from.plusDays(it) } }
    val states = days.map { d -> remember(d) { vm.day(d) }.collectAsStateWithLifecycle(initialValue = null).value }
    Text(stringResource(R.string.wa_last_14), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        states.forEachIndexed { i, s ->
            val done = s?.habit(habitId)?.done == true
            val logged = s?.habit(habitId)?.logged == true
            DaySquare(
                when {
                    done -> tone.strong
                    logged -> tone.strong.copy(alpha = 0.4f)
                    else -> Kairos.colors.track
                },
                Modifier.weight(1f),
                size = 18.dp,
                ring = if (days[i] == date) Arc.primary else null,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaterControls(vm: ArcViewModel, date: LocalDate, current: Double, target: Double) {
    val tone = Arc.tone(HabitKind.WATER)
    var last by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickChip(stringResource(R.string.wa_water_add, 250), tone, { vm.addWater(date, 250); last = 250 }, Modifier.weight(1f))
        QuickChip(stringResource(R.string.wa_water_add, 500), tone, { vm.addWater(date, 500); last = 500 }, Modifier.weight(1f), filled = true)
        QuickChip(stringResource(R.string.wa_water_add_l), tone, { vm.addWater(date, 1000); last = 1000 }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    val locale = currentLocale()
    Text(
        if (current >= target) stringResource(R.string.wa_water_goal_met) else stringResource(R.string.wa_water_left, ArcFormat.litres(target - current, locale)),
        style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted,
    )
    FlowRow {
        if (last > 0) TextButton(onClick = { vm.addWater(date, -last); last = 0 }) { Text(stringResource(R.string.wa_water_undo)) }
        var editing by remember { mutableStateOf(false) }
        TextButton(onClick = { editing = true }) { Text(stringResource(R.string.wa_water_set)) }
        if (editing) NumberDialog(stringResource(R.string.wa_water_set), current.toInt(), "ml", onDismiss = { editing = false }) { vm.setWater(date, it); editing = false }
    }
}

@Composable
private fun StepsControls(vm: ArcViewModel, date: LocalDate, steps: Int, target: Int, km: Double, ran: Boolean) {
    val tone = Arc.tone(HabitKind.STEPS)
    val locale = currentLocale()
    val remaining = (target - steps).coerceAtLeast(0)
    if (remaining > 0) Text(stringResource(R.string.wa_steps_remaining, ArcFormat.count(remaining, locale)), color = Kairos.colors.muted)
    Spacer(Modifier.height(10.dp))
    var editing by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickChip("+1,000", tone, { vm.addSteps(date, 1000) }, Modifier.weight(1f))
        QuickChip("+2,500", tone, { vm.addSteps(date, 2500) }, Modifier.weight(1f))
        QuickChip(stringResource(R.string.wa_steps_set), tone, { editing = true }, Modifier.weight(1f), filled = true)
    }
    if (editing) NumberDialog(stringResource(R.string.wa_steps_enter), steps, "", onDismiss = { editing = false }) { vm.setSteps(date, it); editing = false }
    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.wa_running), style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.wa_running_today), Modifier.weight(1f))
        Switch(checked = ran, onCheckedChange = { vm.setRunning(date, km, it) })
    }
    if (ran) {
        var kmText by rememberSaveable(km) { mutableStateOf(if (km > 0) km.toString() else "") }
        OutlinedTextField(
            value = kmText,
            onValueChange = { v -> kmText = v.filter { it.isDigit() || it == '.' }.take(6); kmText.toDoubleOrNull()?.let { vm.setRunning(date, it, true) } },
            label = { Text(stringResource(R.string.wa_running_km)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.wa_running_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
    Spacer(Modifier.height(12.dp))
    StepSensorRow(vm)
}

@Composable
private fun ReadingControls(vm: ArcViewModel, date: LocalDate, target: Int, today: Int) {
    val tone = Arc.tone(HabitKind.READING)
    val book by vm.book.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    ArcCard(Modifier.fillMaxWidth(), color = tone.soft, onClick = { picking = true }) {
        Text(stringResource(R.string.wa_reading_book), style = MaterialTheme.typography.labelMedium, color = Kairos.colors.muted)
        Text(book?.title ?: com.kairosera.data.winterarc.WinterArcRepository.DEFAULT_BOOK_TITLE, style = MaterialTheme.typography.titleMedium, color = tone.strong)
        val author = book?.author ?: com.kairosera.data.winterarc.WinterArcRepository.DEFAULT_BOOK_AUTHOR
        if (author.isNotBlank()) Text(author, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
    }
    if (picking) BookPicker(vm, onDismiss = { picking = false })
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.wa_reading_today, today, target), color = Kairos.colors.muted)
    Spacer(Modifier.height(12.dp))
    // A running timer; leaving the sheet keeps nothing, so Stop saves.
    var running by rememberSaveable { mutableStateOf(false) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var elapsed by remember { mutableIntStateOf(0) }
    KeepScreenOn(running)
    LaunchedEffect(running, startedAt) {
        while (running) { elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt(); delay(1000) }
    }
    if (running) {
        Text(ArcFormat.longClock(elapsed), style = MaterialTheme.typography.displaySmall, color = tone.strong, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        ArcButton(stringResource(R.string.wa_reading_stop), {
            running = false
            val minutes = ((elapsed + 30) / 60).coerceAtLeast(1)
            vm.logReading(date, minutes, 0)
            toaster.show(ctx.getString(R.string.wa_reading_saved, minutes))
        }, icon = Icons.Outlined.Stop, color = tone.strong)
    } else {
        ArcButton(stringResource(R.string.wa_reading_start), { startedAt = System.currentTimeMillis(); elapsed = 0; running = true }, icon = Icons.Outlined.PlayArrow, color = tone.strong)
    }
    Spacer(Modifier.height(14.dp))
    var minutes by rememberSaveable { mutableStateOf("") }
    var pages by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.wa_reading_log), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text(stringResource(R.string.wa_reading_minutes)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(pages, { pages = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text(stringResource(R.string.wa_reading_pages)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    Spacer(Modifier.height(8.dp))
    QuickChip(stringResource(R.string.save), tone, {
        val m = minutes.toIntOrNull() ?: 0
        val p = pages.toIntOrNull() ?: 0
        if (m > 0 || p > 0) { vm.logReading(date, m, p); minutes = ""; pages = "" }
    }, Modifier.fillMaxWidth(), filled = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookPicker(vm: ArcViewModel, onDismiss: () -> Unit) {
    val books by vm.books.collectAsStateWithLifecycle()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wa_reading_pick)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                books.filter { it.isActive }.forEach { b ->
                    TextButton(onClick = { vm.setBook(b.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(b.title, style = MaterialTheme.typography.titleSmall)
                            if (b.author.isNotBlank()) Text(stringResource(R.string.wa_by_author, b.author), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun WakeControls(vm: ArcViewModel, date: LocalDate, minute: Int?) {
    val tone = Arc.tone(HabitKind.WAKE_EARLY)
    var picking by remember { mutableStateOf(false) }
    Text(stringResource(R.string.wa_wake_rule), color = Kairos.colors.muted)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (date == LocalDate.now()) QuickChip(stringResource(R.string.wa_wake_now), tone, { vm.setWake(date, LocalTime.now().withSecond(0).withNano(0)) }, Modifier.weight(1f), filled = true)
        QuickChip(stringResource(R.string.wa_wake_set), tone, { picking = true }, Modifier.weight(1f))
        if (minute != null) QuickChip(stringResource(R.string.wa_wake_clear), tone, { vm.setWake(date, null) }, Modifier.weight(1f))
    }
    if (picking) {
        KTimePickerDialog(
            initial = minute?.let { WakeTime.toTime(it) } ?: LocalTime.of(4, 30),
            onDismiss = { picking = false },
            onPick = { vm.setWake(date, it); picking = false },
        )
    }
}

/** Number entry in a small dialog. */
@Composable
fun NumberDialog(title: String, initial: Int, suffix: String, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (initial > 0) initial.toString() else "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                suffix = if (suffix.isNotEmpty()) ({ Text(suffix) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.toIntOrNull() ?: 0) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Keeps the screen awake while a timer runs. */
@Composable
fun KeepScreenOn(on: Boolean) {
    val activity = LocalContext.current as? android.app.Activity ?: return
    DisposableEffect(on) {
        if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

