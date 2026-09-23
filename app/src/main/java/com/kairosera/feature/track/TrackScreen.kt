package com.kairosera.feature.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.UiState
import com.kairosera.data.tracker.TrackerTemplates
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.StreakUnit
import com.kairosera.domain.tracker.TrackerTemplate
import com.kairosera.ui.kairosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(onOpenTracker: (Long) -> Unit, onNewTracker: (TrackerTemplate) -> Unit) {
    val vm = kairosViewModel { TrackViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_track)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_tracker)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(onRetry = vm::retry)
                is UiState.Ready -> if (s.data.trackers.isEmpty()) {
                    MessageState(Icons.Outlined.Insights, stringResource(R.string.track_empty_title), stringResource(R.string.track_empty_body)) {
                        OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.new_tracker)) }
                    }
                } else {
                    LazyColumn(
                        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val due = s.data.trackers.filter { it.dueToday }
                        val done = due.count { it.today.done }
                        item { SectionLabel(stringResource(R.string.track_today_summary, done, due.size), Modifier.padding(top = 8.dp, start = 4.dp)) }
                        items(s.data.trackers, key = { it.tracker.id }) { t ->
                            TrackerCard(t, onClick = { onOpenTracker(t.tracker.id) }, onToggle = { vm.toggleMain(t) })
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

@Composable
fun TrackerCard(t: TrackerSummary, onClick: () -> Unit, onToggle: (() -> Unit)?) {
    val percent = (t.today.fraction * 100).toInt()
    val a11y = stringResource(R.string.tracker_card_a11y, t.tracker.name, percent, t.stats.currentStreak)
    KCard(Modifier.clickable(onClick = onClick).semantics(mergeDescendants = false) { contentDescription = a11y }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrackerBadge(t.tracker.icon, t.tracker.colorArgb)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.tracker.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    when {
                        !t.dueToday -> stringResource(R.string.tracker_rest_day)
                        t.today.done -> stringResource(R.string.tracker_done_today)
                        else -> stringResource(R.string.tracker_percent_today, percent)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (t.stats.currentStreak > 0) {
                Text(streakText(t.stats.currentStreak, t.stats.unit), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
            val main = t.tracker.enabledFields.firstOrNull()
            if (onToggle != null && main?.type == MeasurementType.CHECKBOX) {
                Checkbox(checked = (t.todayEntry?.values?.get(main.id)?.number ?: 0.0) > 0.0, onCheckedChange = { onToggle() })
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { t.today.fraction.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Color(t.tracker.colorArgb),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun TrackerBadge(icon: String, color: Long, size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(color).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Text(icon, style = if (size >= 44) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall) }
}

@Composable
fun streakText(count: Int, unit: StreakUnit): String = "🔥 " + when (unit) {
    StreakUnit.DAY -> pluralStringResource(R.plurals.streak_days, count, count)
    StreakUnit.WEEK -> pluralStringResource(R.plurals.streak_weeks, count, count)
    StreakUnit.MONTH -> pluralStringResource(R.plurals.streak_months, count, count)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(onDismiss: () -> Unit, onPick: (TrackerTemplate) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Text(stringResource(R.string.template_pick_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                Text(
                    stringResource(R.string.template_pick_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            items(TrackerTemplate.entries) { t ->
                ListItem(
                    headlineContent = { Text(stringResource(TrackerTemplates.nameRes(t))) },
                    supportingContent = { Text(stringResource(TrackerTemplates.summaryRes(t))) },
                    leadingContent = { TrackerBadge(TrackerTemplates.icon(t), TrackerTemplates.COLORS[t.ordinal % TrackerTemplates.COLORS.size], 40) },
                    modifier = Modifier.clickable { onPick(t) },
                )
            }
        }
    }
}
