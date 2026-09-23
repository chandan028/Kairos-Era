package com.kairosera.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.settings.AppSettings
import com.kairosera.core.settings.HomeCard
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.domain.model.TaskOccurrence
import com.kairosera.domain.time.DayPart
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: AppSettings,
    onOpenToday: () -> Unit,
    onOpenTask: (Long, LocalDate) -> Unit,
    onQuickAdd: () -> Unit,
    onCustomize: () -> Unit,
) {
    val vm = kairosViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onQuickAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.quick_add_title)) }
        },
    ) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(onRetry = vm::retry, modifier = Modifier.padding(padding))
            is UiState.Ready -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                HomeContent(s.data, settings, vm, onOpenToday, onOpenTask, onCustomize)
            }
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    settings: AppSettings,
    vm: HomeViewModel,
    onOpenToday: () -> Unit,
    onOpenTask: (Long, LocalDate) -> Unit,
    onCustomize: () -> Unit,
) {
    val dateFmt = rememberDateFormatter("EEEE\nd MMMM yyyy")
    val cards = settings.homeCards.filterNot { it in settings.hiddenCards }
    LazyColumn(
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(data.dayPart, settings.name), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(dateFmt(data.date), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onCustomize) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.home_customize)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (card in cards) {
            when (card) {
                HomeCard.PROGRESS -> item(key = "progress") { ProgressCard(data, onOpenToday) }
                HomeCard.NEXT_UP -> item(key = "next") { NextUpCard(data, vm, onOpenTask, onOpenToday) }
                HomeCard.QUOTE -> item(key = "quote") {
                    QuoteCard(data, favorite = data.quote.dayOfYear in settings.favoriteQuotes, onFavorite = { vm.toggleFavorite(data.quote.dayOfYear) })
                }
                HomeCard.TRACKERS -> item(key = "trackers") { TrackersTeaserCard() }
            }
        }
        item {
            FilledTonalButton(onClick = onOpenToday, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.home_open_today))
            }
        }
    }
}

@Composable
private fun greeting(part: DayPart, name: String): String {
    val base = when (part) {
        DayPart.MORNING -> stringResource(R.string.greeting_morning)
        DayPart.AFTERNOON -> stringResource(R.string.greeting_afternoon)
        DayPart.EVENING -> stringResource(R.string.greeting_evening)
        DayPart.NIGHT -> stringResource(R.string.greeting_night)
    }
    val emoji = when (part) {
        DayPart.MORNING -> "🌅"
        DayPart.AFTERNOON -> "☀️"
        DayPart.EVENING -> "🌇"
        DayPart.NIGHT -> "🌙"
    }
    return if (name.isBlank()) "$base $emoji" else "$base, $name $emoji"
}

@Composable
private fun ProgressCard(data: HomeData, onOpenToday: () -> Unit) {
    val description = stringResource(R.string.home_progress_a11y, data.progress.done, data.progress.total)
    KCard(Modifier.clickable(onClick = onOpenToday).semantics(mergeDescendants = true) { contentDescription = description }) {
        SectionLabel(stringResource(R.string.home_today_progress))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { data.progress.fraction },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 7.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Text("${(data.progress.fraction * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(stringResource(R.string.home_tasks), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.home_tasks_done, data.progress.done, data.progress.total),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    when {
                        data.progress.total == 0 -> stringResource(R.string.home_nothing_planned)
                        data.progress.remaining == 0 -> stringResource(R.string.home_all_done)
                        else -> stringResource(R.string.home_remaining, data.progress.remaining)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NextUpCard(data: HomeData, vm: HomeViewModel, onOpenTask: (Long, LocalDate) -> Unit, onOpenToday: () -> Unit) {
    val timeFmt = rememberTimeFormatter()
    KCard {
        SectionLabel(stringResource(R.string.home_next_up))
        Spacer(Modifier.height(8.dp))
        if (data.nextUp.isEmpty()) {
            Text(
                stringResource(if (data.progress.total == 0) R.string.home_next_empty else R.string.home_next_clear),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpenToday).padding(vertical = 8.dp),
            )
        }
        data.nextUp.forEach { occ -> NextRow(occ, timeFmt, onClick = { onOpenTask(occ.task.id, occ.date) }, onDone = { vm.setDone(occ, it) }) }
    }
}

@Composable
private fun NextRow(occ: TaskOccurrence, timeFmt: (java.time.LocalTime) -> String, onClick: () -> Unit, onDone: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            occ.task.startTime?.let(timeFmt) ?: stringResource(R.string.all_day_short),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(76.dp),
        )
        Text(occ.task.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2)
        Checkbox(checked = occ.isDone, onCheckedChange = onDone)
    }
}

@Composable
private fun QuoteCard(data: HomeData, favorite: Boolean, onFavorite: () -> Unit) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.home_daily_motivation), Modifier.weight(1f))
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(if (favorite) R.string.quote_unfavorite else R.string.quote_favorite),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Text("“${data.quote.quote}”", style = MaterialTheme.typography.titleLarge, fontStyle = FontStyle.Normal)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.quote_action, data.quote.actionPrompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackersTeaserCard() {
    KCard {
        SectionLabel(stringResource(R.string.home_trackers))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_trackers_soon), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

