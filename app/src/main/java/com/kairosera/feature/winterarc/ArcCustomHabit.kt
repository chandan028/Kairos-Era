package com.kairosera.feature.winterarc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kairosera.R
import com.kairosera.core.ui.components.currentLocale
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.winterarc.Habit
import com.kairosera.domain.winterarc.HabitDay
import com.kairosera.domain.winterarc.HabitType
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/*
 * A person's own Winter Arc habits: a yes/no check ("Journal", "No junk food") or an amount toward
 * a daily target ("Push-ups 50 reps", "Pages 20"). They count toward the day like the built-in ones.
 */

/** Add (existing == null) or edit a custom habit. Editing also offers delete, behind a confirm. */
@Composable
fun CustomHabitDialog(existing: Habit?, onDismiss: () -> Unit, onSave: (CustomHabitDraft) -> Unit, onDelete: (() -> Unit)? = null) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var amount by rememberSaveable { mutableStateOf(existing?.type == HabitType.AMOUNT) }
    var target by rememberSaveable { mutableStateOf(existing?.takeIf { it.type == HabitType.AMOUNT }?.target?.let { plainNumber(it) } ?: "") }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: "") }
    var icon by rememberSaveable { mutableStateOf(existing?.icon?.ifBlank { null } ?: CUSTOM_ICONS.first().first) }
    var color by rememberSaveable { mutableIntStateOf(existing?.color ?: 0) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val targetValue = parseAmount(target)
    val valid = name.isNotBlank() && (!amount || (targetValue != null && targetValue > 0))
    val tone = Arc.customTone(color)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.wa_add_habit_title else R.string.wa_edit_habit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Habit.NAME_MAX) },
                    label = { Text(stringResource(R.string.wa_habit_name)) },
                    placeholder = { Text(stringResource(R.string.wa_habit_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.wa_habit_track_how), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !amount, onClick = { amount = false }, label = { Text(stringResource(R.string.wa_habit_type_check)) })
                    FilterChip(selected = amount, onClick = { amount = true }, label = { Text(stringResource(R.string.wa_habit_type_amount)) })
                }
                if (amount) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = target,
                            onValueChange = { v -> target = v.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                            label = { Text(stringResource(R.string.wa_habit_target)) },
                            singleLine = true,
                            isError = target.isNotEmpty() && (targetValue == null || targetValue <= 0),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it.take(Habit.UNIT_MAX) },
                            label = { Text(stringResource(R.string.wa_habit_unit)) },
                            placeholder = { Text(stringResource(R.string.wa_habit_unit_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.wa_habit_check_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.wa_habit_icon), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                Spacer(Modifier.height(6.dp))
                val iconLabel = stringResource(R.string.wa_habit_icon)
                // Plain rows rather than FlowRow: a FlowRow inside the dialog's scrolling content
                // never settled on some screen sizes.
                CUSTOM_ICONS.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (key, vector) ->
                            val i = CUSTOM_ICONS.indexOfFirst { it.first == key }
                            val selected = key == icon
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (selected) tone.soft else Kairos.colors.track.copy(alpha = 0.5f))
                                    .then(if (selected) Modifier.border(2.dp, tone.strong, CircleShape) else Modifier)
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = { icon = key })
                                    .semantics { contentDescription = "$iconLabel ${i + 1}" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(vector, contentDescription = null, tint = if (selected) tone.strong else Kairos.colors.muted, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.wa_habit_color), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                Spacer(Modifier.height(6.dp))
                val colorLabel = stringResource(R.string.wa_habit_color)
                (0 until CUSTOM_COLOR_COUNT).chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { i ->
                            val t = Arc.customTone(i)
                            val selected = i == color
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(t.strong)
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = { color = i })
                                    .semantics { contentDescription = "$colorLabel ${i + 1}" },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = t.soft, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                if (existing != null && onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.wa_habit_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        CustomHabitDraft(
                            name = name.trim(),
                            type = if (amount) HabitType.AMOUNT else HabitType.CHECK,
                            target = if (amount) targetValue ?: 1.0 else 1.0,
                            unit = if (amount) unit.trim() else "",
                            icon = icon,
                            color = color,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (confirmDelete && existing != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.wa_habit_delete_title, existing.name)) },
            text = { Text(stringResource(R.string.wa_habit_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.wa_habit_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The sheet body for a custom habit: one tap for yes/no, quick steps and an exact entry for amounts. */
@Composable
internal fun CustomControls(vm: ArcViewModel, date: LocalDate, h: HabitDay) {
    val tone = Arc.tone(h)
    val locale = currentLocale()
    if (h.type != HabitType.AMOUNT) {
        Text(stringResource(R.string.wa_binary_hint), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
        Spacer(Modifier.height(14.dp))
        ArcButton(
            stringResource(if (h.done) R.string.wa_mark_not_done else R.string.wa_mark_done),
            { vm.setCustomChecked(h.habitId, date, !h.done) },
            color = if (h.done) Kairos.colors.track else tone.strong,
            contentColor = if (h.done) MaterialTheme.colorScheme.onSurface else Arc.onPrimary,
        )
        return
    }
    val step = customStep(h.target)
    val unit = h.habit.unit
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickChip("−" + plainNumber(step, locale), tone, { vm.addCustomValue(h.habitId, date, -step) }, Modifier.weight(1f))
        QuickChip("+" + plainNumber(step, locale), tone, { vm.addCustomValue(h.habitId, date, step) }, Modifier.weight(1f), filled = true)
        QuickChip("+" + plainNumber(step * 5, locale), tone, { vm.addCustomValue(h.habitId, date, step * 5) }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    val left = (h.target - h.value).coerceAtLeast(0.0)
    Text(
        if (h.done) stringResource(R.string.wa_custom_goal_met)
        else stringResource(R.string.wa_custom_left, plainNumber(left, locale) + if (unit.isNotBlank()) " $unit" else ""),
        style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted,
    )
    var editing by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { editing = true }) { Text(stringResource(R.string.wa_custom_set)) }
    if (editing) {
        var text by rememberSaveable { mutableStateOf(if (h.value > 0) plainNumber(h.value) else "") }
        val parsed = parseAmount(text)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.wa_custom_set)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                    suffix = if (unit.isNotBlank()) ({ Text(unit) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = text.isEmpty() || parsed != null, onClick = { vm.setCustomValue(h.habitId, date, parsed ?: 0.0); editing = false }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A friendly step for the quick buttons: about a tenth of the target, rounded to 1, 2, 5 × 10ⁿ. */
internal fun customStep(target: Double): Double {
    if (target <= 10) return 1.0
    val raw = target / 10
    var base = 1.0
    while (base * 10 <= raw) base *= 10
    return listOf(1.0, 2.0, 5.0, 10.0).map { it * base }.last { it <= raw }
}

/** Parses "2.5" or "2,5"; null when it isn't a number. */
internal fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

/** 50.0 -> "50", 2.5 -> "2.5", without grouping so it round-trips through a text field. */
internal fun plainNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else ((v * 100).toLong() / 100.0).toString()

internal fun plainNumber(v: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(v)
