package com.kairosera.feature.tasks

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.KTimePickerDialog
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.components.rememberTimeFormatter
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(taskId: Long?, occurrenceDate: LocalDate?, onClose: () -> Unit) {
    val vm = kairosViewModel(key = "task-$taskId-${occurrenceDate?.toEpochDay()}") { TaskEditorViewModel(it, taskId, occurrenceDate) }
    val form by vm.form.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }

    LaunchedEffect(status) { if (status == EditorStatus.Saved) onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isNew) R.string.new_task else R.string.edit_task)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) } },
                actions = {
                    if (!vm.isNew && status == EditorStatus.Editing) {
                        IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.move_to_trash)) }
                    }
                    TextButton(onClick = vm::save, enabled = status == EditorStatus.Editing) {
                        if (status == EditorStatus.Saving) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
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

    Column(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val titleError = when {
            TaskValidationError.EmptyTitle in errors -> stringResource(R.string.error_title_empty)
            TaskValidationError.TitleTooLong in errors -> stringResource(R.string.error_title_long, TaskValidator.MAX_TITLE)
            else -> null
        }
        OutlinedTextField(
            value = form.title,
            onValueChange = { v -> vm.edit { it.copy(title = v.take(TaskValidator.MAX_TITLE + 20)) } },
            label = { Text(stringResource(R.string.field_title)) },
            isError = titleError != null,
            supportingText = titleError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.description,
            onValueChange = { v -> vm.edit { it.copy(description = v.take(2000)) } },
            label = { Text(stringResource(R.string.field_description)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel(stringResource(R.string.section_when), Modifier.padding(top = 8.dp))
        OutlinedButton(onClick = { pick = "date" }, modifier = Modifier.fillMaxWidth()) { Text(dateFmt(form.date)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.all_day), Modifier.weight(1f))
            Switch(checked = form.allDay, onCheckedChange = { v -> vm.edit { it.copy(allDay = v) } })
        }
        if (!form.allDay) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pick = "start" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.starts_at, timeFmt(form.start))) }
                OutlinedButton(onClick = { pick = "end" }, modifier = Modifier.weight(1f)) {
                    Text(form.end?.let { stringResource(R.string.ends_at, timeFmt(it)) } ?: stringResource(R.string.add_end_time))
                }
            }
            if (TaskValidationError.EndBeforeStart in errors) ErrorText(stringResource(R.string.error_end_before_start))
            if (form.end != null) TextButton(onClick = { vm.edit { it.copy(end = null) } }) { Text(stringResource(R.string.remove_end_time)) }
        }

        RepeatSection(form, vm, onPickEnd = { pick = "repeatEnd" })

        ReminderSection(form, errors, vm, onPickTime = { pick = "reminderTime" })

        SectionLabel(stringResource(R.string.section_priority), Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { p ->
                FilterChip(selected = form.priority == p, onClick = { vm.edit { it.copy(priority = p) } }, label = { Text(stringResource(priorityLabel(p))) })
            }
        }

        if (categories.isNotEmpty()) {
            SectionLabel(stringResource(R.string.section_category), Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = form.categoryId == cat.id,
                        onClick = { vm.edit { it.copy(categoryId = if (it.categoryId == cat.id) null else cat.id) } },
                        label = { Text(cat.name) },
                    )
                }
            }
        }

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
        if (form.isSample) Text(stringResource(R.string.example_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
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
                scope.launch { val r = vm.newReminder(ReminderTiming.AtTime(t)); vm.edit { it.copy(reminders = it.reminders + r) } }
            }
        }
    }
}

@Composable
private fun ErrorText(text: String) = Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

private fun priorityLabel(p: Priority) = when (p) {
    Priority.NONE -> R.string.priority_none
    Priority.LOW -> R.string.priority_low
    Priority.MEDIUM -> R.string.priority_medium
    Priority.HIGH -> R.string.priority_high
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatSection(form: TaskForm, vm: TaskEditorViewModel, onPickEnd: () -> Unit) {
    val dateFmt = rememberMediumDateFormatter()
    val dayFmt = rememberDateFormatter("EEE")
    SectionLabel(stringResource(R.string.section_repeat), Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RepeatChoice.entries.forEach { r ->
            FilterChip(selected = form.repeat == r, onClick = { vm.edit { it.copy(repeat = r) } }, label = { Text(stringResource(repeatLabel(r))) })
        }
    }
    if (form.repeat == RepeatChoice.WEEKLY) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                val selected = d in form.weekdays || (form.weekdays.isEmpty() && d == form.date.dayOfWeek)
                FilterChip(
                    selected = selected,
                    onClick = {
                        vm.edit { f ->
                            val base = f.weekdays.ifEmpty { setOf(f.date.dayOfWeek) }
                            f.copy(weekdays = if (d in base && base.size > 1) base - d else base + d)
                        }
                    },
                    label = { Text(dayFmt(form.date.with(d))) },
                )
            }
        }
    }
    if (form.repeat != RepeatChoice.NONE && form.repeat != RepeatChoice.WEEKDAYS) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.repeat_every, form.interval), Modifier.weight(1f))
            IconButton(onClick = { vm.edit { it.copy(interval = (it.interval - 1).coerceAtLeast(1)) } }) { Text("−", style = MaterialTheme.typography.titleLarge) }
            IconButton(onClick = { vm.edit { it.copy(interval = (it.interval + 1).coerceAtMost(99)) } }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
    if (form.repeat != RepeatChoice.NONE) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickEnd, modifier = Modifier.weight(1f)) {
                Text(form.repeatEnd?.let { stringResource(R.string.repeat_until, dateFmt(it)) } ?: stringResource(R.string.repeat_forever))
            }
            if (form.repeatEnd != null) IconButton(onClick = { vm.edit { it.copy(repeatEnd = null) } }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
        }
    }
}

private fun repeatLabel(r: RepeatChoice) = when (r) {
    RepeatChoice.NONE -> R.string.repeat_none
    RepeatChoice.DAILY -> R.string.repeat_daily
    RepeatChoice.WEEKDAYS -> R.string.repeat_weekdays
    RepeatChoice.WEEKLY -> R.string.repeat_weekly
    RepeatChoice.MONTHLY -> R.string.repeat_monthly
    RepeatChoice.YEARLY -> R.string.repeat_yearly
}

@Composable
private fun ReminderSection(form: TaskForm, errors: List<TaskValidationError>, vm: TaskEditorViewModel, onPickTime: () -> Unit) {
    val scope = rememberCoroutineScope()
    val timeFmt = rememberTimeFormatter()
    var menu by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun add(timing: ReminderTiming) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        scope.launch { val r = vm.newReminder(timing); vm.edit { it.copy(reminders = it.reminders + r) } }
    }
    SectionLabel(stringResource(R.string.section_reminders), Modifier.padding(top = 8.dp))
    form.reminders.forEachIndexed { index, r ->
        ReminderRow(r, timeFmt, onChange = { nr -> vm.edit { f -> f.copy(reminders = f.reminders.toMutableList().also { it[index] = nr }) } },
            onRemove = { vm.edit { f -> f.copy(reminders = f.reminders.filterIndexed { i, _ -> i != index }) } })
    }
    if (TaskValidationError.ReminderNeedsStartTime in errors) ErrorText(stringResource(R.string.error_reminder_needs_time))
    if (TaskValidationError.TooManyReminders in errors) ErrorText(stringResource(R.string.error_too_many_reminders, TaskValidator.MAX_REMINDERS))
    if (form.reminders.size < TaskValidator.MAX_REMINDERS) {
        Box {
            OutlinedButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.Add, null)
                Text(stringResource(R.string.add_reminder), Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!form.allDay) {
                    listOf(0, 10, 30, 60).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(if (m == 0) stringResource(R.string.reminder_at_start) else stringResource(R.string.reminder_before, m)) },
                            onClick = { menu = false; add(ReminderTiming.BeforeStart(m)) },
                        )
                    }
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.reminder_custom_time)) }, onClick = {
                    menu = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    onPickTime()
                })
            }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder, timeFmt: (LocalTime) -> String, onChange: (Reminder) -> Unit, onRemove: () -> Unit) {
    var soundMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        HorizontalDivider()
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
    SectionLabel(stringResource(R.string.section_subtasks), Modifier.padding(top = 8.dp))
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
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { commit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { commit() }) { Icon(Icons.Outlined.Add, stringResource(R.string.add_subtask)) }
    }
}

private const val MAX_SUBTASKS = 50

