package com.kairosera.feature.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.EmojiBadge
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.GhostAddButton
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.KProgressBar
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.PrimaryWideButton
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.TaskCheck
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.components.toneFor
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.Space
import com.kairosera.core.ui.theme.Tone
import com.kairosera.data.tracker.TrackerTemplates
import com.kairosera.domain.reading.Book
import com.kairosera.domain.tracker.FieldValue
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.StreakUnit
import com.kairosera.domain.tracker.StudySummary
import com.kairosera.domain.tracker.TrackerField
import com.kairosera.domain.tracker.TrackerTemplate
import com.kairosera.ui.kairosViewModel
import java.text.NumberFormat

/** Track: "your progress" as a few honest cards, one per thing you're growing. */
@Composable
fun TrackScreen(onOpenTracker: (Long) -> Unit, onNewTracker: (TrackerTemplate) -> Unit, onOpenBook: (Long) -> Unit = {}) {
    val vm = kairosViewModel { TrackViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_track), subtitle = stringResource(R.string.track_subtitle)) {
            IconButton(onClick = { picking = true }) { Icon(Icons.Filled.Add, stringResource(R.string.new_tracker)) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(onRetry = vm::retry)
                is UiState.Ready -> if (s.data.trackers.isEmpty()) {
                    MessageState(Icons.Outlined.Insights, stringResource(R.string.track_empty_title), stringResource(R.string.track_empty_body)) {
                        PrimaryWideButton(stringResource(R.string.track_create_own), onClick = { picking = true }, icon = Icons.Outlined.Add)
                    }
                } else {
                    LazyColumn(
                        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
                        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.s, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.m),
                    ) {
                        val due = s.data.trackers.filter { it.dueToday }
                        item(key = "label") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionLabel(stringResource(R.string.track_active), Modifier.weight(1f))
                                Text(
                                    stringResource(R.string.track_today_summary, due.count { it.today.done }, due.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Kairos.colors.success.strong,
                                )
                            }
                        }
                        items(s.data.trackers, key = { it.tracker.id }) { t ->
                            TrackerCard(
                                t,
                                study = s.data.study[t.tracker.id],
                                targets = s.data.targetsToday[t.tracker.id],
                                onClick = { onOpenTracker(t.tracker.id) },
                                onToggle = { vm.toggleMain(t) },
                            )
                        }
                        s.data.currentBook?.let { book ->
                            item(key = "book") { ReadingCard(book, onClick = { onOpenBook(book.id) }) }
                        }
                        item(key = "create") {
                            GhostAddButton(stringResource(R.string.track_create_own), onClick = { picking = true }, icon = Icons.Outlined.Add, modifier = Modifier.padding(top = Space.s))
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        TemplatePickerSheet(onDismiss = { picking = false }, onPick = { picking = false; onNewTracker(it) })
    }
}

/**
 * One card per tracker, shaped by what it measures:
 * study shows topics mastered and today's plan; others show today's key values.
 */
@Composable
fun TrackerCard(t: TrackerSummary, study: StudySummary?, targets: Pair<Int, Int>?, onClick: () -> Unit, onToggle: (() -> Unit)?) {
    val tone = toneFor(t.tracker.template)
    val percent = (t.today.fraction * 100).toInt()
    val a11y = stringResource(R.string.tracker_card_a11y, t.tracker.name, percent, t.stats.currentStreak)
    val isStudy = t.tracker.template == TrackerTemplate.STUDY && study != null
    KCard(Modifier.semantics(mergeDescendants = false) { contentDescription = a11y }, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(t.tracker.icon, tone)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.tracker.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        t.stats.currentStreak > 0 -> streakText(t.stats.currentStreak, t.stats.unit)
                        !t.dueToday -> stringResource(R.string.tracker_rest_day)
                        t.today.done -> stringResource(R.string.tracker_done_today)
                        else -> stringResource(R.string.tracker_percent_today, percent)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (t.stats.currentStreak > 0) Kairos.colors.motivation.strong else Kairos.colors.muted,
                )
            }
            val main = t.tracker.enabledFields.firstOrNull()
            if (onToggle != null && main?.type == MeasurementType.CHECKBOX) {
                TaskCheck(
                    checked = (t.todayEntry?.values?.get(main.id)?.number ?: 0.0) > 0.0,
                    onCheckedChange = { onToggle() },
                    label = main.label,
                )
            } else if (!isStudy) {
                Text("$percent%", style = MaterialTheme.typography.titleSmall, color = tone.strong)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (isStudy && study != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KProgressBar(study.fraction.toFloat(), color = tone.strong, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text("${(study.fraction * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = tone.strong)
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Text(stringResource(R.string.track_topics_line, study.done, study.leafTopics), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, modifier = Modifier.weight(1f))
                if (targets != null && targets.second > 0) {
                    Text(stringResource(R.string.track_today_line, targets.first, targets.second), style = MaterialTheme.typography.bodySmall, color = tone.strong)
                }
            }
        } else {
            KProgressBar(t.today.fraction.toFloat(), color = tone.strong, height = 6.dp)
            val facts = keyFacts(t.tracker.enabledFields, t.todayEntry?.values.orEmpty())
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(facts.joinToString("   ·   "), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Today's values in a few words: "6,432 steps · Workout ✓ · Energy 4/5". */
@Composable
private fun keyFacts(fields: List<TrackerField>, values: Map<Long, FieldValue>): List<String> {
    val nf = NumberFormat.getInstance()
    val out = mutableListOf<String>()
    for (f in fields) {
        if (out.size >= 3) break
        val v = values[f.id]
        val n = v?.number
        val text = when (f.type) {
            MeasurementType.CHECKBOX -> "${f.label} ${if ((n ?: 0.0) > 0) "✓" else "–"}"
            MeasurementType.RATING -> n?.let { "${f.label} ${it.toInt()}/5" }
            MeasurementType.CHECKLIST -> "${f.label} ${v?.checked?.size ?: 0}/${f.options.size}"
            MeasurementType.TEXT -> null
            MeasurementType.DURATION -> n?.let { "${nf.format(it)} ${stringResource(R.string.unit_minutes)} ${f.label.lowercase()}" }
            MeasurementType.PERCENTAGE -> n?.let { "${f.label} ${nf.format(it)}%" }
            else -> n?.let { "${nf.format(it)} ${f.unit.ifBlank { f.label.lowercase() }}" }
        }
        if (text != null) out += text
    }
    return out
}

@Composable
private fun ReadingCard(book: Book, onClick: () -> Unit) {
    val tone = Kairos.colors.motivation
    KCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge("📖", tone)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.template_reading), style = MaterialTheme.typography.titleMedium)
                Text(book.title, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = tone.strong)
        }
        Spacer(Modifier.height(14.dp))
        KProgressBar(book.progress.toFloat(), color = tone.strong, height = 6.dp)
    }
}

@Composable
fun streakText(count: Int, unit: StreakUnit): String = "🔥 " + when (unit) {
    StreakUnit.DAY -> pluralStringResource(R.plurals.streak_days, count, count)
    StreakUnit.WEEK -> pluralStringResource(R.plurals.streak_weeks, count, count)
    StreakUnit.MONTH -> pluralStringResource(R.plurals.streak_months, count, count)
}

/** Create a tracker: pick a starting point from a calm grid; every field stays editable afterwards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(onDismiss: () -> Unit, onPick: (TrackerTemplate) -> Unit) {
    val main = listOf(
        TrackerTemplate.STUDY, TrackerTemplate.FITNESS, TrackerTemplate.READING,
        TrackerTemplate.HABIT, TrackerTemplate.HEALTH, TrackerTemplate.PROJECT,
        TrackerTemplate.LEARNING, TrackerTemplate.FINANCE, TrackerTemplate.PERSONAL,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xl).navigationBarsPadding()) {
            Text(stringResource(R.string.template_pick_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.template_pick_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Kairos.colors.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = Space.l),
            )
            main.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    row.forEach { t -> TemplateTile(t, toneFor(t), Modifier.weight(1f)) { onPick(t) } }
                }
            }
            GhostAddButton(stringResource(R.string.template_start_blank), onClick = { onPick(TrackerTemplate.CUSTOM) }, icon = Icons.Outlined.Add)
        }
    }
}

@Composable
private fun TemplateTile(t: TrackerTemplate, tone: Tone, modifier: Modifier, onClick: () -> Unit) {
    KCard(modifier, onClick = onClick, padding = 12.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmojiBadge(TrackerTemplates.icon(t), tone, size = 48.dp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(TrackerTemplates.nameRes(t)), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
