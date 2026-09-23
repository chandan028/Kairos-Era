package com.kairosera.feature.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.AppContainer
import com.kairosera.R
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.ui.appContainer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** One row in Trash, whatever it was before it was deleted. */
private data class TrashItem(val kind: Kind, val id: Long, val title: String, val deletedAt: Instant?) {
    enum class Kind { TASK, TRACKER, BOOK }
    val key get() = "$kind-$id"
}

private suspend fun AppContainer.restore(item: TrashItem) {
    val now = Instant.now(clock)
    when (item.kind) {
        TrashItem.Kind.TASK -> restoreTask(item.id)
        TrashItem.Kind.TRACKER -> trackers.restore(item.id, now)
        TrashItem.Kind.BOOK -> books.restore(item.id, now)
    }
}

private suspend fun AppContainer.deleteForever(item: TrashItem) {
    when (item.kind) {
        TrashItem.Kind.TASK -> { tasks.deletePermanently(item.id); scheduler.rebuild("deleted") }
        TrashItem.Kind.TRACKER -> trackers.deletePermanently(item.id)
        TrashItem.Kind.BOOK -> books.deletePermanently(item.id)
    }
}

/** Recently deleted tasks, trackers and books. Nothing leaves Trash without an explicit, confirmed permanent delete. */
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val flow = remember {
        combine(c.tasks.observeTrash(), c.trackers.observeTrash(), c.books.observeTrash()) { tasks, trackers, books ->
            (
                tasks.map { TrashItem(TrashItem.Kind.TASK, it.id, it.title, it.deletedAt) } +
                    trackers.map { TrashItem(TrashItem.Kind.TRACKER, it.id, "${it.icon} ${it.name}", it.deletedAt) } +
                    books.map { TrashItem(TrashItem.Kind.BOOK, it.id, it.title, it.deletedAt) }
                ).sortedByDescending { it.deletedAt }
        }.catch { emit(emptyList()) }
    }
    val trashed by flow.collectAsStateWithLifecycle(initialValue = null)
    var confirm by remember { mutableStateOf<TrashItem?>(null) }
    val dateFmt = rememberMediumDateFormatter()

    SubScreen(stringResource(R.string.trash), onBack) {
        val list = trashed
        when {
            list == null -> LoadingState()
            list.isEmpty() -> MessageState(Icons.Outlined.DeleteOutline, stringResource(R.string.trash_empty_title), stringResource(R.string.trash_empty_body))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(stringResource(R.string.trash_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                items(list, key = { it.key }) { t ->
                    ListItem(
                        overlineContent = {
                            Text(
                                stringResource(
                                    when (t.kind) {
                                        TrashItem.Kind.TASK -> R.string.trash_kind_task
                                        TrashItem.Kind.TRACKER -> R.string.trash_kind_tracker
                                        TrashItem.Kind.BOOK -> R.string.trash_kind_book
                                    },
                                ),
                            )
                        },
                        headlineContent = { Text(t.title) },
                        supportingContent = {
                            t.deletedAt?.let { Text(stringResource(R.string.deleted_on, dateFmt(it.atZone(ZoneId.systemDefault()).toLocalDate()))) }
                        },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { scope.launch { runCatching { c.restore(t) } } }) { Text(stringResource(R.string.restore)) }
                                TextButton(onClick = { confirm = t }) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) }
                            }
                        },
                    )
                }
            }
        }
    }
    confirm?.let { t ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.delete_forever_title)) },
            text = { Text(stringResource(if (t.kind == TrashItem.Kind.TASK) R.string.delete_forever_body else R.string.delete_forever_body_history, t.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch { runCatching { c.deleteForever(t) } }
                }) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
