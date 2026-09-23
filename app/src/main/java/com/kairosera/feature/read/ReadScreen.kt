package com.kairosera.feature.read

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.reading.ReadingMath
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(onOpenBook: (Long) -> Unit, onNewBook: () -> Unit) {
    val vm = kairosViewModel { ReadViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_read)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewBook, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_book)) })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(onRetry = vm::retry)
                is UiState.Ready -> if (s.data.books.isEmpty()) {
                    MessageState(Icons.Outlined.AutoStories, stringResource(R.string.read_empty_title), stringResource(R.string.read_empty_body)) {
                        OutlinedButton(onClick = onNewBook) { Text(stringResource(R.string.add_book)) }
                    }
                } else {
                    LazyColumn(
                        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { TotalsCard(s.data.totals) }
                        listOf(
                            BookStatus.READING to R.string.shelf_reading,
                            BookStatus.PAUSED to R.string.shelf_paused,
                            BookStatus.WANT_TO_READ to R.string.shelf_want,
                            BookStatus.FINISHED to R.string.shelf_finished,
                        ).forEach { (status, label) ->
                            val shelf = s.data.byStatus(status)
                            if (shelf.isNotEmpty()) {
                                item(key = "h-$status") { SectionLabel(stringResource(label), Modifier.padding(top = 8.dp, start = 4.dp)) }
                                items(shelf, key = { it.id }) { BookRow(it, s.data.today) { onOpenBook(it.id) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(t: ReadingTotals) {
    KCard {
        SectionLabel(stringResource(R.string.reading_this_week))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Total(t.pagesToday.toString(), stringResource(R.string.pages_today), Modifier.weight(1f))
            Total(t.pagesWeek.toString(), stringResource(R.string.pages_week), Modifier.weight(1f))
            Total(t.minutesWeek.toString(), stringResource(R.string.minutes_week), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.reading_days_line, t.readingDaysWeek, t.finishedThisYear),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Total(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BookSpine(book: Book, width: Int = 44) {
    Box(
        Modifier.size(width = width.dp, height = (width * 1.4f).dp).clip(RoundedCornerShape(6.dp)).background(Color(book.coverColor)),
        contentAlignment = Alignment.Center,
    ) {
        Text(book.title.trim().take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun BookRow(book: Book, today: LocalDate, onClick: () -> Unit) {
    KCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookSpine(book)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.totalPages > 0 && book.status != BookStatus.WANT_TO_READ) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { book.progress.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(book.coverColor),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(4.dp))
                    val perDay = if (book.status == BookStatus.READING) ReadingMath.pagesPerDayToTarget(book, today) else null
                    Text(
                        listOfNotNull(
                            stringResource(R.string.book_page_of, book.currentPage, book.totalPages),
                            perDay?.takeIf { it > 0 }?.let { stringResource(R.string.book_pages_per_day, it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
