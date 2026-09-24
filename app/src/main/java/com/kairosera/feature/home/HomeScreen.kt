package com.kairosera.feature.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.settings.AppSettings
import com.kairosera.core.settings.HomeCard
import com.kairosera.core.ui.components.BrandCard
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.EmojiBadge
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.Hairline
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.KProgressBar
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.PrimaryWideButton
import com.kairosera.core.ui.components.SectionHeader
import com.kairosera.core.ui.components.StatTile
import com.kairosera.core.ui.components.TaskCheck
import com.kairosera.core.ui.components.ToastKind
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.core.ui.components.toneFor
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.OverlineStyle
import com.kairosera.core.ui.theme.QuoteStyle
import com.kairosera.core.ui.theme.Space
import com.kairosera.domain.model.TaskOccurrence
import com.kairosera.domain.time.DayPart
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate
import java.time.LocalTime

/**
 * Home answers one question: what matters right now?
 * A greeting, today's thought, two numbers, the next few things, and one way to add.
 */
@Composable
fun HomeScreen(
    settings: AppSettings,
    onOpenToday: () -> Unit,
    onOpenTask: (Long, LocalDate) -> Unit,
    onQuickAdd: () -> Unit,
    onCustomize: () -> Unit,
    onOpenTracker: (Long) -> Unit,
    onOpenTrack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenQuote: () -> Unit,
) {
    val vm = kairosViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (val s = state) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(onRetry = vm::retry)
            is UiState.Ready -> HomeContent(s.data, settings, vm, onOpenToday, onOpenTask, onQuickAdd, onCustomize, onOpenTracker, onOpenTrack, onOpenBook, onOpenQuote)
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
    onQuickAdd: () -> Unit,
    onCustomize: () -> Unit,
    onOpenTracker: (Long) -> Unit,
    onOpenTrack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenQuote: () -> Unit,
) {
    val dateFmt = rememberDateFormatter("EEEE, d MMMM")
    val cards = settings.homeCards.filterNot { it in settings.hiddenCards }
    LazyColumn(
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = 0.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        item(key = "header") {
            Row(Modifier.statusBarsPadding().padding(top = 20.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(data.dayPart, settings.name), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(dateFmt(data.date), style = MaterialTheme.typography.bodyLarge, color = Kairos.colors.muted)
                }
                IconButton(onClick = onCustomize) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.home_customize), tint = Kairos.colors.muted) }
            }
        }
        for (card in cards) {
            when (card) {
                HomeCard.QUOTE -> item(key = "quote") {
                    ThoughtCard(data, favorite = data.quote.dayOfYear in settings.favoriteQuotes, onFavorite = { vm.toggleFavorite(data.quote.dayOfYear) }, onOpen = onOpenQuote)
                }
                HomeCard.PROGRESS -> item(key = "progress") { TodayTiles(data, onOpenToday) }
                HomeCard.NEXT_UP -> item(key = "next") { NextUp(data, vm, onOpenTask, onOpenToday) }
                HomeCard.TRACKERS -> item(key = "keep") { KeepGoing(data, onOpenTracker, onOpenTrack, onOpenBook) }
            }
        }
        item(key = "add") {
            PrimaryWideButton(stringResource(R.string.home_quick_add), onClick = onQuickAdd, icon = Icons.Filled.Add, modifier = Modifier.padding(top = Space.s))
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
        DayPart.MORNING -> "☀️"
        DayPart.AFTERNOON -> "🌤️"
        DayPart.EVENING -> "🌇"
        DayPart.NIGHT -> "🌙"
    }
    return if (name.isBlank()) "$base $emoji" else "$base, $name $emoji"
}

/** The emotional center: today's thought on deep navy, with the one small action it suggests. */
@Composable
private fun ThoughtCard(data: HomeData, favorite: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit) {
    BrandCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_thought).uppercase(), style = OverlineStyle, color = Kairos.colors.sun, modifier = Modifier.weight(1f))
            IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(if (favorite) R.string.quote_unfavorite else R.string.quote_favorite),
                    tint = Kairos.colors.sun,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("“${data.quote.quote}”", style = QuoteStyle)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.quote_action, data.quote.actionPrompt),
                style = MaterialTheme.typography.bodySmall,
                color = Kairos.colors.onBrand.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Kairos.colors.sun, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TodayTiles(data: HomeData, onOpenToday: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.today))
        Spacer(Modifier.height(Space.s))
        val a11y = stringResource(R.string.home_progress_a11y, data.progress.done, data.progress.total)
        Row(Modifier.semantics(mergeDescendants = true) { contentDescription = a11y }, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            StatTile(
                value = "${data.progress.done}/${data.progress.total}",
                label = stringResource(R.string.home_stat_tasks),
                modifier = Modifier.weight(1f),
                onClick = onOpenToday,
            )
            StatTile(
                value = "${(data.overall * 100).toInt()}%",
                label = stringResource(R.string.home_stat_progress),
                modifier = Modifier.weight(1f),
                tone = Kairos.colors.success,
                onClick = onOpenToday,
            )
        }
    }
}

@Composable
private fun NextUp(data: HomeData, vm: HomeViewModel, onOpenTask: (Long, LocalDate) -> Unit, onOpenToday: () -> Unit) {
    val timeFmt = rememberTimeFormatter()
    val toaster = LocalToaster.current
    val undo = stringResource(R.string.undo)
    val doneText = stringResource(R.string.task_done_toast)
    Column {
        SectionHeader(stringResource(R.string.home_next_up), action = stringResource(R.string.home_see_day), onAction = onOpenToday)
        Spacer(Modifier.height(Space.s))
        KCard(padding = 4.dp) {
            if (data.nextUp.isEmpty()) {
                Text(
                    stringResource(
                        when {
                            data.progress.total == 0 -> R.string.home_next_empty
                            data.progress.remaining == 0 -> R.string.home_all_done
                            else -> R.string.home_next_clear
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Kairos.colors.muted,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenToday).padding(16.dp),
                )
            }
            data.nextUp.forEachIndexed { i, occ ->
                if (i > 0) Hairline(Modifier.padding(start = 56.dp, end = 16.dp))
                NextRow(
                    occ = occ,
                    meta = taskMeta(occ.task.startTime, data.categories[occ.task.categoryId]?.name, timeFmt),
                    onClick = { onOpenTask(occ.task.id, occ.date) },
                    onDone = { done ->
                        vm.setDone(occ, done)
                        if (done) toaster.show(doneText, ToastKind.SUCCESS, action = undo) { vm.setDone(occ, false) }
                    },
                )
            }
        }
    }
}

@Composable
private fun NextRow(occ: TaskOccurrence, meta: String, onClick: () -> Unit, onDone: (Boolean) -> Unit) {
    // Local state lets the tick animate before the row leaves "next up".
    var checked by remember(occ.task.id, occ.date, occ.isDone) { mutableStateOf(occ.isDone) }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheck(
            checked = checked,
            onCheckedChange = { checked = it; onDone(it) },
            label = stringResource(R.string.task_mark_done_a11y, occ.task.title),
        )
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                occ.task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = if (checked) Kairos.colors.muted else MaterialTheme.colorScheme.onSurface,
            )
            Text(meta, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, maxLines = 1)
        }
        if (occ.task.reminders.isNotEmpty()) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = stringResource(R.string.has_reminder), tint = Kairos.colors.muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** "08:00 AM · Study" — the one small line of context under a task title. */
@Composable
fun taskMeta(start: LocalTime?, category: String?, timeFmt: (LocalTime) -> String): String =
    listOfNotNull(start?.let(timeFmt) ?: stringResource(R.string.all_day_short), category).joinToString(" · ")

/** Up to three things already in motion: trackers due today and the book being read. */
@Composable
private fun KeepGoing(data: HomeData, onOpenTracker: (Long) -> Unit, onOpenTrack: () -> Unit, onOpenBook: (Long) -> Unit) {
    val due = data.trackersDue.take(if (data.currentBook != null) 2 else 3)
    if (due.isEmpty() && data.currentBook == null) return
    Column {
        SectionHeader(stringResource(R.string.home_keep_going), action = stringResource(R.string.see_all), onAction = onOpenTrack)
        Spacer(Modifier.height(Space.s))
        KCard(padding = 4.dp) {
            due.forEachIndexed { i, t ->
                if (i > 0) Hairline(Modifier.padding(start = 68.dp, end = 16.dp))
                val tone = toneFor(t.tracker.template)
                MotionRow(
                    badge = { EmojiBadge(t.tracker.icon, tone, size = 40.dp) },
                    title = t.tracker.name,
                    detail = if (t.today.done) stringResource(R.string.done) else "${(t.today.fraction * 100).toInt()}%",
                    fraction = t.today.fraction.toFloat(),
                    tone = tone,
                    onClick = { onOpenTracker(t.tracker.id) },
                )
            }
            data.currentBook?.let { book ->
                if (due.isNotEmpty()) Hairline(Modifier.padding(start = 68.dp, end = 16.dp))
                val tone = Kairos.colors.motivation
                MotionRow(
                    badge = { EmojiBadge("📖", tone, size = 40.dp) },
                    title = book.title,
                    detail = "${(book.progress * 100).toInt()}%",
                    fraction = book.progress.toFloat(),
                    tone = tone,
                    onClick = { onOpenBook(book.id) },
                )
            }
        }
    }
}

@Composable
private fun MotionRow(
    badge: @Composable () -> Unit,
    title: String,
    detail: String,
    fraction: Float,
    tone: com.kairosera.core.ui.theme.Tone,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badge()
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = tone.strong)
            }
            Spacer(Modifier.height(6.dp))
            KProgressBar(fraction, color = tone.strong, height = 6.dp)
        }
    }
}
