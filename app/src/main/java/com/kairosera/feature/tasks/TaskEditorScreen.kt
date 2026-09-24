package com.kairosera.feature.tasks

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.Hairline
import com.kairosera.core.ui.components.KChip
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.KTimePickerDialog
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.PrimaryWideButton
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.domain.model.Priority
import com.kairosera.domain.model.Reminder
import com.kairosera.domain.model.ReminderSound
import com.kairosera.domain.model.ReminderTiming
import com.kairosera.domain.model.Subtask
import com.kairosera.domain.usecase.TaskValidationError
import com.kairosera.domain.usecase.TaskValidator
import com.kairosera.ui.kairosViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * New task is a short sheet, not a form: one question ("What needs to be done?"), then a few
 * one-tap rows for day, time, reminder, repeat, category and priority. Everything else hides
 * under "More details".
 */
@Composable
fun TaskEditorScreen(taskId: Long?, occurrenceDate: LocalDate?, onClose: () -> Unit) {
    val vm = kairosViewModel(key = "task-$taskId-${occurrenceDate?.toEpochDay()}") { TaskEditorViewModel(it, taskId, occurrenceDate) }
    val form by vm.form.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }

    LaunchedEffect(status) { if (status == EditorStatus.Saved) onClose() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            Text(
                stringResource(if (vm.isNew) R.string.new_task else R.string.edit_task),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (!vm.isNew && status == EditorStatus.Editing) {
                IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.move_to_trash)) }
            }
            TextButton(onClick = vm::save, enabled = status == EditorStatus.Editing) {
                if (status == EditorStatus.Saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save), style = MaterialTheme.typography.titleSmall)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (status) {
                EditorStatus.Loading -> LoadingState()
                EditorStatus.Missing -> MessageState(Icons.Outlined.SearchOff, stringResource(R.string.task_missing), stringResource(R.string.task_missing_body)) {
                    OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                }
                else -> EditorForm(form, errors, vm)
            }
        }
    }
    if (status == EditorStatus.Failed) {
        AlertDialog(
            onDismissRequest = vm::dismissFailure,
            confirmButton = { TextButton(onClick = { vm.dismissFailure(); vm.save() }) { Text(stringResource(R.string.retry)) } },
            dismissButton = { TextButton(onClick = vm::dismissFailure) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(stringResource(R.string.error_save_body)) },
        )
    }
    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            confirmButton = { TextButton(onClick = { confirmTrash = false; vm.moveToTrash(onClose) }) { Text(stringResource(R.string.move_to_trash)) } },
            dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text(stringResource(R.string.cancel)) } },
            text = { Text(stringResource(R.string.trash_confirm_body)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorForm(form: TaskForm, errors: List<TaskValidationError>, vm: TaskEditorViewModel) {
    val dateFmt = rememberMediumDateFormatter()
    val timeFmt = rememberTimeFormatter()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    var more by rememberSaveable { mutableStateOf(!vm.isNew && (form.description.isNotBlank() || form.subtasks.isNotEmpty() || form.notes.isNotBlank() || form.tagsText.isNotBlank())) }
    var newCategory by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (vm.isNew) runCatching { focus.requestFocus() } }
    val today = LocalDate.now()

    Column(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val titleError = when {
            TaskValidationError.EmptyTitle in errors -> stringResource(R.string.error_title_empty)
            TaskValidationError.TitleTooLong in errors -> stringResource(R.string.error_title_long, TaskValidator.MAX_TITLE)
            else -> null
        }
        Column {
            TextField(
                value = form.title,
                onValueChange = { v -> vm.edit { it.copy(title = v.take(TaskValidator.MAX_TITLE + 20)) } },
                placeholder = { Text(stringResource(R.string.task_what), style = MaterialTheme.typography.headlineSmall, color = Kairos.colors.muted) },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = SerifFamily),
                isError = titleError != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Kairos.colors.brand,
                    unfocusedIndicatorColor = Kairos.colors.line,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (titleError != null) ErrorText(titleError, Modifier.padding(top = 4.dp, start = 16.dp))
        }

        // WHEN
        Field(stringResource(R.string.section_when)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KChip(stringResource(R.string.today), form.date == today) { vm.edit { it.copy(date = today) } }
                KChip(stringResource(R.string.tomorrow), form.date == today.plusDays(1)) { vm.edit { it.copy(date = today.plusDays(1)) } }
                val other = form.date != today && form.date != today.plusDays(1)
                KChip(if (other) dateFmt(form.date) else stringResource(R.string.task_pick_day), other, Icons.Outlined.CalendarToday) { pick = "date" }
            }
        }

        // TIME
        Field(stringResource(R.string.task_time)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KChip(stringResource(R.string.all_day_short), form.allDay) { vm.edit { it.copy(allDay = true) } }
                KChip(if (form.allDay) stringResource(R.string.task_set_time) else timeFmt(form.start), !form.allDay, Icons.Outlined.Schedule) {
                    if (form.allDay) vm.edit { it.copy(allDay = false) } else pick = "start"
                }
                if (!form.allDay) {
                    KChip(form.end?.let { stringResource(R.string.ends_at, timeFmt(it)) } ?: stringResource(R.string.add_end_time), form.end != null) { pick = "end" }
                    if (form.end != null) {
                        IconButton(onClick = { vm.edit { it.copy(end = null) } }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, stringResource(R.string.remove_end_time), Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (TaskValidationError.EndBeforeStart in errors) ErrorText(stringResource(R.string.error_end_before_start))
        }

        ReminderField(form, errors, vm, onPickTime = { pick = "reminderTime" })

        RepeatField(form, vm, onPickEnd = { pick = "repeatEnd" })

        // CATEGORY
        Field(stringResource(R.string.section_category)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    KChip(cat.name, form.categoryId == cat.id) { vm.edit { it.copy(categoryId = if (it.categoryId == cat.id) null else cat.id) } }
                }
                KChip(stringResource(R.string.task_new_category), false, Icons.Outlined.Add) { newCategory = true }
            }
        }

        // PRIORITY: Low / Normal / High. Older "medium" tasks read as Normal and keep their value unless changed.
        Field(stringResource(R.string.section_priority)) {
            val options = listOf(Priority.LOW to R.string.priority_low, Priority.NONE to R.string.priority_normal, Priority.HIGH to R.string.priority_high)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (p, label) ->
                    val selected = form.priority == p || (p == Priority.NONE && form.priority == Priority.MEDIUM)
                    SegmentedButton(
                        selected = selected,
                        onClick = { vm.edit { it.copy(priority = p) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = Kairos.colors.brand, activeContentColor = Kairos.colors.onBrand),
                    ) { Text(stringResource(label)) }
                }
            }
        }

        TextButton(onClick = { more = !more }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.task_more_details), modifier = Modifier.weight(1f))
            Icon(if (more) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(more) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { v -> vm.edit { it.copy(description = v.take(2000)) } },
                    label = { Text(stringResource(R.string.field_description)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                SubtaskSection(form, vm)
                OutlinedTextField(
                    value = form.tagsText,
                    onValueChange = { v -> vm.edit { it.copy(tagsText = v.take(300)) } },
                    label = { Text(stringResource(R.string.field_tags)) },
                    supportingText = { Text(stringResource(R.string.field_tags_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { v -> vm.edit { it.copy(notes = v.take(5000)) } },
                    label = { Text(stringResource(R.string.field_notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (form.isSample) Text(stringResource(R.string.example_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        PrimaryWideButton(
            stringResource(if (vm.isNew) R.string.task_create else R.string.task_save_changes),
            onClick = vm::save,
            modifier = Modifier.navigationBarsPadding().padding(bottom = 24.dp),
        )
    }

    when (pick) {
        "date" -> KDatePickerDialog(form.date, onDismiss = { pick = null }) { d -> vm.edit { it.copy(date = d) } }
        "repeatEnd" -> KDatePickerDialog(form.repeatEnd ?: form.date.plusMonths(1), onDismiss = { pick = null }) { d -> vm.edit { it.copy(repeatEnd = d) } }
        "start" -> KTimePickerDialog(form.start, onDismiss = { pick = null }) { t ->
            vm.edit { f ->
                // Keep the duration when the start moves.
                val shift = java.time.Duration.between(f.start, t)
                f.copy(start = t, end = f.end?.plus(shift)?.takeIf { it > t })
            }
        }
        "end" -> KTimePickerDialog(form.end ?: form.start.plusHours(1), onDismiss = { pick = null }) { t -> vm.edit { it.copy(end = t) } }
        "reminderTime" -> {
            val scope = rememberCoroutineScope()
            KTimePickerDialog(if (form.allDay) LocalTime.of(9, 0) else form.start, onDismiss = { pick = null }) { t ->
                scope.launch { val r = vm.newReminder(ReminderTiming.AtTime(t)); vm.edit { it.copy(reminders = listOf(r)) } }
            }
        }
    }
    if (newCategory) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newCategory = false },
            title = { Text(stringResource(R.string.task_new_category_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.task_new_category_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            },
            confirmButton = { TextButton(onClick = { vm.addCategory(name); newCategory = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.add)) } },
            dismissButton = { TextButton(onClick = { newCategory = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Overline label above a row of one-tap choices. */
@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(label)
        content()
    }
}

@Composable
private fun ErrorText(text: String, modifier: Modifier = Modifier) =
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = modifier)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatField(form: TaskForm, vm: TaskEditorViewModel, onPickEnd: () -> Unit) {
    val dateFmt = rememberMediumDateFormatter()
    val dayFmt = rememberDateFormatter("EEE")
    Field(stringResource(R.string.section_repeat)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatChoice.entries.forEach { r -> KChip(stringResource(repeatLabel(r)), form.repeat == r) { vm.edit { it.copy(repeat = r) } } }
        }
        if (form.repeat == RepeatChoice.WEEKLY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    val selected = d in form.weekdays || (form.weekdays.isEmpty() && d == form.date.dayOfWeek)
                    KChip(dayFmt(form.date.with(d)), selected) {
                        vm.edit { f ->
                            val base = f.weekdays.ifEmpty { setOf(f.date.dayOfWeek) }
                            f.copy(weekdays = if (d in base && base.size > 1) base - d else base + d)
                        }
                    }
                }
            }
        }
        if (form.repeat != RepeatChoice.NONE && form.repeat != RepeatChoice.WEEKDAYS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.repeat_every, form.interval), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { vm.edit { it.copy(interval = (it.interval - 1).coerceAtLeast(1)) } }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                IconButton(onClick = { vm.edit { it.copy(interval = (it.interval + 1).coerceAtMost(99)) } }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
        if (form.repeat != RepeatChoice.NONE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KChip(form.repeatEnd?.let { stringResource(R.string.repeat_until, dateFmt(it)) } ?: stringResource(R.string.repeat_forever), form.repeatEnd != null, Icons.Outlined.CalendarToday, onPickEnd)
                if (form.repeatEnd != null) IconButton(onClick = { vm.edit { it.copy(repeatEnd = null) } }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
            }
        }
    }
}

private fun repeatLabel(r: RepeatChoice) = when (r) {
    RepeatChoice.NONE -> R.string.repeat_never
    RepeatChoice.DAILY -> R.string.repeat_daily
    RepeatChoice.WEEKDAYS -> R.string.repeat_weekdays
    RepeatChoice.WEEKLY -> R.string.repeat_weekly
    RepeatChoice.MONTHLY -> R.string.repeat_monthly
    RepeatChoice.YEARLY -> R.string.repeat_yearly
}

/**
 * One reminder in one tap: None, At start, 10 min, 30 min, 1 hour, or a time.
 * Tasks saved earlier with several reminders keep them and list them below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderField(form: TaskForm, errors: List<TaskValidationError>, vm: TaskEditorViewModel, onPickTime: () -> Unit) {
    val scope = rememberCoroutineScope()
    val timeFmt = rememberTimeFormatter()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun set(timing: ReminderTiming?) {
        if (timing == null) { vm.edit { it.copy(reminders = emptyList()) }; return }
        askPermission()
        scope.launch {
            val fresh = vm.newReminder(timing)
            // Keep the person's "keep until I act" and sound choices when switching the timing.
            vm.edit { f -> f.copy(reminders = listOf(f.reminders.firstOrNull()?.copy(timing = timing) ?: fresh)) }
        }
    }
    val current = form.reminders.singleOrNull()?.timing
    Field(stringResource(R.string.section_reminder)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KChip(stringResource(R.string.reminder_none), form.reminders.isEmpty()) { set(null) }
            if (!form.allDay) {
                listOf(0, 10, 30, 60).forEach { m ->
                    KChip(
                        if (m == 0) stringResource(R.string.reminder_at_start) else stringResource(R.string.reminder_before, m),
                        current == ReminderTiming.BeforeStart(m),
                        if (current == ReminderTiming.BeforeStart(m)) Icons.Outlined.NotificationsNone else null,
                    ) { set(ReminderTiming.BeforeStart(m)) }
                }
            }
            val at = current as? ReminderTiming.AtTime
            KChip(at?.let { stringResource(R.string.reminder_at, timeFmt(it.time)) } ?: stringResource(R.string.reminder_custom_time), at != null, Icons.Outlined.Schedule) {
                askPermission(); onPickTime()
            }
        }
        if (form.reminders.size > 1) {
            form.reminders.forEachIndexed { index, r ->
                ReminderRow(r, timeFmt, onChange = { nr -> vm.edit { f -> f.copy(reminders = f.reminders.toMutableList().also { it[index] = nr }) } },
                    onRemove = { vm.edit { f -> f.copy(reminders = f.reminders.filterIndexed { i, _ -> i != index }) } })
            }
        } else {
            form.reminders.firstOrNull()?.let { r -> ReminderOptions(r) { nr -> vm.edit { it.copy(reminders = listOf(nr)) } } }
        }
        if (TaskValidationError.ReminderNeedsStartTime in errors) ErrorText(stringResource(R.string.error_reminder_needs_time))
        if (TaskValidationError.TooManyReminders in errors) ErrorText(stringResource(R.string.error_too_many_reminders, TaskValidator.MAX_REMINDERS))
    }
}

/** "Keep until I act" and the sound, for the single reminder. */
@Composable
private fun ReminderOptions(r: Reminder, onChange: (Reminder) -> Unit) {
    var soundMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = r.persistent, onCheckedChange = { onChange(r.copy(persistent = it)) })
        Text(stringResource(R.string.reminder_persistent), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { soundMenu = true }) { Text(stringResource(soundLabel(r.sound))) }
            DropdownMenu(expanded = soundMenu, onDismissRequest = { soundMenu = false }) {
                SELECTABLE_SOUNDS.forEach { s ->
                    DropdownMenuItem(text = { Text(stringResource(soundLabel(s))) }, onClick = { soundMenu = false; onChange(r.copy(sound = s)) })
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder, timeFmt: (LocalTime) -> String, onChange: (Reminder) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = Kairos.colors.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when (val t = r.timing) {
                    is ReminderTiming.AtTime -> stringResource(R.string.reminder_at, timeFmt(t.time))
                    is ReminderTiming.BeforeStart -> if (t.minutes == 0) stringResource(R.string.reminder_at_start) else stringResource(R.string.reminder_before, t.minutes)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(R.string.remove_reminder)) }
        }
        ReminderOptions(r, onChange)
        Hairline()
    }
}

val SELECTABLE_SOUNDS = listOf(ReminderSound.DEFAULT, ReminderSound.KAIROS_BELL, ReminderSound.SOFT_CHIME, ReminderSound.FOCUS, ReminderSound.SILENT)

fun soundLabel(s: ReminderSound) = when (s) {
    ReminderSound.DEFAULT, ReminderSound.CUSTOM -> R.string.sound_default
    ReminderSound.KAIROS_BELL -> R.string.sound_kairos_bell
    ReminderSound.SOFT_CHIME -> R.string.sound_soft_chime
    ReminderSound.FOCUS -> R.string.sound_focus
    ReminderSound.SILENT -> R.string.sound_silent
}

@Composable
private fun SubtaskSection(form: TaskForm, vm: TaskEditorViewModel) {
    var draft by rememberSaveable { mutableStateOf("") }
    Column {
        SectionLabel(stringResource(R.string.section_subtasks))
        form.subtasks.forEachIndexed { index, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.done, onCheckedChange = { v -> vm.edit { f -> f.copy(subtasks = f.subtasks.toMutableList().also { it[index] = s.copy(done = v) }) } })
                Text(s.title, Modifier.weight(1f))
                IconButton(onClick = { vm.edit { f -> f.copy(subtasks = f.subtasks.filterIndexed { i, _ -> i != index }) } }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.remove_subtask))
                }
            }
        }
        fun commit() {
            val t = draft.trim()
            if (t.isNotEmpty() && form.subtasks.size < MAX_SUBTASKS) vm.edit { it.copy(subtasks = it.subtasks + Subtask(title = t.take(200), position = it.subtasks.size)) }
            draft = ""
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.add_subtask)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { commit() }) { Icon(Icons.Outlined.Add, stringResource(R.string.add_subtask)) }
        }
    }
}

private const val MAX_SUBTASKS = 50

