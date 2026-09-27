package com.kairosera.feature.read

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.text.style.TextAlign
import com.kairosera.core.ui.components.GhostAddButton
import com.kairosera.core.ui.components.KChip
import com.kairosera.core.ui.components.KProgressBar
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.Pill
import com.kairosera.core.ui.components.PrimaryWideButton
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionHeader
import com.kairosera.core.ui.components.ToastKind
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.OverlineStyle
import com.kairosera.core.ui.theme.QuoteStyle
import com.kairosera.core.ui.theme.Space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookNoteKind
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.reading.ReadingMath
import com.kairosera.ui.kairosViewModel

@Composable
fun statusLabel(s: BookStatus): String = stringResource(
    when (s) {
        BookStatus.WANT_TO_READ -> R.string.status_want
        BookStatus.READING -> R.string.status_reading
        BookStatus.PAUSED -> R.string.status_paused
        BookStatus.FINISHED -> R.string.status_finished
    },
)

@Composable
private fun noteKindLabel(k: BookNoteKind): String = stringResource(
    when (k) {
        BookNoteKind.NOTE -> R.string.note_kind_note
        BookNoteKind.HIGHLIGHT -> R.string.note_kind_highlight
        BookNoteKind.LESSON -> R.string.note_kind_lesson
    },
)

@Composable
fun BookDetailScreen(bookId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm = kairosViewModel(key = "book-$bookId") { BookDetailViewModel(it, bookId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }
    DisposableEffect(vm) { onDispose { vm.flush() } }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(title = "", onBack = onBack) {
            if (state is BookState.Ready) {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_book)) }
                IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.move_to_trash)) }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                BookState.Loading -> LoadingState()
                BookState.Error -> ErrorState(onRetry = null)
                BookState.Missing -> MessageState(Icons.Outlined.SearchOff, stringResource(R.string.book_missing), stringResource(R.string.book_missing_body)) {
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.close)) }
                }
                is BookState.Ready -> BookContent(s.book, vm)
            }
        }
    }
    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            confirmButton = { TextButton(onClick = { confirmTrash = false; vm.moveToTrash(onBack) }) { Text(stringResource(R.string.move_to_trash)) } },
            dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text(stringResource(R.string.cancel)) } },
            text = { Text(stringResource(R.string.book_trash_body)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookContent(book: Book, vm: BookDetailViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val dateFmt = rememberMediumDateFormatter()
    val toaster = LocalToaster.current
    val loggedText = stringResource(R.string.reading_logged)
    var logging by rememberSaveable { mutableStateOf(false) }
    var addingNote by rememberSaveable { mutableStateOf(false) }
    var reflecting by rememberSaveable { mutableStateOf(false) }
    var deletingSession by remember { mutableStateOf<Long?>(null) }
    val tone = Kairos.colors.motivation

    LazyColumn(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.s, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        item(key = "hero") {
            Row {
                BookCover(book, 96.dp)
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall)
                    if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
                    Spacer(Modifier.height(Space.s))
                    Pill(statusLabel(book.status), if (book.status == BookStatus.FINISHED) Kairos.colors.success else tone)
                }
            }
        }
        item(key = "progress") {
            Column {
                KProgressBar(book.progress.toFloat(), color = if (book.status == BookStatus.FINISHED) Kairos.colors.success.strong else tone.strong)
                Spacer(Modifier.height(Space.s))
                Text(
                    if (book.totalPages > 0) stringResource(R.string.book_page_of_percent, book.currentPage, book.totalPages, (book.progress * 100).toInt())
                    else stringResource(R.string.book_page_only, book.currentPage),
                    style = MaterialTheme.typography.bodyMedium,
                )
                book.targetDate?.let { target ->
                    val perDay = ReadingMath.pagesPerDayToTarget(book, vm.today)
                    Text(
                        stringResource(R.string.book_target_line, dateFmt(target)) + (perDay?.takeIf { it > 0 }?.let { " · " + stringResource(R.string.book_pages_per_day, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted,
                    )
                }
                book.finishedDate?.let { Text(stringResource(R.string.book_finished_on, dateFmt(it)), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted) }
                Spacer(Modifier.height(Space.l))
                if (book.status != BookStatus.FINISHED) {
                    PrimaryWideButton(stringResource(R.string.log_reading), onClick = { logging = true }, icon = Icons.Outlined.Add)
                    Spacer(Modifier.height(Space.m))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookStatus.entries.forEach { s -> KChip(statusLabel(s), book.status == s) { vm.setStatus(s) } }
                }
            }
        }
        val i = insights
        if (i != null) {
            if (reflecting) {
                item(key = "reflect-edit") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        SectionLabel(stringResource(R.string.book_reflection))
                        Text(stringResource(R.string.book_insights_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                        InsightField(R.string.insight_why, i.whyStarted) { v -> vm.editInsights { it.copy(whyStarted = v) } }
                        InsightField(R.string.insight_expected, i.expected) { v -> vm.editInsights { it.copy(expected = v) } }
                        InsightField(R.string.insight_key_ideas, i.keyIdeas) { v -> vm.editInsights { it.copy(keyIdeas = v) } }
                        InsightField(R.string.insight_applied, i.applied) { v -> vm.editInsights { it.copy(applied = v) } }
                        InsightField(R.string.insight_learned, i.learned) { v -> vm.editInsights { it.copy(learned = v) } }
                        InsightField(R.string.insight_changed, i.changed) { v -> vm.editInsights { it.copy(changed = v) } }
                        InsightField(R.string.insight_skills, i.skills) { v -> vm.editInsights { it.copy(skills = v) } }
                        InsightField(R.string.insight_takeaway, i.finalTakeaway) { v -> vm.editInsights { it.copy(finalTakeaway = v) } }
                        Text(stringResource(R.string.insight_recommend), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KChip(stringResource(R.string.yes), i.recommendAgain == true) { vm.editInsights { it.copy(recommendAgain = if (it.recommendAgain == true) null else true) } }
                            KChip(stringResource(R.string.no), i.recommendAgain == false) { vm.editInsights { it.copy(recommendAgain = if (it.recommendAgain == false) null else false) } }
                        }
                        Spacer(Modifier.height(Space.s))
                        PrimaryWideButton(stringResource(R.string.done), onClick = { vm.flush(); reflecting = false })
                    }
                }
            } else {
                item(key = "reflect") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        Reflection(R.string.insight_why, i.whyStarted, quote = true)
                        Reflection(R.string.insight_expected, i.expected)
                        Reflection(R.string.insight_key_ideas, i.keyIdeas)
                        Reflection(R.string.insight_applied, i.applied)
                        if (i.learned.isNotBlank()) Reflection(R.string.insight_learned, i.learned)
                        if (i.changed.isNotBlank()) Reflection(R.string.insight_changed, i.changed)
                        if (i.skills.isNotBlank()) Reflection(R.string.insight_skills, i.skills)
                        if (i.finalTakeaway.isNotBlank()) Reflection(R.string.insight_takeaway, i.finalTakeaway, quote = true)
                        GhostAddButton(
                            stringResource(if (listOf(i.whyStarted, i.expected, i.keyIdeas, i.applied).all { it.isBlank() }) R.string.book_add_reflection else R.string.book_edit_reflection),
                            onClick = { reflecting = true },
                            icon = Icons.Outlined.Edit,
                        )
                    }
                }
            }
        }
        item(key = "notes") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                SectionHeader(stringResource(R.string.book_notes), action = stringResource(R.string.add_note), onAction = { addingNote = true })
                if (notes.isEmpty()) {
                    Text(stringResource(R.string.book_notes_empty), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
                }
                notes.forEach { n ->
                    KCard(padding = 16.dp) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    noteKindLabel(n.kind).uppercase() + (n.page?.let { " · " + stringResource(R.string.page_short, it) } ?: ""),
                                    style = OverlineStyle, color = tone.strong,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(n.text, style = if (n.kind == BookNoteKind.HIGHLIGHT) QuoteStyle else MaterialTheme.typography.bodyLarge)
                            }
                            IconButton(onClick = { vm.deleteNote(n.id) }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete_note), tint = Kairos.colors.muted) }
                        }
                    }
                }
            }
        }
        if (sessions.isNotEmpty()) {
            item(key = "sessions") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    SectionLabel(stringResource(R.string.book_sessions))
                    KCard(padding = 8.dp) {
                        sessions.take(30).forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(dateFmt(s.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.session_line, s.pages, s.minutes), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
                                IconButton(onClick = { deletingSession = s.id }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete_session), tint = Kairos.colors.muted) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (logging) {
        LogReadingSheet(book, onDismiss = { logging = false }) { pages, minutes ->
            vm.logSession(pages, minutes, java.time.LocalDate.now())
            logging = false
            toaster.show(loggedText, ToastKind.SUCCESS)
        }
    }
    if (addingNote) NoteDialog(onDismiss = { addingNote = false }, onSave = { kind, text, page -> vm.saveNote(kind, text, page); addingNote = false })
    deletingSession?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            text = { Text(stringResource(R.string.delete_session_body)) },
            confirmButton = { TextButton(onClick = { deletingSession = null; vm.deleteSession(id) }) { Text(stringResource(R.string.delete_session), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingSession = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** One reflection prompt in read mode: the question as a label, the answer (or a gentle prompt) below. */
@Composable
private fun Reflection(label: Int, text: String, quote: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionLabel(stringResource(label))
        KCard(padding = 16.dp) {
            if (text.isBlank()) {
                Text(stringResource(R.string.book_reflection_empty), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
            } else {
                Text(if (quote) "“$text”" else text, style = if (quote) QuoteStyle else MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Log reading without typing: pick pages and minutes with presets or steppers. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LogReadingSheet(book: Book, onDismiss: () -> Unit, onLog: (Int, Int) -> Unit) {
    var pages by rememberSaveable { mutableStateOf(10) }
    var minutes by rememberSaveable { mutableStateOf(20) }
    val left = if (book.totalPages > 0) (book.totalPages - book.currentPage).coerceAtLeast(0) else Int.MAX_VALUE
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(bottom = Space.xl).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Text(stringResource(R.string.log_reading), style = MaterialTheme.typography.headlineSmall)
            Counter(stringResource(R.string.pages_read), pages, step = 1, max = minOf(left, 5000)) { pages = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20, 30, 50).filter { it <= left }.forEach { p -> KChip("$p", pages == p) { pages = p } }
                if (left in 1..5000) KChip(stringResource(R.string.read_to_end, left), pages == left) { pages = left }
            }
            Counter(stringResource(R.string.minutes_read), minutes, step = 5, max = 1440) { minutes = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30, 45, 60).forEach { m -> KChip("$m", minutes == m) { minutes = m } }
            }
            PrimaryWideButton(stringResource(R.string.save), onClick = { onLog(pages, minutes) }, enabled = pages > 0)
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, step: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = OverlineStyle, color = Kairos.colors.muted, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceAtLeast(0)) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.widthIn(min = 56.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun InsightField(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(4000)) },
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        minLines = 1,
        maxLines = 8,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteDialog(onDismiss: () -> Unit, onSave: (BookNoteKind, String, Int?) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(BookNoteKind.NOTE) }
    var text by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookNoteKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, { Text(noteKindLabel(k)) }) }
                }
                OutlinedTextField(text, { text = it.take(4000) }, label = { Text(stringResource(R.string.note_text)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    page, { v -> page = v.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.note_page)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(kind, text, page.toIntOrNull()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
