package com.kairosera.feature.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.domain.tracker.FieldValue
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.TrackerField
import com.kairosera.domain.tracker.TrackerScoring
import com.kairosera.domain.tracker.TrackerTemplate
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(trackerId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm = kairosViewModel(key = "tracker-$trackerId") { TrackerDetailViewModel(it, trackerId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }
    DisposableEffect(vm) { onDispose { vm.flush() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? DetailState.Ready)?.summary?.tracker?.name.orEmpty(), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (state is DetailState.Ready) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_tracker)) }
                        IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.move_to_trash)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                DetailState.Loading -> LoadingState()
                DetailState.Error -> ErrorState(onRetry = null)
                DetailState.Missing -> MessageState(Icons.Outlined.SearchOff, stringResource(R.string.tracker_missing), stringResource(R.string.tracker_missing_body)) {
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.close)) }
                }
                is DetailState.Ready -> DetailContent(s, vm)
            }
        }
    }
    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            confirmButton = { TextButton(onClick = { confirmTrash = false; vm.moveToTrash(onBack) }) { Text(stringResource(R.string.move_to_trash)) } },
            dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text(stringResource(R.string.cancel)) } },
            text = { Text(stringResource(R.string.tracker_trash_body)) },
        )
    }
}

@Composable
private fun DetailContent(s: DetailState.Ready, vm: TrackerDetailViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val t = s.summary.tracker
    val dayScore = TrackerScoring.score(t, com.kairosera.domain.tracker.TrackerEntry(t.id, s.date, draft))
    LazyColumn(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackerBadge(t.icon, t.colorArgb)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.headlineSmall)
                    if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (t.why.isNotBlank() || t.gain.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                KCard {
                    if (t.why.isNotBlank()) Text(stringResource(R.string.tracker_why_line, t.why), style = MaterialTheme.typography.bodyMedium)
                    if (t.gain.isNotBlank()) Text(stringResource(R.string.tracker_gain_line, t.gain), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item { DateSwitcher(s.date, s.today, vm::setDate) }
        item {
            KCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.tracker_log), Modifier.weight(1f))
                    Text(
                        if (dayScore.done) stringResource(R.string.tracker_done_today) else "${(dayScore.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (dayScore.done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                t.enabledFields.forEach { field ->
                    Spacer(Modifier.height(12.dp))
                    FieldInput(field, draft[field.id], key = "${s.date}-${field.id}", onChange = vm::update)
                }
                if (t.enabledFields.isEmpty()) {
                    Text(stringResource(R.string.tracker_no_fields), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (t.template == TrackerTemplate.STUDY) {
            item { StudySection(vm, s.date) }
        }
        item { StatsCard(s.summary) }
        item { HistoryGrid(s.summary, s.today, onPick = vm::setDate) }
    }
}

@Composable
fun DateSwitcher(date: LocalDate, today: LocalDate, onChange: (LocalDate) -> Unit) {
    val fmt = rememberDateFormatter("EEE, d MMM")
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onChange(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
        Text(
            when (date) {
                today -> stringResource(R.string.today)
                today.minusDays(1) -> stringResource(R.string.yesterday)
                else -> fmt(date)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).clickable { picking = true }.padding(8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { onChange(date.plusDays(1)) }, enabled = date < today) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next))
        }
    }
    if (picking) KDatePickerDialog(date, onDismiss = { picking = false }) { picking = false; onChange(it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldInput(field: TrackerField, value: FieldValue?, key: String, onChange: (FieldValue) -> Unit) {
    val fraction = TrackerScoring.fieldFraction(field, value)
    Column {
        when (field.type) {
            MeasurementType.CHECKBOX -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(field.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = (value?.number ?: 0.0) > 0.0, onCheckedChange = { onChange(FieldValue(field.id, number = if (it) 1.0 else null)) })
            }
            MeasurementType.RATING -> {
                Text(field.label, style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { r ->
                        val selected = value?.number?.toInt() == r
                        FilterChip(
                            selected = selected,
                            onClick = { onChange(FieldValue(field.id, number = if (selected) null else r.toDouble())) },
                            label = { Text(r.toString()) },
                        )
                    }
                }
            }
            MeasurementType.TEXT -> {
                var text by rememberSaveable(key) { mutableStateOf(value?.text.orEmpty()) }
                val loadedText = value?.text
                if (text.isEmpty() && !loadedText.isNullOrEmpty()) text = loadedText
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(4000); onChange(FieldValue(field.id, text = text)) },
                    label = { Text(field.label) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            MeasurementType.CHECKLIST -> {
                Text(field.label, style = MaterialTheme.typography.bodyLarge)
                field.options.forEachIndexed { i, option ->
                    val checked = i in value?.checked.orEmpty()
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val set = value?.checked.orEmpty()
                            onChange(FieldValue(field.id, checked = if (checked) set - i else set + i))
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            else -> NumberFieldInput(field, value, key, onChange)
        }
        if (fraction != null && field.type != MeasurementType.CHECKBOX && field.type != MeasurementType.RATING) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun NumberFieldInput(field: TrackerField, value: FieldValue?, key: String, onChange: (FieldValue) -> Unit) {
    val decimal = field.type == MeasurementType.DECIMAL
    var text by rememberSaveable(key) { mutableStateOf(value?.number?.let { formatNumber(it) }.orEmpty()) }
    // Draft values load asynchronously; adopt them once if the user has not typed yet.
    val loaded = value?.number?.let { formatNumber(it) }
    if (text.isEmpty() && loaded != null) text = loaded
    val unit = when (field.type) {
        MeasurementType.DURATION -> stringResource(R.string.unit_minutes)
        MeasurementType.PERCENTAGE -> "%"
        MeasurementType.PAGES -> field.unit.ifBlank { stringResource(R.string.unit_pages) }
        else -> field.unit
    }
    val step = when (field.type) {
        MeasurementType.DURATION -> 5.0
        MeasurementType.PERCENTAGE -> 10.0
        MeasurementType.DECIMAL -> if ((field.target ?: 0.0) >= 20) 1.0 else 0.5
        else -> if ((field.target ?: 0.0) >= 1000) 500.0 else 1.0
    }
    fun set(n: Double?) {
        val clean = n?.coerceIn(0.0, 1_000_000.0)
        text = clean?.let { formatNumber(it) }.orEmpty()
        onChange(FieldValue(field.id, number = clean))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.replace(',', '.').take(10)
                text = filtered
                onChange(FieldValue(field.id, number = filtered.toDoubleOrNull()?.coerceIn(0.0, 1_000_000.0)))
            },
            label = { Text(field.label) },
            suffix = { if (unit.isNotBlank()) Text(unit) },
            supportingText = field.target?.let { { Text(stringResource(R.string.field_target_line, formatNumber(it), unit)) } },
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { set(((text.toDoubleOrNull() ?: 0.0) - step).takeIf { it > 0 }) }) { Text("−") }
        TextButton(onClick = { set((text.toDoubleOrNull() ?: 0.0) + step) }) { Text("+") }
    }
}

fun formatNumber(n: Double): String =
    if (n == Math.floor(n) && n < 1e12) n.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, n).trimEnd('0').trimEnd('.')

@Composable
private fun StatsCard(summary: TrackerSummary) {
    val s = summary.stats
    KCard {
        SectionLabel(stringResource(R.string.tracker_stats))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat(streakText(s.currentStreak, s.unit), stringResource(R.string.stat_current_streak), Modifier.weight(1f))
            Stat(streakText(s.bestStreak, s.unit).removePrefix("🔥 "), stringResource(R.string.stat_best_streak), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("${(s.completionRate * 100).toInt()}%", stringResource(R.string.stat_completion_30), Modifier.weight(1f))
            Stat(s.activeDays.toString(), stringResource(R.string.stat_active_days), Modifier.weight(1f))
        }
        if (s.missedYesterday) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.tracker_missed_yesterday), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The last five weeks, Monday first. Each square's colour strength is that day's score. */
@Composable
private fun HistoryGrid(summary: TrackerSummary, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val fmt = rememberDateFormatter("d MMM")
    val color = Color(summary.tracker.colorArgb)
    val lastMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val start = lastMonday.minusWeeks(4)
    KCard {
        SectionLabel(stringResource(R.string.tracker_history))
        Spacer(Modifier.height(12.dp))
        for (week in 0 until 5) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in 0 until 7) {
                    val date = start.plusWeeks(week.toLong()).plusDays(d.toLong())
                    val score = summary.scores[date]
                    val future = date > today
                    val fill = when {
                        future -> Color.Transparent
                        score == null || score.fraction == 0.0 -> MaterialTheme.colorScheme.surfaceVariant
                        else -> color.copy(alpha = (0.25f + 0.75f * score.fraction.toFloat()))
                    }
                    val desc = stringResource(R.string.history_cell_a11y, fmt(date), ((score?.fraction ?: 0.0) * 100).toInt())
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(fill)
                            .then(if (future) Modifier else Modifier.clickable { onPick(date) })
                            .semantics { contentDescription = desc },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date == today) Text("•", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
