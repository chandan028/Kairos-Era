package com.kairosera.feature.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.ToastKind
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.journal.Mood
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate

/**
 * Writing a reflection: a mood, a free page, and four short prompts that close the loop
 * (a win, a lesson, gratitude, one thing for tomorrow). Everything is optional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEditorScreen(entryId: Long?, date: LocalDate?, presetMood: Mood?, onClose: () -> Unit) {
    val vm = kairosViewModel(key = "journal-$entryId-$date") { JournalEditorViewModel(it, entryId, date, presetMood) }
    val form by vm.form.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val day by vm.date.collectAsStateWithLifecycle()
    val titleFmt = rememberDateFormatter("EEEE, d MMMM")
    val toaster = LocalToaster.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val savedMsg = stringResource(R.string.journal_saved)
    val savedTaskMsg = stringResource(R.string.journal_saved_task)
    val trashedMsg = stringResource(R.string.journal_trashed)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(done) {
        when (done) {
            JournalEditorViewModel.Outcome.SAVED -> { toaster.show(savedMsg, ToastKind.SUCCESS); onClose() }
            JournalEditorViewModel.Outcome.SAVED_WITH_TASK -> { toaster.show(savedTaskMsg, ToastKind.SUCCESS); onClose() }
            JournalEditorViewModel.Outcome.TRASHED -> { onClose(); toaster.show(trashedMsg, ToastKind.UNDO, undo) { vm.restore() } }
            null -> Unit
        }
    }
    val close = { if (vm.hasChanges) confirmDiscard = true else onClose() }
    BackHandler(onBack = close)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFmt(day), maxLines = 1) },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.Filled.Close, stringResource(R.string.close)) } },
                actions = {
                    if (vm.isExisting) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete)) }
                    TextButton(onClick = vm::save, enabled = ready && done == null) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            if (!ready) { LoadingState(); return@Box }
            Column(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel(stringResource(R.string.journal_mood_question))
                Text(stringResource(R.string.journal_mood_optional), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoodScale.forEach { m ->
                        val selected = form.mood == m
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) Kairos.colors.accentSoft else Kairos.colors.card,
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Kairos.colors.accent else Kairos.colors.line),
                            modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton) { vm.edit { it.copy(mood = if (selected) null else m) } },
                        ) {
                            Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                MoodIcon(m, Modifier.size(28.dp), tint = if (selected) Kairos.colors.accentText else Kairos.colors.muted)
                                Spacer(Modifier.height(4.dp))
                                Text(moodLabel(m), style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (selected) Kairos.colors.accentText else Kairos.colors.muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                SectionLabel(stringResource(R.string.journal_reflection_label))
                OutlinedTextField(
                    form.text, { v -> vm.edit { it.copy(text = v.take(10_000)) } },
                    placeholder = { Text(stringResource(R.string.journal_what_happened)) },
                    minLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
                SectionLabel(stringResource(R.string.journal_lesson))
                PromptField(stringResource(R.string.journal_lesson_hint), form.lesson) { v -> vm.edit { it.copy(lesson = v) } }
                SectionLabel(stringResource(R.string.journal_tomorrow))
                PromptField(stringResource(R.string.journal_tomorrow_hint), form.tomorrow) { v -> vm.edit { it.copy(tomorrow = v) } }
                if (form.tomorrow.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().selectable(form.planTomorrow, role = Role.Switch) { vm.edit { it.copy(planTomorrow = !it.planTomorrow) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.journal_plan_tomorrow), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.journal_plan_tomorrow_hint), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                        }
                        Switch(checked = form.planTomorrow, onCheckedChange = null)
                    }
                }
                var more by rememberSaveable { mutableStateOf(form.win.isNotBlank() || form.gratitude.isNotBlank()) }
                TextButton(onClick = { more = !more }) {
                    Text(stringResource(if (more) R.string.journal_fewer_prompts else R.string.journal_more_prompts), color = Kairos.colors.accentText)
                }
                if (more) {
                    SectionLabel(stringResource(R.string.journal_win))
                    PromptField(stringResource(R.string.journal_win_hint), form.win) { v -> vm.edit { it.copy(win = v) } }
                    SectionLabel(stringResource(R.string.journal_gratitude))
                    PromptField(stringResource(R.string.journal_gratitude_hint), form.gratitude) { v -> vm.edit { it.copy(gratitude = v) } }
                }
                Button(
                    onClick = vm::save, enabled = done == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Kairos.colors.accent, contentColor = Color(0xFF1A2233)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.journal_save_reflection), style = MaterialTheme.typography.titleSmall) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Kairos.colors.muted, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.journal_private_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.journal_discard_title)) },
            text = { Text(stringResource(R.string.journal_discard_body)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false; vm.save() }) { Text(stringResource(R.string.save)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.journal_delete_title)) },
            text = { Text(stringResource(R.string.journal_delete_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.moveToTrash() }) { Text(stringResource(R.string.move_to_trash)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (failed) {
        AlertDialog(
            onDismissRequest = vm::dismissFailure,
            confirmButton = { TextButton(onClick = { vm.dismissFailure(); vm.save() }) { Text(stringResource(R.string.retry)) } },
            dismissButton = { TextButton(onClick = vm::dismissFailure) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(stringResource(R.string.error_action_failed)) },
        )
    }
}

@Composable
private fun PromptField(hint: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.take(500)) },
        placeholder = { Text(hint) },
        minLines = 2,
        maxLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}
