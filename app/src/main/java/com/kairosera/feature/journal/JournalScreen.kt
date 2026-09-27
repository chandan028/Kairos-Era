package com.kairosera.feature.journal

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.domain.journal.JournalEntry
import com.kairosera.domain.journal.Mood
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate

/** The reflection prompts, one a day, so the page never feels like the same form. */
private val PROMPTS = listOf(
    R.string.journal_prompt_1, R.string.journal_prompt_2, R.string.journal_prompt_3, R.string.journal_prompt_4,
    R.string.journal_prompt_5, R.string.journal_prompt_6, R.string.journal_prompt_7,
)

fun dailyPrompt(date: LocalDate): Int = PROMPTS[(date.toEpochDay() % PROMPTS.size).toInt().let { if (it < 0) it + PROMPTS.size else it }]

@Composable
fun moodLabel(mood: Mood): String = stringResource(
    when (mood) {
        Mood.GREAT -> R.string.mood_great
        Mood.GOOD -> R.string.mood_good
        Mood.OKAY -> R.string.mood_okay
        Mood.LOW -> R.string.mood_low
        Mood.HARD -> R.string.mood_hard
    },
)

/** A mood as a calm line face, never a colored emoji. */
@Composable
fun MoodIcon(mood: Mood, modifier: Modifier = Modifier, tint: Color = Kairos.colors.accentText) {
    val icon = when (mood) {
        Mood.GREAT -> Icons.Outlined.SentimentVerySatisfied
        Mood.GOOD -> Icons.Outlined.SentimentSatisfied
        Mood.OKAY -> Icons.Outlined.SentimentNeutral
        Mood.LOW -> Icons.Outlined.SentimentDissatisfied
        Mood.HARD -> Icons.Outlined.SentimentVeryDissatisfied
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier)
}

/** Worst to best, the order people read a scale in. */
val MoodScale = Mood.entries.reversed()

/**
 * The journal tab: today's reflection first (the one thing to do here), then every past entry,
 * a date and a line each. Search looks through every part of every entry, on the device.
 */
@Composable
fun JournalScreen(onWrite: (LocalDate, Mood?) -> Unit, onOpen: (Long) -> Unit, onOpenCalendar: () -> Unit) {
    val vm = kairosViewModel { JournalViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    val monthFmt = rememberDateFormatter("MMMM yyyy")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item {
                JournalHero(
                    searching = searching,
                    onSearch = { searching = !searching; if (!searching) vm.search("") },
                    onNew = { onWrite(LocalDate.now(), null) },
                    onCalendar = onOpenCalendar,
                )
            }
            when (val s = state) {
                UiState.Loading -> item { LoadingState(Modifier.height(240.dp)) }
                is UiState.Error -> item { ErrorState(onRetry = vm::retry) }
                is UiState.Ready -> {
                    val d = s.data
                    if (searching) item { SearchField(d.query, vm::search) }
                    if (!searching) item { TodaySection(d.today, d.todayEntry, onWrite) }
                    if (d.total > 0) {
                        if (!searching) item {
                            Text(
                                stringResource(R.string.journal_summary_line, d.thisMonth, d.total) +
                                    if (d.streak >= 2) " · " + pluralStringResource(R.plurals.days_in_a_row, d.streak, d.streak) else "",
                                style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                            )
                        }
                        if (d.months.isEmpty()) {
                            item { Text(stringResource(R.string.journal_no_matches), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted, modifier = Modifier.padding(24.dp)) }
                        }
                        d.months.forEach { (month, entries) ->
                            item(key = "m-$month") { SectionLabel(monthFmt(month.atDay(1)), Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp)) }
                            items(entries, key = { it.id }) { e -> EntryRow(e) { onOpen(e.id) } }
                        }
                    } else if (!searching) {
                        item {
                            Text(
                                stringResource(R.string.journal_empty_body), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Chan's sunrise photo as a quiet banner, with the title and a line of encouragement over its shaded lower half. */
@Composable
private fun JournalHero(searching: Boolean, onSearch: () -> Unit, onNew: () -> Unit, onCalendar: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(250.dp)) {
        Image(
            painterResource(R.drawable.onboarding_sunrise), contentDescription = null,
            contentScale = ContentScale.Crop, alignment = BiasAlignment(0.1f, 0.25f), modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x990D1729), 0.45f to Color(0x330D1729), 1f to MaterialTheme.colorScheme.background),
            ),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 24.dp, end = 8.dp, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.journal), style = MaterialTheme.typography.headlineMedium, color = Color(0xFFF4EFE5),
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onCalendar) { Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.life_calendar), tint = Color(0xFFF4EFE5)) }
                IconButton(onClick = onSearch) {
                    Icon(if (searching) Icons.Filled.Close else Icons.Outlined.Search, stringResource(R.string.journal_search), tint = Color(0xFFF4EFE5))
                }
                IconButton(onClick = onNew) { Icon(Icons.Filled.Add, stringResource(R.string.journal_new), tint = Color(0xFFF4EFE5)) }
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.journal_hero_line), fontFamily = SerifFamily, fontSize = 20.sp, lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(end = 40.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onSearch: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onSearch,
        placeholder = { Text(stringResource(R.string.journal_search)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) } }) else null,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun TodaySection(today: LocalDate, entry: JournalEntry?, onWrite: (LocalDate, Mood?) -> Unit) {
    val dateFmt = rememberDateFormatter("EEEE, d MMMM")
    KCard(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), padding = 20.dp) {
        SectionLabel(stringResource(R.string.journal_today) + " · " + dateFmt(today), color = Kairos.colors.accentText)
        Spacer(Modifier.height(10.dp))
        if (entry != null && !entry.isBlank) {
            Row(verticalAlignment = Alignment.Top) {
                entry.mood?.let { MoodIcon(it, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)) }
                Text(
                    entry.excerpt().ifBlank { entry.mood?.let { moodLabel(it) }.orEmpty() },
                    fontFamily = SerifFamily, fontSize = 17.sp, lineHeight = 25.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = { onWrite(today, null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_continue)) }
        } else {
            Text(stringResource(R.string.journal_mood_question), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.journal_mood_optional), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
            Spacer(Modifier.height(12.dp))
            // One tap on a mood starts the entry with it already chosen. Choosing none is fine too.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MoodScale.forEach { m ->
                    val label = moodLabel(m)
                    Column(
                        Modifier.clip(MaterialTheme.shapes.medium).clickable { onWrite(today, m) }.padding(4.dp).semantics(mergeDescendants = true) { contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Kairos.colors.accentSoft), contentAlignment = Alignment.Center) {
                            MoodIcon(m, Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(dailyPrompt(today)), fontFamily = SerifFamily, fontSize = 17.sp, lineHeight = 25.sp, color = Kairos.colors.muted)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onWrite(today, null) },
                colors = ButtonDefaults.buttonColors(containerColor = Kairos.colors.accent, contentColor = Color(0xFF1A2233)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.journal_write_today), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** A date and a line: small enough that a month of entries reads at a glance. */
@Composable
private fun EntryRow(e: JournalEntry, onClick: () -> Unit) {
    val dayFmt = rememberDateFormatter("EEE")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(e.date.dayOfMonth.toString(), fontFamily = SerifFamily, fontSize = 24.sp, lineHeight = 28.sp)
            Text(dayFmt(e.date), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val excerpt = e.excerpt(160).ifBlank { e.mood?.let { moodLabel(it) }.orEmpty() }
            Text("“$excerpt”", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        e.mood?.let { Spacer(Modifier.width(10.dp)); MoodIcon(it, Modifier.size(22.dp)) }
    }
}
