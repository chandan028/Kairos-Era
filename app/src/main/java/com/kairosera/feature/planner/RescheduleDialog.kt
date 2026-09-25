package com.kairosera.feature.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairosera.R
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.KTimePickerDialog
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.ToastKind
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.model.Task
import com.kairosera.ui.appContainer
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Move one occurrence to another day/time. For a repeating task only this occurrence moves;
 * the series itself is never rewritten. Quick choices first; a date and time picker for the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(taskId: Long, date: LocalDate, onDismiss: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var task by remember { mutableStateOf<Task?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<LocalDate?>(null) }
    val dateFmt = rememberMediumDateFormatter()
    val timeFmt = rememberTimeFormatter()
    val movedText = stringResource(R.string.reschedule_moved)
    val failedText = stringResource(R.string.error_action_failed)

    LaunchedEffect(taskId) {
        task = runCatching { c.tasks.getTask(taskId) }.getOrNull()?.takeIf { it.isActive }
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

    fun move(to: LocalDate, time: LocalTime?) {
        scope.launch {
            val ok = runCatching { c.reschedule(t.id, date, to, time) }.isSuccess
            if (ok) toaster.show(movedText.format(dateFmt(to)), ToastKind.INFO) else toaster.show(failedText, ToastKind.ERROR)
            onDismiss()
        }
    }

    val today = LocalDate.now()
    val laterToday = t.startTime?.let { LocalTime.now().plusHours(2).withMinute(0).withSecond(0).withNano(0) }
        ?.takeIf { date == today && it.isAfter(LocalTime.now()) && it.isBefore(LocalTime.of(23, 0)) }
    val tomorrow = maxOf(date, today).plusDays(1)
    val nextWeek = maxOf(date, today).with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.reschedule_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp))
            Text(t.title, style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), maxLines = 2)
            if (t.isRecurring) {
                Text(stringResource(R.string.reschedule_recurring_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, modifier = Modifier.padding(horizontal = 12.dp))
            }
            Spacer(Modifier.height(8.dp))
            if (laterToday != null) {
                Choice(Icons.Outlined.Schedule, stringResource(R.string.reschedule_later_today), timeFmt(laterToday)) { move(today, laterToday) }
            }
            Choice(Icons.Outlined.WbSunny, stringResource(R.string.tomorrow), dateFmt(tomorrow)) { move(tomorrow, t.startTime) }
            Choice(Icons.Outlined.DateRange, stringResource(R.string.reschedule_next_week), dateFmt(nextWeek)) { move(nextWeek, t.startTime) }
            Choice(Icons.Outlined.CalendarMonth, stringResource(R.string.reschedule_pick), null) { pickDate = true }
        }
    }
    if (pickDate) {
        KDatePickerDialog(tomorrow, onDismiss = { pickDate = false }) { d -> if (t.startTime != null) pickTime = d else move(d, null) }
    }
    pickTime?.let { d ->
        KTimePickerDialog(t.startTime ?: LocalTime.of(9, 0), onDismiss = { pickTime = null }) { time -> move(d, time) }
    }
}

@Composable
private fun Choice(icon: ImageVector, title: String, detail: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        trailingContent = detail?.let { { Text(it, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = Kairos.colors.motivation.strong) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    )
}
