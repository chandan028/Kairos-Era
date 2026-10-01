package com.kairosera.feature.winterarc

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.settings.ArcReminder
import com.kairosera.core.settings.ReminderConfig
import com.kairosera.core.settings.WinterArcSettings
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.KTimePickerDialog
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.currentLocale
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.HabitType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** Everything about the challenge itself: dates, pause, habits, targets, book, reminders. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArcSettingsScreen(
    vm: ArcViewModel,
    onBack: () -> Unit,
    onOpenStudy: () -> Unit,
    onOpenEveryday: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val arc by vm.arc.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val book by vm.book.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val fmt = rememberMediumDateFormatter()
    var pickStart by remember { mutableStateOf(false) }
    var resetDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickReset by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var editTarget by rememberSaveable { mutableStateOf<HabitKind?>(null) }
    var pickBook by remember { mutableStateOf(false) }
    var editReminder by rememberSaveable { mutableStateOf<ArcReminder?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.wa_settings), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally).widthIn(max = ContentMaxWidth),
        ) {
            ArcSectionTitle(stringResource(R.string.wa_settings_challenge))
            ArcCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                val a = arc
                Row2(stringResource(R.string.wa_start_date), a?.let { fmt(it.startDate) } ?: "—") { pickStart = true }
                if (a != null) {
                    if (a.isPaused) Row2(stringResource(R.string.wa_resume), stringResource(R.string.wa_paused_banner)) { vm.resume() }
                    else Row2(stringResource(R.string.wa_pause), stringResource(R.string.wa_pause_summary)) { vm.pause() }
                }
                Row2(stringResource(R.string.wa_reset), stringResource(R.string.wa_reset_summary)) { resetDate = today.toEpochDay() }
                Row2(stringResource(R.string.wa_exit), stringResource(R.string.wa_exit_summary), danger = true) { confirmExit = true }
            }

            ArcSectionTitle(stringResource(R.string.wa_settings_habits))
            ArcCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                habits.sortedBy { it.sortOrder }.forEach { h ->
                    val tone = Arc.tone(h.kind)
                    val locale = currentLocale()
                    val targetText = when (h.kind) {
                        HabitKind.WATER -> ArcFormat.litres(h.target, locale) + " L"
                        HabitKind.STEPS -> ArcFormat.count(h.target.toInt(), locale)
                        HabitKind.READING, HabitKind.STUDY, HabitKind.DEEP_WORK -> stringResource(R.string.wa_minutes_est, h.target.toInt()).removePrefix("≈ ")
                        HabitKind.WAKE_EARLY -> "< 5:00"
                        else -> stringResource(R.string.wa_sub_daily)
                    }
                    val editable = h.kind.type != HabitType.CHECK && h.kind != HabitKind.WAKE_EARLY
                    ListItem(
                        leadingContent = { HabitBadge(h.kind.icon(), tone, size = 36.dp) },
                        headlineContent = { Text(stringResource(h.kind.longLabel())) },
                        supportingContent = {
                            Text(
                                if (h.kind == HabitKind.READING) (book?.title ?: com.kairosera.data.winterarc.WinterArcRepository.DEFAULT_BOOK_TITLE) + " · " + targetText
                                else targetText,
                            )
                        },
                        trailingContent = { Switch(checked = h.active, onCheckedChange = { vm.setHabitActive(h.kind, it) }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = editable || h.kind == HabitKind.READING) {
                            if (h.kind == HabitKind.READING) pickBook = true else editTarget = h.kind
                        },
                    )
                    if (h.kind == HabitKind.READING) {
                        TextButton(onClick = { editTarget = HabitKind.READING }, modifier = Modifier.padding(start = 64.dp)) {
                            Text(stringResource(R.string.wa_target))
                        }
                    }
                }
                androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { StepSensorRow(vm) }
            }

            ArcSectionTitle(stringResource(R.string.wa_settings_study))
            ArcCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                Row2(stringResource(R.string.wa_study_title), stringResource(R.string.wa_study_import)) { onOpenStudy() }
            }

            ArcSectionTitle(stringResource(R.string.wa_settings_reminders))
            val s = settings ?: WinterArcSettings()
            NotificationPermissionRow()
            ArcCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                ArcReminder.entries.forEach { r ->
                    val cfg = s.reminders.getValue(r)
                    ListItem(
                        headlineContent = { Text(stringResource(r.label())) },
                        supportingContent = { Text(if (cfg.enabled) stringResource(R.string.wa_rem_summary, timeText(cfg), daysText(cfg.days)) else stringResource(R.string.wa_rem_off)) },
                        trailingContent = { Switch(checked = cfg.enabled, onCheckedChange = { vm.setReminder(r, cfg.copy(enabled = it)) }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { editReminder = r },
                    )
                }
                Text(stringResource(R.string.wa_rem_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, modifier = Modifier.padding(16.dp))
            }

            ArcSectionTitle(stringResource(R.string.wa_settings_app))
            ArcCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                Row2(stringResource(R.string.wa_open_kairos), stringResource(R.string.wa_open_kairos_summary)) { onOpenEveryday() }
                Row2(stringResource(R.string.wa_settings_app), stringResource(R.string.wa_settings_app_summary)) { onOpenAppSettings() }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    if (pickStart) {
        KDatePickerDialog(initial = arc?.startDate ?: today, onDismiss = { pickStart = false }, onPick = { pickStart = false; vm.setStartDate(it) })
    }
    resetDate?.let { epoch ->
        val d = LocalDate.ofEpochDay(epoch)
        AlertDialog(
            onDismissRequest = { resetDate = null },
            title = { Text(stringResource(R.string.wa_reset_confirm)) },
            text = {
                Column {
                    Text(stringResource(R.string.wa_reset_body))
                    Spacer(Modifier.height(12.dp))
                    FilterChip(selected = true, onClick = { pickReset = true }, label = { Text(stringResource(R.string.wa_start_date) + ": " + fmt(d)) })
                }
            },
            confirmButton = { TextButton(onClick = { resetDate = null; vm.reset(d) }) { Text(stringResource(R.string.wa_reset_action)) } },
            dismissButton = { TextButton(onClick = { resetDate = null }) { Text(stringResource(R.string.cancel)) } },
        )
        if (pickReset) KDatePickerDialog(initial = d, onDismiss = { pickReset = false }, onPick = { pickReset = false; resetDate = it.toEpochDay() })
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.wa_exit_confirm)) },
            text = { Text(stringResource(R.string.wa_exit_body)) },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.exit() }) { Text(stringResource(R.string.wa_exit_action)) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    editTarget?.let { k ->
        val current = habits.firstOrNull { it.kind == k }?.target ?: k.defaultTarget
        val (shown, suffix) = when (k) {
            HabitKind.WATER -> current.toInt() to "ml"
            HabitKind.STEPS -> current.toInt() to ""
            else -> current.toInt() to "min"
        }
        NumberDialog(stringResource(R.string.wa_target) + " · " + stringResource(k.longLabel()), shown, suffix, onDismiss = { editTarget = null }) { v ->
            editTarget = null
            val max = when (k) { HabitKind.WATER -> 10_000; HabitKind.STEPS -> 100_000; else -> 600 }
            if (v > 0) vm.setTarget(k, v.coerceAtMost(max).toDouble())
        }
    }
    if (pickBook) BookPicker(vm) { pickBook = false }
    editReminder?.let { r ->
        val cfg = (settings ?: WinterArcSettings()).reminders.getValue(r)
        ReminderDialog(r, cfg, onDismiss = { editReminder = null }) { vm.setReminder(r, it); editReminder = null }
    }
}

@Composable
private fun Row2(title: String, summary: String, danger: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
        supportingContent = { Text(summary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun NotificationPermissionRow() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) return
    TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.wa_rem_permission)) }
}

private fun ArcReminder.label() = when (this) {
    ArcReminder.WATER -> R.string.wa_rem_water
    ArcReminder.READING -> R.string.wa_rem_reading
    ArcReminder.STUDY -> R.string.wa_rem_study
    ArcReminder.COLD_SHOWER -> R.string.wa_rem_cold
    ArcReminder.DEEP_WORK -> R.string.wa_rem_focus
    ArcReminder.WAKE_PREP -> R.string.wa_rem_wake
    ArcReminder.SPEAKING -> R.string.wa_rem_speak
}

@Composable
private fun timeText(cfg: ReminderConfig): String {
    val locale = currentLocale()
    return remember(cfg.time, locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(cfg.time) }
}

@Composable
private fun daysText(days: Set<Int>): String {
    val locale = currentLocale()
    return when (days) {
        WinterArcSettings.ALL_DAYS -> stringResource(R.string.wa_rem_every_day)
        (1..5).toSet() -> stringResource(R.string.wa_rem_weekdays)
        else -> days.sorted().joinToString(" ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(r: ArcReminder, cfg: ReminderConfig, onDismiss: () -> Unit, onSave: (ReminderConfig) -> Unit) {
    var time by remember { mutableStateOf(cfg.time) }
    var days by remember { mutableStateOf(cfg.days) }
    var picking by remember { mutableStateOf(false) }
    val locale = currentLocale()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(r.label())) },
        text = {
            Column {
                FilterChip(selected = true, onClick = { picking = true }, label = { Text(timeText(cfg.copy(time = time))) })
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.wa_rem_days), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = d.value in days
                        FilterChip(
                            selected = on,
                            onClick = { days = if (on) days - d.value else days + d.value },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, locale)) },
                        )
                    }
                }
                Row {
                    TextButton(onClick = { days = WinterArcSettings.ALL_DAYS }) { Text(stringResource(R.string.wa_rem_every_day)) }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { days = (1..5).toSet() }) { Text(stringResource(R.string.wa_rem_weekdays)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ReminderConfig(enabled = days.isNotEmpty(), time = time, days = days)) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) KTimePickerDialog(initial = time, onDismiss = { picking = false }, onPick = { picking = false; time = it })
}
