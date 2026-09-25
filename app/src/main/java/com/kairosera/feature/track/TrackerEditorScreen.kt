package com.kairosera.feature.track

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.data.tracker.TrackerTemplates
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerField
import com.kairosera.domain.tracker.TrackerFrequency
import com.kairosera.domain.tracker.TrackerTemplate
import com.kairosera.ui.kairosViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerEditorScreen(trackerId: Long?, template: TrackerTemplate, onClose: () -> Unit, onSaved: (Long, Boolean) -> Unit) {
    val context = LocalContext.current
    val vm = kairosViewModel(key = "tracker-edit-$trackerId-$template") { TrackerEditorViewModel(it, trackerId, template, context) }
    val form by vm.form.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    LaunchedEffect(status) { (status as? TrackerEditorStatus.Saved)?.let { onSaved(it.id, vm.isNew) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isNew) R.string.new_tracker else R.string.edit_tracker)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) } },
                actions = {
                    TextButton(onClick = vm::save, enabled = status == TrackerEditorStatus.Editing) {
                        if (status == TrackerEditorStatus.Saving) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            when (status) {
                TrackerEditorStatus.Loading -> LoadingState()
                TrackerEditorStatus.Missing -> MessageState(Icons.Outlined.SearchOff, stringResource(R.string.tracker_missing), stringResource(R.string.tracker_missing_body)) {
                    OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                }
                else -> EditorForm(form, errors, vm)
            }
        }
    }
    if (status == TrackerEditorStatus.Failed) {
        AlertDialog(
            onDismissRequest = vm::dismissFailure,
            confirmButton = { TextButton(onClick = { vm.dismissFailure(); vm.save() }) { Text(stringResource(R.string.retry)) } },
            dismissButton = { TextButton(onClick = vm::dismissFailure) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(stringResource(R.string.error_action_failed)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorForm(form: Tracker, errors: Set<TrackerEditorError>, vm: TrackerEditorViewModel) {
    var editingField by remember { mutableStateOf<Pair<Int?, TrackerField>?>(null) }
    Column(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> vm.edit { it.copy(name = v.take(TrackerEditorViewModel.MAX_NAME)) } },
            label = { Text(stringResource(R.string.tracker_name)) },
            isError = TrackerEditorError.NAME_EMPTY in errors,
            supportingText = if (TrackerEditorError.NAME_EMPTY in errors) ({ Text(stringResource(R.string.error_tracker_name)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        SectionLabel(stringResource(R.string.tracker_icon))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TrackerTemplates.ICONS.forEach { icon ->
                val selected = form.icon == icon
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { vm.edit { it.copy(icon = icon) } }
                        .semantics { this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) { Text(icon, style = MaterialTheme.typography.titleMedium) }
            }
        }
        SectionLabel(stringResource(R.string.tracker_color))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrackerTemplates.COLORS.forEachIndexed { i, color ->
                val selected = form.colorArgb == color
                val desc = stringResource(R.string.color_option, i + 1)
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(color))
                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { vm.edit { it.copy(colorArgb = color) } }
                        .semantics { contentDescription = desc; this.selected = selected },
                )
            }
        }
        OutlinedTextField(
            value = form.description, onValueChange = { v -> vm.edit { it.copy(description = v.take(300)) } },
            label = { Text(stringResource(R.string.field_description)) }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.why, onValueChange = { v -> vm.edit { it.copy(why = v.take(300)) } },
            label = { Text(stringResource(R.string.tracker_why)) }, placeholder = { Text(stringResource(R.string.tracker_why_hint)) }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.gain, onValueChange = { v -> vm.edit { it.copy(gain = v.take(300)) } },
            label = { Text(stringResource(R.string.tracker_gain)) }, placeholder = { Text(stringResource(R.string.tracker_gain_hint)) }, modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        SectionLabel(stringResource(R.string.tracker_frequency))
        FrequencyEditor(form.frequency, vm::setFrequency)

        HorizontalDivider()
        SectionLabel(stringResource(R.string.tracker_fields))
        Text(stringResource(R.string.tracker_fields_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (TrackerEditorError.NO_FIELDS in errors) Text(stringResource(R.string.error_tracker_fields), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (TrackerEditorError.CHECKLIST_EMPTY in errors) Text(stringResource(R.string.error_checklist_empty), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        form.fields.forEachIndexed { index, field ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { editingField = index to field }) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(field.label.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = if (field.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fieldSummary(field), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = field.enabled, onCheckedChange = { on -> vm.upsertField(index, field.copy(enabled = on)) })
                IconButton(onClick = { vm.moveField(index, up = true) }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.move_up)) }
                IconButton(onClick = { vm.moveField(index, up = false) }, enabled = index < form.fields.lastIndex) { Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.move_down)) }
            }
        }
        if (form.fields.size < TrackerEditorViewModel.MAX_FIELDS) {
            TextButton(onClick = { editingField = null to TrackerField(label = "", type = MeasurementType.CHECKBOX) }) {
                Icon(Icons.Outlined.Add, null)
                Text(stringResource(R.string.add_field))
            }
        }
        Spacer(Modifier.height(48.dp))
    }
    editingField?.let { (index, field) ->
        FieldDialog(
            field = field,
            onDismiss = { editingField = null },
            onSave = { vm.upsertField(index, it); editingField = null },
            onRemove = index?.let { { vm.removeField(it); editingField = null } },
        )
    }
}

@Composable
fun typeLabel(type: MeasurementType): String = stringResource(
    when (type) {
        MeasurementType.CHECKBOX -> R.string.type_checkbox
        MeasurementType.NUMBER -> R.string.type_number
        MeasurementType.DECIMAL -> R.string.type_decimal
        MeasurementType.DURATION -> R.string.type_duration
        MeasurementType.PAGES -> R.string.type_pages
        MeasurementType.PERCENTAGE -> R.string.type_percentage
        MeasurementType.RATING -> R.string.type_rating
        MeasurementType.TEXT -> R.string.type_text
        MeasurementType.CHECKLIST -> R.string.type_checklist
    },
)

@Composable
private fun fieldSummary(field: TrackerField): String {
    val parts = mutableListOf(typeLabel(field.type))
    field.target?.let { parts += stringResource(R.string.field_target_line, formatNumber(it), if (field.type == MeasurementType.DURATION) stringResource(R.string.unit_minutes) else field.unit) }
    if (field.type == MeasurementType.CHECKLIST) parts += stringResource(R.string.checklist_items, field.options.size)
    if (!field.enabled) parts += stringResource(R.string.field_off)
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrequencyEditor(frequency: TrackerFrequency, onChange: (TrackerFrequency) -> Unit) {
    val locale = com.kairosera.core.ui.components.currentLocale()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(frequency is TrackerFrequency.Daily, { onChange(TrackerFrequency.Daily) }, { Text(stringResource(R.string.freq_daily)) })
        FilterChip(frequency is TrackerFrequency.SelectedDays, {
            if (frequency !is TrackerFrequency.SelectedDays) onChange(TrackerFrequency.SelectedDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
        }, { Text(stringResource(R.string.freq_selected_days)) })
        FilterChip(frequency is TrackerFrequency.TimesPerWeek, {
            if (frequency !is TrackerFrequency.TimesPerWeek) onChange(TrackerFrequency.TimesPerWeek(3))
        }, { Text(stringResource(R.string.freq_per_week)) })
        FilterChip(frequency is TrackerFrequency.TimesPerMonth, {
            if (frequency !is TrackerFrequency.TimesPerMonth) onChange(TrackerFrequency.TimesPerMonth(10))
        }, { Text(stringResource(R.string.freq_per_month)) })
    }
    when (frequency) {
        is TrackerFrequency.SelectedDays -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                val on = d in frequency.days
                FilterChip(on, {
                    val next = if (on) frequency.days - d else frequency.days + d
                    if (next.isNotEmpty()) onChange(TrackerFrequency.SelectedDays(next))
                }, { Text(d.getDisplayName(TextStyle.SHORT, locale)) })
            }
        }
        is TrackerFrequency.TimesPerWeek -> Stepper(stringResource(R.string.freq_times_week, frequency.times), frequency.times, 1..7) { onChange(TrackerFrequency.TimesPerWeek(it)) }
        is TrackerFrequency.TimesPerMonth -> Stepper(stringResource(R.string.freq_times_month, frequency.times), frequency.times, 1..31) { onChange(TrackerFrequency.TimesPerMonth(it)) }
        TrackerFrequency.Daily -> Unit
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        TextButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldDialog(field: TrackerField, onDismiss: () -> Unit, onSave: (TrackerField) -> Unit, onRemove: (() -> Unit)?) {
    var draft by remember { mutableStateOf(field) }
    var targetText by remember { mutableStateOf(field.target?.let { formatNumber(it) }.orEmpty()) }
    var optionsText by remember { mutableStateOf(field.options.joinToString("\n")) }
    val hasTarget = draft.type !in setOf(MeasurementType.CHECKBOX, MeasurementType.TEXT, MeasurementType.CHECKLIST)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (onRemove == null) R.string.add_field else R.string.edit_field)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(draft.label, { draft = draft.copy(label = it.take(60)) }, label = { Text(stringResource(R.string.field_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.field_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MeasurementType.entries.forEach { t -> FilterChip(draft.type == t, { draft = draft.copy(type = t) }, { Text(typeLabel(t)) }) }
                }
                if (hasTarget) {
                    OutlinedTextField(
                        targetText, { v -> targetText = v.filter { it.isDigit() || it == '.' }.take(10) },
                        label = { Text(stringResource(R.string.field_target)) },
                        supportingText = { Text(stringResource(R.string.field_target_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    if (draft.type in setOf(MeasurementType.NUMBER, MeasurementType.DECIMAL, MeasurementType.PAGES)) {
                        OutlinedTextField(draft.unit, { draft = draft.copy(unit = it.take(16)) }, label = { Text(stringResource(R.string.field_unit)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (draft.type == MeasurementType.CHECKLIST) {
                    OutlinedTextField(optionsText, { optionsText = it.take(2000) }, label = { Text(stringResource(R.string.checklist_options)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
                if (onRemove != null) {
                    Text(stringResource(R.string.remove_field_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onRemove) {
                        Icon(Icons.Outlined.DeleteOutline, null)
                        Text(stringResource(R.string.remove_field))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.label.isNotBlank(),
                onClick = {
                    onSave(
                        draft.copy(
                            label = draft.label.trim(),
                            target = if (hasTarget) targetText.toDoubleOrNull()?.takeIf { it > 0 } else null,
                            options = if (draft.type == MeasurementType.CHECKLIST) optionsText.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(30) else emptyList(),
                            unit = if (hasTarget) draft.unit.trim() else "",
                        ),
                    )
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
