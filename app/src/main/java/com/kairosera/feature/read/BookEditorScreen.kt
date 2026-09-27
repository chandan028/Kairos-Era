package com.kairosera.feature.read

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.data.tracker.TrackerTemplates
import com.kairosera.domain.reading.BookStatus
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookEditorScreen(bookId: Long?, onClose: () -> Unit, onSaved: (Long, Boolean) -> Unit) {
    val vm = kairosViewModel(key = "book-edit-$bookId") { BookEditorViewModel(it, bookId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val dateFmt = rememberMediumDateFormatter()

    LaunchedEffect(saved) { saved?.let { onSaved(it, vm.isNew) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isNew) R.string.add_book else R.string.edit_book)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) } },
                actions = { TextButton(onClick = vm::save, enabled = ready && saved == null) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            if (!ready) { LoadingState(); return@Box }
            Column(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    form.title, { v -> vm.edit { it.copy(title = v.take(200)) } },
                    label = { Text(stringResource(R.string.book_title)) },
                    isError = BookFormError.TITLE_EMPTY in errors,
                    supportingText = if (BookFormError.TITLE_EMPTY in errors) ({ Text(stringResource(R.string.error_book_title)) }) else null,
                    singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    form.author, { v -> vm.edit { it.copy(author = v.take(120)) } }, label = { Text(stringResource(R.string.book_author)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        form.totalPages, { v -> vm.edit { it.copy(totalPages = v.filter(Char::isDigit).take(5)) } }, label = { Text(stringResource(R.string.book_total_pages)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        form.currentPage, { v -> vm.edit { it.copy(currentPage = v.filter(Char::isDigit).take(5)) } }, label = { Text(stringResource(R.string.book_current_page)) },
                        isError = BookFormError.PAGE_BEYOND_TOTAL in errors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                if (BookFormError.PAGE_BEYOND_TOTAL in errors) Text(stringResource(R.string.error_page_beyond), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                SectionLabel(stringResource(R.string.book_status))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookStatus.entries.forEach { s -> FilterChip(form.status == s, { vm.edit { it.copy(status = s) } }, { Text(statusLabel(s)) }) }
                }
                SectionLabel(stringResource(R.string.book_target_date))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { pickingDate = true }, label = { Text(form.targetDate?.let(dateFmt) ?: stringResource(R.string.book_no_target)) })
                    if (form.targetDate != null) TextButton(onClick = { vm.edit { it.copy(targetDate = null) } }) { Text(stringResource(R.string.clear)) }
                }
                SectionLabel(stringResource(R.string.tracker_color))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TrackerTemplates.COLORS.forEachIndexed { i, color ->
                        val selected = form.coverColor == color
                        val desc = stringResource(R.string.color_option, i + 1)
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(color))
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { vm.edit { it.copy(coverColor = color) } }
                                .semantics { contentDescription = desc; this.selected = selected },
                        )
                    }
                }
            }
        }
    }
    if (pickingDate) KDatePickerDialog(form.targetDate ?: LocalDate.now().plusDays(30), onDismiss = { pickingDate = false }) { d -> pickingDate = false; vm.edit { it.copy(targetDate = d) } }
    if (failed) {
        AlertDialog(
            onDismissRequest = vm::dismissFailure,
            confirmButton = { TextButton(onClick = { vm.dismissFailure(); vm.save() }) { Text(stringResource(R.string.retry)) } },
            dismissButton = { TextButton(onClick = vm::dismissFailure) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(stringResource(R.string.error_action_failed)) },
        )
    }
}
