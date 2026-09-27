package com.kairosera.feature.track

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.KProgressBar
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.PrimaryWideButton
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.StatTile
import com.kairosera.core.ui.components.TaskCheck
import com.kairosera.core.ui.components.ToastKind
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.components.toneFor
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.Space
import com.kairosera.core.ui.theme.Tone
import com.kairosera.domain.tracker.FieldValue
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.TrackerEntry
import com.kairosera.domain.tracker.TrackerField
import com.kairosera.domain.tracker.TrackerScoring
import com.kairosera.domain.tracker.TrackerTemplate
import java.time.LocalDate
import com.kairosera.ui.kairosViewModel

@Composable
fun TrackerDetailScreen(trackerId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm = kairosViewModel(key = "tracker-$trackerId") { TrackerDetailViewModel(it, trackerId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }
    DisposableEffect(vm) { onDispose { vm.flush() } }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = (state as? DetailState.Ready)?.summary?.tracker?.name.orEmpty(),
            onBack = onBack,
        ) {
            if (state is DetailState.Ready) {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_tracker)) }
                IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.move_to_trash)) }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
    val tone = toneFor(t.template)
    val dayScore = TrackerScoring.score(t, TrackerEntry(t.id, s.date, draft))
    val toaster = LocalToaster.current
    val savedText = stringResource(R.string.tracker_logged)
    val isStudy = t.template == TrackerTemplate.STUDY
    LazyColumn(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.s, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        if (t.why.isNotBlank()) item(key = "why") { WhyCard(t.why) }
        if (isStudy) {
            item(key = "study") { StudySection(vm, s.date, tone, t.gain) }
        }
        item(key = "log") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(if (isStudy) R.string.tracker_todays_log else R.string.tracker_daily_log), Modifier.weight(1f))
                    Text(
                        if (dayScore.done) stringResource(R.string.done) else "${(dayScore.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (dayScore.done) Kairos.colors.success.strong else Kairos.colors.muted,
                    )
                }
                DateSwitcher(s.date, s.today, vm::setDate)
                QuickLog(t.enabledFields, draft, s.date, tone, vm::update)
                if (t.enabledFields.isEmpty()) {
                    Text(stringResource(R.string.tracker_no_fields), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
                } else if (!isStudy) {
                    PrimaryWideButton(stringResource(R.string.save), onClick = { vm.flush(); toaster.show(savedText, ToastKind.SUCCESS) })
                }
            }
        }
        if (!isStudy && t.gain.isNotBlank()) item(key = "gain") { GainCard(t.gain, tone) }
        item(key = "stats") { StatsRow(s.summary) }
        item(key = "history") { HistoryGrid(s.summary, s.today, tone, onPick = vm::setDate) }
    }
}

/** "Your why" in the person's own words: the reason this tracker exists. */
@Composable
fun WhyCard(why: String) {
    com.kairosera.core.ui.components.BrandCard {
        Text(stringResource(R.string.tracker_your_why).uppercase(), style = com.kairosera.core.ui.theme.OverlineStyle, color = Kairos.colors.sun)
        Spacer(Modifier.height(8.dp))
        Text("“$why”", style = com.kairosera.core.ui.theme.QuoteStyle)
    }
}

/** "What you're gaining": one line per benefit, split on new lines, commas or semicolons. */
@Composable
fun GainCard(gain: String, tone: Tone) {
    val items = gain.split('\n', ';', ',').map { it.trim() }.filter { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionLabel(stringResource(R.string.tracker_gaining))
        KCard(padding = 16.dp) {
            items.forEachIndexed { i, line ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(tone.strong))
                    Spacer(Modifier.width(12.dp))
                    Text(line, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun DateSwitcher(date: LocalDate, today: LocalDate, onChange: (LocalDate) -> Unit) {
    val fmt = rememberDateFormatter("EEE, d MMM")
    var picking by rememberSaveable { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = Kairos.colors.card, border = BorderStroke(1.dp, Kairos.colors.line)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { onChange(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
            Text(
                when (date) {
                    today -> stringResource(R.string.today)
                    today.minusDays(1) -> stringResource(R.string.yesterday)
                    else -> fmt(date)
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).clickable { picking = true }.padding(8.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onChange(date.plusDays(1)) }, enabled = date < today) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next))
            }
        }
    }
    if (picking) KDatePickerDialog(date, onDismiss = { picking = false }) { picking = false; onChange(it) }
}

private fun isCircleField(f: TrackerField): Boolean =
    f.type == MeasurementType.NUMBER && f.target != null && f.target!! >= 1.0 && f.target!! <= 12.0 && f.target!! == Math.floor(f.target!!)

private val TileTypes = setOf(MeasurementType.NUMBER, MeasurementType.DECIMAL, MeasurementType.DURATION, MeasurementType.PAGES, MeasurementType.PERCENTAGE)

/**
 * The quick daily log, designed to take under 20 seconds:
 * number tiles two by two, small counts (like glasses of water) as tappable circles,
 * yes/no items and checklists as tap rows, ratings as 1–5, and a note last.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickLog(fields: List<TrackerField>, draft: Map<Long, FieldValue>, date: LocalDate, tone: Tone, onChange: (FieldValue) -> Unit) {
    val tiles = fields.filter { it.type in TileTypes && !isCircleField(it) }
    val circles = fields.filter(::isCircleField)
    val checks = fields.filter { it.type == MeasurementType.CHECKBOX }
    val lists = fields.filter { it.type == MeasurementType.CHECKLIST }
    val ratings = fields.filter { it.type == MeasurementType.RATING }
    val notes = fields.filter { it.type == MeasurementType.TEXT }
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                row.forEach { f -> NumberTile(f, draft[f.id], "$date-${f.id}", tone, onChange, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        circles.forEach { f -> CircleCounter(f, draft[f.id], tone, onChange) }
        if (checks.isNotEmpty() || lists.isNotEmpty()) {
            KCard(padding = 4.dp) {
                checks.forEach { f ->
                    val on = (draft[f.id]?.number ?: 0.0) > 0.0
                    CheckLine(f.label, on) { onChange(FieldValue(f.id, number = if (it) 1.0 else null)) }
                }
                lists.forEach { f ->
                    Text(f.label.uppercase(), style = com.kairosera.core.ui.theme.OverlineStyle, color = Kairos.colors.muted, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                    f.options.forEachIndexed { i, option ->
                        val set = draft[f.id]?.checked.orEmpty()
                        CheckLine(option, i in set) { on -> onChange(FieldValue(f.id, checked = if (on) set + i else set - i)) }
                    }
                }
            }
        }
        ratings.forEach { f -> RatingRow(f, draft[f.id], tone, onChange) }
        notes.forEach { f -> NoteField(f, draft[f.id], "$date-${f.id}", onChange) }
    }
}

@Composable
private fun CheckLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onChange(!checked) }.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskCheck(checked = checked, onCheckedChange = onChange, label = label)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun unitFor(field: TrackerField): String = when (field.type) {
    MeasurementType.DURATION -> stringResource(R.string.unit_minutes)
    MeasurementType.PERCENTAGE -> "%"
    MeasurementType.PAGES -> field.unit.ifBlank { stringResource(R.string.unit_pages) }
    else -> field.unit
}

/** A 2×2 tile: label, big editable number, − and + for one-thumb logging. */
@Composable
private fun NumberTile(field: TrackerField, value: FieldValue?, key: String, tone: Tone, onChange: (FieldValue) -> Unit, modifier: Modifier) {
    val decimal = field.type == MeasurementType.DECIMAL
    var text by rememberSaveable(key) { mutableStateOf(value?.number?.let { formatNumber(it) }.orEmpty()) }
    // Draft values load asynchronously; adopt them once if the person has not typed yet.
    val loaded = value?.number?.let { formatNumber(it) }
    if (text.isEmpty() && loaded != null) text = loaded
    val unit = unitFor(field)
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
    val fraction = TrackerScoring.fieldFraction(field, value)
    KCard(modifier, padding = 14.dp) {
        Text(field.label, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.replace(',', '.').take(10)
                    text = filtered
                    onChange(FieldValue(field.id, number = filtered.toDoubleOrNull()?.coerceIn(0.0, 1_000_000.0)))
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(tone.strong),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("0", style = MaterialTheme.typography.headlineSmall, color = Kairos.colors.muted.copy(alpha = 0.5f))
                        inner()
                    }
                },
                modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 24.dp).semantics { contentDescription = field.label },
            )
            if (unit.isNotBlank()) Text(" $unit", style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, modifier = Modifier.padding(bottom = 6.dp), maxLines = 1)
        }
        field.target?.let {
            Text(stringResource(R.string.field_target_short, formatNumber(it)), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
        }
        if (fraction != null) {
            Spacer(Modifier.height(6.dp))
            KProgressBar(fraction.toFloat(), color = tone.strong, height = 4.dp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            StepButton("−", stringResource(R.string.decrease_value, field.label)) { set(((text.toDoubleOrNull() ?: 0.0) - step).takeIf { it > 0 }) }
            StepButton("+", stringResource(R.string.increase_value, field.label)) { set((text.toDoubleOrNull() ?: 0.0) + step) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(BorderStroke(1.dp, Kairos.colors.line), CircleShape).clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
}

/** Small counts (e.g. 8 glasses of water) as circles: tap the fifth to log five; tap it again to go back to four. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CircleCounter(field: TrackerField, value: FieldValue?, tone: Tone, onChange: (FieldValue) -> Unit) {
    val target = field.target?.toInt() ?: return
    val count = value?.number?.toInt() ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(field.label, Modifier.weight(1f))
            Text("$count / $target ${field.unit}".trim(), style = MaterialTheme.typography.labelLarge, color = tone.strong)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (1..target).forEach { i ->
                val filled = i <= count
                val desc = stringResource(R.string.circle_value_a11y, field.label, i)
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (filled) tone.strong else Color.Transparent)
                        .border(BorderStroke(2.dp, if (filled) tone.strong else Kairos.colors.line), CircleShape)
                        .clickable { onChange(FieldValue(field.id, number = (if (i == count) i - 1 else i).toDouble().takeIf { it > 0 })) }
                        .semantics { contentDescription = desc },
                )
            }
        }
    }
}

@Composable
private fun RatingRow(field: TrackerField, value: FieldValue?, tone: Tone, onChange: (FieldValue) -> Unit) {
    val current = value?.number?.toInt()
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionLabel(field.label)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (1..5).forEach { r ->
                val selected = current == r
                Box(
                    Modifier.weight(1f).aspectRatio(1f).widthIn(max = 56.dp).clip(CircleShape)
                        .background(if (selected) tone.strong else Kairos.colors.card)
                        .border(BorderStroke(1.dp, if (selected) tone.strong else Kairos.colors.line), CircleShape)
                        .clickable { onChange(FieldValue(field.id, number = if (selected) null else r.toDouble())) }
                        .semantics { contentDescription = "${field.label} $r" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(r.toString(), style = MaterialTheme.typography.titleMedium, color = if (selected) Kairos.colors.card else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun NoteField(field: TrackerField, value: FieldValue?, key: String, onChange: (FieldValue) -> Unit) {
    var text by rememberSaveable(key) { mutableStateOf(value?.text.orEmpty()) }
    val loadedText = value?.text
    if (text.isEmpty() && !loadedText.isNullOrEmpty()) text = loadedText
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(4000); onChange(FieldValue(field.id, text = text)) },
        label = { Text(field.label) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        minLines = 2,
    )
}

fun formatNumber(n: Double): String =
    if (n == Math.floor(n) && n < 1e12) n.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, n).trimEnd('0').trimEnd('.')

@Composable
private fun StatsRow(summary: TrackerSummary) {
    val s = summary.stats
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        SectionLabel(stringResource(R.string.tracker_stats))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            StatTile(streakText(s.currentStreak, s.unit), stringResource(R.string.stat_current_streak), Modifier.weight(1f), tone = Kairos.colors.motivation)
            StatTile(streakText(s.bestStreak, s.unit).removePrefix("🔥 "), stringResource(R.string.stat_best_streak), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            StatTile("${(s.completionRate * 100).toInt()}%", stringResource(R.string.stat_completion_30), Modifier.weight(1f), tone = Kairos.colors.success)
            StatTile(s.activeDays.toString(), stringResource(R.string.stat_active_days), Modifier.weight(1f))
        }
        if (s.missedYesterday) {
            Text(stringResource(R.string.tracker_missed_yesterday), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        }
    }
}

/** The last five weeks, Monday first. Each square's strength is that day's score. */
@Composable
private fun HistoryGrid(summary: TrackerSummary, today: LocalDate, tone: Tone, onPick: (LocalDate) -> Unit) {
    val fmt = rememberDateFormatter("d MMM")
    val lastMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val start = lastMonday.minusWeeks(4)
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        SectionLabel(stringResource(R.string.tracker_history))
        KCard(padding = 16.dp) {
            for (week in 0 until 5) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 0 until 7) {
                        val date = start.plusWeeks(week.toLong()).plusDays(d.toLong())
                        val score = summary.scores[date]
                        val future = date > today
                        val fill = when {
                            future -> Color.Transparent
                            score == null || score.fraction == 0.0 -> Kairos.colors.track
                            else -> tone.strong.copy(alpha = (0.25f + 0.75f * score.fraction.toFloat()))
                        }
                        val desc = stringResource(R.string.history_cell_a11y, fmt(date), ((score?.fraction ?: 0.0) * 100).toInt())
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(fill)
                                .then(if (date == today) Modifier.border(BorderStroke(2.dp, Kairos.colors.sun), RoundedCornerShape(8.dp)) else Modifier)
                                .then(if (future) Modifier else Modifier.clickable { onPick(date) })
                                .semantics { contentDescription = desc },
                        )
                    }
                }
                if (week < 4) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

