package com.kairosera.feature.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.domain.model.Task
import com.kairosera.ui.appContainer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Recently deleted tasks. Nothing leaves Trash without an explicit, confirmed permanent delete. */
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val flow = remember { c.tasks.observeTrash().map<List<Task>, List<Task>?> { it }.catch { emit(emptyList()) } }
    val trashed by flow.collectAsStateWithLifecycle(initialValue = null)
    var confirm by remember { mutableStateOf<Task?>(null) }
    val dateFmt = rememberMediumDateFormatter()

    SubScreen(stringResource(R.string.trash), onBack) {
        val list = trashed
        when {
            list == null -> com.kairosera.core.ui.components.LoadingState()
            list.isEmpty() -> MessageState(Icons.Outlined.DeleteOutline, stringResource(R.string.trash_empty_title), stringResource(R.string.trash_empty_body))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(stringResource(R.string.trash_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = androidx.compose.ui.Modifier.padding(16.dp))
                }
                items(list, key = { it.id }) { t ->
                    ListItem(
                        headlineContent = { Text(t.title) },
                        supportingContent = {
                            t.deletedAt?.let { Text(stringResource(R.string.deleted_on, dateFmt(it.atZone(ZoneId.systemDefault()).toLocalDate()))) }
                        },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { scope.launch { runCatching { c.restoreTask(t.id) } } }) { Text(stringResource(R.string.restore)) }
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
            text = { Text(stringResource(R.string.delete_forever_body, t.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch { runCatching { c.tasks.deletePermanently(t.id); c.scheduler.rebuild("deleted") } }
                }) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

