package com.kairosera.feature.read

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.GhostAddButton
import com.kairosera.core.ui.components.Hairline
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.KProgressBar
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.PrimaryWideButton
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionHeader
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.StatTile
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.QuoteStyle
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.core.ui.theme.Space
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.reading.ReadingMath
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate

/** Read: a personal library. The book in your hands first, then today, then the shelf. */
@Composable
fun ReadScreen(onOpenBook: (Long) -> Unit, onNewBook: () -> Unit, onBack: (() -> Unit)? = null) {
    val vm = kairosViewModel { ReadViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_read), subtitle = stringResource(R.string.read_subtitle), onBack = onBack) {
            IconButton(onClick = onNewBook) { Icon(Icons.Filled.Add, stringResource(R.string.add_book)) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(onRetry = vm::retry)
                is UiState.Ready -> if (s.data.books.isEmpty()) {
                    MessageState(Icons.Outlined.AutoStories, stringResource(R.string.read_empty_title), stringResource(R.string.read_empty_body)) {
                        PrimaryWideButton(stringResource(R.string.add_book), onClick = onNewBook, icon = Icons.Outlined.Add)
                    }
                } else {
                    LibraryContent(s.data, onOpenBook, onNewBook)
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(data: ReadData, onOpenBook: (Long) -> Unit, onNewBook: () -> Unit) {
    val current = data.byStatus(BookStatus.READING)
    val hero = current.firstOrNull()
    val shelf = data.books
        .sortedBy { listOf(BookStatus.READING, BookStatus.PAUSED, BookStatus.WANT_TO_READ, BookStatus.FINISHED).indexOf(it.status) }
    LazyColumn(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.s, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        if (hero != null) {
            item(key = "hero") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    SectionLabel(stringResource(R.string.read_currently))
                    CurrentlyReading(hero, data.today, onOpen = { onOpenBook(hero.id) })
                }
            }
        }
        item(key = "today") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                SectionLabel(stringResource(R.string.today))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    StatTile(data.totals.pagesToday.toString(), stringResource(R.string.read_pages), Modifier.weight(1f), tone = Kairos.colors.motivation)
                    StatTile(data.totals.minutesToday.toString(), stringResource(R.string.read_minutes), Modifier.weight(1f))
                }
                Text(
                    stringResource(R.string.read_week_line, data.totals.pagesWeek, data.totals.readingDaysWeek, data.totals.finishedThisYear),
                    style = MaterialTheme.typography.bodySmall,
                    color = Kairos.colors.muted,
                )
            }
        }
        if (hero != null && hero.whyStarted.isNotBlank()) {
            item(key = "why") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    SectionLabel(stringResource(R.string.read_why_started))
                    KCard { Text("“${hero.whyStarted}”", style = QuoteStyle) }
                }
            }
        }
        if (hero != null && hero.expected.isNotBlank()) {
            item(key = "gain") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    SectionLabel(stringResource(R.string.read_gaining))
                    KCard { Text(hero.expected, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
        item(key = "lib-h") { SectionHeader(stringResource(R.string.read_my_library, data.books.size)) }
        if (shelf.isNotEmpty()) {
            item(key = "lib") {
                KCard(padding = 4.dp) {
                    shelf.forEachIndexed { i, b ->
                        if (i > 0) Hairline(Modifier.padding(start = 76.dp, end = 16.dp))
                        BookRow(b, data.today) { onOpenBook(b.id) }
                    }
                }
            }
        }
        item(key = "add") { GhostAddButton(stringResource(R.string.add_book), onClick = onNewBook, icon = Icons.Outlined.Add) }
    }
}

@Composable
private fun CurrentlyReading(book: Book, today: LocalDate, onOpen: () -> Unit) {
    KCard(onClick = onOpen) {
        Row {
            BookCover(book, 84.dp)
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
            }
        }
        Spacer(Modifier.height(Space.l))
        val tone = Kairos.colors.motivation
        KProgressBar(book.progress.toFloat(), color = tone.strong)
        Spacer(Modifier.height(Space.s))
        Row {
            Text(
                if (book.totalPages > 0) stringResource(R.string.book_page_of, book.currentPage, book.totalPages) else stringResource(R.string.book_page_only, book.currentPage),
                style = MaterialTheme.typography.bodySmall,
                color = Kairos.colors.muted,
                modifier = Modifier.weight(1f),
            )
            Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = tone.strong)
        }
        ReadingMath.pagesPerDayToTarget(book, today)?.takeIf { it > 0 }?.let {
            Text(stringResource(R.string.book_pages_per_day, it), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        }
        Spacer(Modifier.height(Space.l))
        PrimaryWideButton(stringResource(R.string.read_continue), onClick = onOpen, icon = Icons.AutoMirrored.Outlined.MenuBook)
    }
}

/**
 * A simple generated cover: the book's color with a soft sheen and its initial in serif.
 * No images leave or enter the app.
 */
@Composable
fun BookCover(book: Book, width: Dp) {
    val base = Color(book.coverColor)
    Box(
        Modifier.size(width = width, height = width * 1.45f).clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp))
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.78f)))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = 5.dp).width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.18f)))
        Text(
            book.title.trim().take(1).uppercase(),
            color = Color(0xFFF5EFE3),
            style = TextStyle(fontFamily = SerifFamily, fontSize = (width.value * 0.42f).sp),
        )
    }
}

/** Kept for other screens that show a small cover. */
@Composable
fun BookSpine(book: Book, width: Int = 44) = BookCover(book, width.dp)

@Composable
fun BookRow(book: Book, today: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, 44.dp)
        Spacer(Modifier.width(Space.l))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(book.author.takeIf { it.isNotBlank() }, statusLabel(book.status)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Kairos.colors.muted,
                maxLines = 1,
            )
            if (book.totalPages > 0 && book.status != BookStatus.WANT_TO_READ) {
                Spacer(Modifier.height(8.dp))
                KProgressBar(book.progress.toFloat(), color = if (book.status == BookStatus.FINISHED) Kairos.colors.success.strong else Kairos.colors.motivation.strong, height = 4.dp)
                val perDay = if (book.status == BookStatus.READING) ReadingMath.pagesPerDayToTarget(book, today) else null
                Text(
                    listOfNotNull(
                        stringResource(R.string.book_page_of, book.currentPage, book.totalPages),
                        perDay?.takeIf { it > 0 }?.let { stringResource(R.string.book_pages_per_day, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Kairos.colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
