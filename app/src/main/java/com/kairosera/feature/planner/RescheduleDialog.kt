package com.kairosera.feature.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kairosera.R
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.KTimePickerDialog
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.domain.model.Task
import com.kairosera.ui.appContainer
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Move one occurrence to another day/time. For a repeating task only this occurrence moves;
 * the series itself is never rewritten.
 */
@Composable
fun RescheduleDialog(taskId: Long, date: LocalDate, onDismiss: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var task by remember { mutableStateOf<Task?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var newDate by remember { mutableStateOf(date.plusDays(1)) }
    var newTime by remember { mutableStateOf<LocalTime?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val dateFmt = rememberMediumDateFormatter()
    val timeFmt = rememberTimeFormatter()

    LaunchedEffect(taskId) {
        task = runCatching { c.tasks.getTask(taskId) }.getOrNull()?.takeIf { it.isActive }
        newTime = task?.startTime
        loaded = true
    }
    if (!loaded) return
    val t = task
    if (t == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
            text = { Text(stringResource(R.string.task_missing)) },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reschedule_title)) },
        text = {
            Column {
                Text(t.title)
                if (t.isRecurring) Text(stringResource(R.string.reschedule_recurring_note))
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text(dateFmt(newDate)) }
                if (t.startTime != null) {
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth()) { Text(newTime?.let(timeFmt).orEmpty()) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching { c.reschedule(t.id, date, newDate, newTime) }
                    onDismiss()
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickDate) KDatePickerDialog(newDate, onDismiss = { pickDate = false }) { newDate = it }
    if (pickTime) KTimePickerDialog(newTime ?: LocalTime.of(9, 0), onDismiss = { pickTime = false }) { newTime = it }
}
