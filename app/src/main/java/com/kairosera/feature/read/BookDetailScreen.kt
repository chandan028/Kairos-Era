package com.kairosera.feature.read

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(bookId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm = kairosViewModel(key = "book-$bookId") { BookDetailViewModel(it, bookId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }
    DisposableEffect(vm) { onDispose { vm.flush() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? BookState.Ready)?.book?.title.orEmpty(), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (state is BookState.Ready) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_book)) }
                        IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.move_to_trash)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
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
    var logging by rememberSaveable { mutableStateOf(false) }
    var addingNote by rememberSaveable { mutableStateOf(false) }
    var deletingSession by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookSpine(book, 56)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall)
                    if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookStatus.entries.forEach { s -> FilterChip(book.status == s, { vm.setStatus(s) }, { Text(statusLabel(s)) }) }
            }
        }
        item {
            KCard {
                SectionLabel(stringResource(R.string.book_progress))
                Spacer(Modifier.height(10.dp))
                if (book.totalPages > 0) {
                    LinearProgressIndicator(
                        progress = { book.progress.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Color(book.coverColor),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.book_page_of_percent, book.currentPage, book.totalPages, (book.progress * 100).toInt()), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(stringResource(R.string.book_page_only, book.currentPage), style = MaterialTheme.typography.bodyLarge)
                }
                book.targetDate?.let { target ->
                    val perDay = ReadingMath.pagesPerDayToTarget(book, vm.today)
                    Text(
                        stringResource(R.string.book_target_line, dateFmt(target)) + (perDay?.takeIf { it > 0 }?.let { " · " + stringResource(R.string.book_pages_per_day, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                book.finishedDate?.let { Text(stringResource(R.string.book_finished_on, dateFmt(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { logging = true }, enabled = book.status != BookStatus.FINISHED) { Text(stringResource(R.string.log_reading)) }
            }
        }
        item {
            KCard {
                SectionLabel(stringResource(R.string.book_insights))
                Text(stringResource(R.string.book_insights_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val i = insights
                if (i != null) {
                    InsightField(R.string.insight_why, i.whyStarted) { v -> vm.editInsights { it.copy(whyStarted = v) } }
                    InsightField(R.string.insight_expected, i.expected) { v -> vm.editInsights { it.copy(expected = v) } }
                    InsightField(R.string.insight_learned, i.learned) { v -> vm.editInsights { it.copy(learned = v) } }
                    InsightField(R.string.insight_key_ideas, i.keyIdeas) { v -> vm.editInsights { it.copy(keyIdeas = v) } }
                    InsightField(R.string.insight_applied, i.applied) { v -> vm.editInsights { it.copy(applied = v) } }
                    InsightField(R.string.insight_changed, i.changed) { v -> vm.editInsights { it.copy(changed = v) } }
                    InsightField(R.string.insight_skills, i.skills) { v -> vm.editInsights { it.copy(skills = v) } }
                    InsightField(R.string.insight_takeaway, i.finalTakeaway) { v -> vm.editInsights { it.copy(finalTakeaway = v) } }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.insight_recommend), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(i.recommendAgain == true, { vm.editInsights { it.copy(recommendAgain = if (it.recommendAgain == true) null else true) } }, { Text(stringResource(R.string.yes)) })
                        FilterChip(i.recommendAgain == false, { vm.editInsights { it.copy(recommendAgain = if (it.recommendAgain == false) null else false) } }, { Text(stringResource(R.string.no)) })
                    }
                }
            }
        }
        item {
            KCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.book_notes), Modifier.weight(1f))
                    TextButton(onClick = { addingNote = true }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_note)) }
                }
                if (notes.isEmpty()) Text(stringResource(R.string.book_notes_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                notes.forEach { n ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                noteKindLabel(n.kind) + (n.page?.let { " · " + stringResource(R.string.page_short, it) } ?: ""),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            )
                            Text(n.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { vm.deleteNote(n.id) }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete_note)) }
                    }
                }
            }
        }
        item {
            KCard {
                SectionLabel(stringResource(R.string.book_sessions))
                if (sessions.isEmpty()) Text(stringResource(R.string.book_sessions_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                sessions.take(30).forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dateFmt(s.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.session_line, s.pages, s.minutes),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { deletingSession = s.id }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete_session)) }
                    }
                }
            }
        }
    }

    if (logging) LogReadingDialog(onDismiss = { logging = false }, onLog = { pages, minutes -> vm.logSession(pages, minutes, java.time.LocalDate.now()); logging = false })
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

@Composable
private fun LogReadingDialog(onDismiss: () -> Unit, onLog: (Int, Int) -> Unit) {
    var pages by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_reading)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    pages, { v -> pages = v.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.pages_read)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    minutes, { v -> minutes = v.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.minutes_read)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onLog(pages.toIntOrNull() ?: 0, minutes.toIntOrNull() ?: 0) }, enabled = (pages.toIntOrNull() ?: 0) > 0) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
