package com.kairosera.feature.planner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.domain.model.Priority
import com.kairosera.domain.model.TaskOccurrence
import com.kairosera.domain.usecase.DaySummary
import com.kairosera.ui.kairosViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(initialDate: LocalDate?, onOpenTask: (Long, LocalDate) -> Unit, onNewTask: (LocalDate) -> Unit) {
    val vm = kairosViewModel(key = "planner-${initialDate?.toEpochDay()}") { PlannerViewModel(it, initialDate) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val movedText = stringResource(R.string.task_moved_to_trash)
    val undoText = stringResource(R.string.undo)
    val failedText = stringResource(R.string.error_action_failed)
    var reschedule by remember { mutableStateOf<TaskOccurrence?>(null) }
    var pickDate by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is PlannerEvent.Trashed -> {
                    val r = snackbar.showSnackbar(movedText, actionLabel = undoText, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) vm.undoTrash(e.taskId)
                }
                PlannerEvent.Failed -> snackbar.showSnackbar(failedText)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNewTask(selected) }) { Icon(Icons.Filled.Add, stringResource(R.string.new_task)) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                PlannerHeader(state, selected, vm, onPickDate = { pickDate = true })
                when (val s = state) {
                    UiState.Loading -> LoadingState()
                    is UiState.Error -> ErrorState(onRetry = vm::retry)
                    is UiState.Ready -> when (s.data.mode) {
                        PlannerMode.DAY -> DayList(s.data.byDate[s.data.selected].orEmpty(), vm, onOpenTask, onReschedule = { reschedule = it })
                        PlannerMode.WEEK -> WeekList(s.data, vm, onOpenTask, onReschedule = { reschedule = it })
                        PlannerMode.MONTH -> MonthGrid(s.data, onSelect = { vm.select(it); vm.setMode(PlannerMode.DAY) })
                    }
                }
            }
        }
    }
    if (pickDate) KDatePickerDialog(initial = selected, onDismiss = { pickDate = false }, onPick = vm::select)
    reschedule?.let { o -> RescheduleDialog(taskId = o.task.id, date = o.date, onDismiss = { reschedule = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerHeader(state: UiState<PlannerData>, selected: LocalDate, vm: PlannerViewModel, onPickDate: () -> Unit) {
    val mode = (state as? UiState.Ready)?.data?.mode ?: PlannerMode.DAY
    val today = LocalDate.now()
    val titleFmt = rememberDateFormatter(if (mode == PlannerMode.MONTH) "MMMM yyyy" else "EEE, d MMM yyyy")
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_today), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).padding(start = 4.dp))
            IconButton(onClick = onPickDate) { Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.pick_date)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf(
                today.minusDays(1) to R.string.yesterday,
                today to R.string.today,
                today.plusDays(1) to R.string.tomorrow,
            ).forEach { (d, label) ->
                FilterChip(selected = selected == d && mode == PlannerMode.DAY, onClick = { vm.select(d); vm.setMode(PlannerMode.DAY) }, label = { Text(stringResource(label)) })
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PlannerMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { vm.setMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, PlannerMode.entries.size),
                ) {
                    Text(stringResource(when (m) { PlannerMode.DAY -> R.string.mode_day; PlannerMode.WEEK -> R.string.mode_week; PlannerMode.MONTH -> R.string.mode_month }))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
            Text(titleFmt(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next)) }
        }
    }
}

@Composable
private fun DayList(items: List<TaskOccurrence>, vm: PlannerViewModel, onOpenTask: (Long, LocalDate) -> Unit, onReschedule: (TaskOccurrence) -> Unit) {
    if (items.isEmpty()) {
        MessageState(Icons.Outlined.EventAvailable, stringResource(R.string.day_empty_title), stringResource(R.string.day_empty_body))
        return
    }
    val progress = DaySummary.progress(items)
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
        item {
            Text(
                stringResource(R.string.home_tasks_done, progress.done, progress.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        items(items, key = { "${it.task.id}-${it.date}" }) { o -> OccurrenceRow(o, vm, onOpenTask, onReschedule) }
    }
}

@Composable
private fun WeekList(data: PlannerData, vm: PlannerViewModel, onOpenTask: (Long, LocalDate) -> Unit, onReschedule: (TaskOccurrence) -> Unit) {
    val dayFmt = rememberDateFormatter("EEEE, d MMM")
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        var d = data.from
        val days = buildList { while (d <= data.to) { add(d); d = d.plusDays(1) } }
        days.forEach { day ->
            val list = data.byDate[day].orEmpty()
            item(key = "h-$day") {
                SectionLabel(
                    dayFmt(day) + if (list.isNotEmpty()) "  ·  ${DaySummary.progress(list).done}/${DaySummary.progress(list).total}" else "",
                    Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            if (list.isEmpty()) {
                item(key = "e-$day") {
                    Text(stringResource(R.string.week_day_free), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
            }
            items(list, key = { "${it.task.id}-${it.date}" }) { o -> OccurrenceRow(o, vm, onOpenTask, onReschedule) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OccurrenceRow(o: TaskOccurrence, vm: PlannerViewModel, onOpenTask: (Long, LocalDate) -> Unit, onReschedule: (TaskOccurrence) -> Unit) {
    val timeFmt = rememberTimeFormatter()
    var menu by remember { mutableStateOf(false) }
    val doneSubtasks = o.task.subtasks.count { it.done }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.combinedClickable(onClick = { onOpenTask(o.task.id, o.date) }, onLongClick = { menu = true }).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = o.isDone, onCheckedChange = { vm.setDone(o, it) }, enabled = !o.isSkipped)
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    o.task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (o.isDone || o.isSkipped) TextDecoration.LineThrough else null,
                    color = if (o.isDone || o.isSkipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val meta = buildList {
                    add(timeRange(o.task.startTime, o.task.endTime, timeFmt) ?: stringResource(R.string.all_day))
                    if (o.task.subtasks.isNotEmpty()) add(stringResource(R.string.subtasks_count, doneSubtasks, o.task.subtasks.size))
                    if (o.isSkipped) add(stringResource(R.string.skipped))
                    if (o.task.isSample) add(stringResource(R.string.example_badge))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (o.task.isRecurring) Icon(Icons.Outlined.Repeat, stringResource(R.string.repeats), Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (o.task.reminders.isNotEmpty()) Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.has_reminder), Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PriorityMark(o.task.priority)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onOpenTask(o.task.id, o.date) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_reschedule)) }, onClick = { menu = false; onReschedule(o) })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_trash)) }, onClick = { menu = false; vm.trash(o) })
            }
        }
    }
}

@Composable
fun PriorityMark(priority: Priority) {
    if (priority == Priority.NONE) return
    val (color, label) = when (priority) {
        Priority.HIGH -> MaterialTheme.colorScheme.secondary to R.string.priority_high
        Priority.MEDIUM -> MaterialTheme.colorScheme.primary to R.string.priority_medium
        else -> MaterialTheme.colorScheme.outline to R.string.priority_low
    }
    val text = stringResource(label)
    // Letter plus color, so priority is never conveyed by color alone.
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)).semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(text.take(1), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun timeRange(start: LocalTime?, end: LocalTime?, fmt: (LocalTime) -> String): String? = when {
    start == null -> null
    end == null -> fmt(start)
    else -> "${fmt(start)} – ${fmt(end)}"
}

@Composable
private fun MonthGrid(data: PlannerData, onSelect: (LocalDate) -> Unit) {
    val first = data.from
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
    val cells: List<LocalDate?> = List(lead) { null } + generateSequence(first) { it.plusDays(1) }.takeWhile { it <= data.to }.toList()
    val weekdayFmt = rememberDateFormatter("EEEEE")
    val today = LocalDate.now()
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row {
            (0L until 7L).forEach { i ->
                Text(
                    weekdayFmt(first.with(DayOfWeek.MONDAY).plusDays(i)),
                    Modifier.weight(1f).padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { day -> MonthCell(day, data.byDate[day].orEmpty(), day == today, day == data.selected, onSelect, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.month_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun MonthCell(day: LocalDate?, items: List<TaskOccurrence>, isToday: Boolean, isSelected: Boolean, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    if (day == null) { Spacer(modifier.aspectRatio(1f)); return }
    val p = DaySummary.progress(items)
    val a11y = stringResource(R.string.month_cell_a11y, day.dayOfMonth, p.done, p.total)
    Column(
        modifier.aspectRatio(1f).padding(2.dp).clip(MaterialTheme.shapes.small)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onSelect(day) }
            .semantics(mergeDescendants = true) { contentDescription = a11y },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        // Filled dot = everything done, ring = something planned, nothing = free day.
        when {
            p.total == 0 -> Spacer(Modifier.size(8.dp))
            p.remaining == 0 -> Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
            else -> Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f + 0.75f * p.fraction)))
        }
    }
}

