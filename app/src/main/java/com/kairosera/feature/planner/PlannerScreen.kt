package com.kairosera.feature.planner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.ErrorState
import com.kairosera.core.ui.components.KProgressBar
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.MessageState
import com.kairosera.core.ui.components.Pill
import com.kairosera.core.ui.components.SwipeAction
import com.kairosera.core.ui.components.SwipeRow
import com.kairosera.core.ui.components.SwipeTones
import com.kairosera.core.ui.components.TaskCheck
import com.kairosera.core.ui.components.ToastKind
import com.kairosera.core.ui.components.UiState
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.core.ui.components.toneFor
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.Space
import com.kairosera.domain.model.Category
import com.kairosera.domain.model.Priority
import com.kairosera.domain.model.TaskOccurrence
import com.kairosera.domain.usecase.DaySummary
import com.kairosera.ui.kairosViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Today: a day strip, one progress line, and the day as a gentle timeline.
 * Swipe right to complete, swipe left to reschedule or delete, long press for more, tap for details.
 */
@Composable
fun PlannerScreen(initialDate: LocalDate?, onOpenTask: (Long, LocalDate) -> Unit, onNewTask: (LocalDate) -> Unit) {
    val vm = kairosViewModel(key = "planner-${initialDate?.toEpochDay()}") { PlannerViewModel(it, initialDate) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val movedText = stringResource(R.string.task_moved_to_trash)
    val undoText = stringResource(R.string.undo)
    val failedText = stringResource(R.string.error_action_failed)
    var reschedule by remember { mutableStateOf<TaskOccurrence?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is PlannerEvent.Trashed -> toaster.show(movedText, ToastKind.UNDO, action = undoText) { vm.undoTrash(e.taskId) }
                PlannerEvent.Failed -> toaster.show(failedText, ToastKind.ERROR)
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
            val mode = (state as? UiState.Ready)?.data?.mode ?: PlannerMode.DAY
            TodayHeader(selected, mode, onToggleMode = { vm.setMode(if (mode == PlannerMode.MONTH) PlannerMode.DAY else PlannerMode.MONTH) }, onToday = { vm.select(LocalDate.now()) })
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(onRetry = vm::retry)
                is UiState.Ready -> when (s.data.mode) {
                    PlannerMode.MONTH -> MonthView(s.data, vm, onSelect = { vm.select(it); vm.setMode(PlannerMode.DAY) })
                    else -> DayView(s.data, vm, onOpenTask, onNewTask, onReschedule = { reschedule = it })
                }
            }
        }
    }
    reschedule?.let { o -> RescheduleDialog(taskId = o.task.id, date = o.date, onDismiss = { reschedule = null }) }
}

@Composable
private fun TodayHeader(selected: LocalDate, mode: PlannerMode, onToggleMode: () -> Unit, onToday: () -> Unit) {
    val today = LocalDate.now()
    val dateFmt = rememberDateFormatter(if (mode == PlannerMode.MONTH) "MMMM yyyy" else "EEEE, d MMMM")
    val title = when {
        mode == PlannerMode.MONTH -> stringResource(R.string.planner_calendar)
        selected == today -> stringResource(R.string.today)
        selected == today.plusDays(1) -> stringResource(R.string.tomorrow)
        selected == today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> rememberDateFormatter("EEEE")(selected)
    }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.gutter, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(dateFmt(selected), style = MaterialTheme.typography.bodyLarge, color = Kairos.colors.muted)
        }
        if (selected != today) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.today), style = MaterialTheme.typography.labelLarge) }
        }
        IconButton(onClick = onToggleMode) {
            Icon(
                if (mode == PlannerMode.MONTH) Icons.Outlined.ViewAgenda else Icons.Outlined.CalendarMonth,
                contentDescription = stringResource(if (mode == PlannerMode.MONTH) R.string.planner_show_timeline else R.string.planner_show_calendar),
            )
        }
    }
}

@Composable
private fun DayView(
    data: PlannerData,
    vm: PlannerViewModel,
    onOpenTask: (Long, LocalDate) -> Unit,
    onNewTask: (LocalDate) -> Unit,
    onReschedule: (TaskOccurrence) -> Unit,
) {
    val items = data.byDate[data.selected].orEmpty()
    val progress = DaySummary.progress(items)
    val timeFmt = rememberTimeFormatter()
    val (allDay, timed) = items.partition { it.task.startTime == null }
    val ordered = allDay + timed.sortedBy { it.task.startTime }
    LazyColumn(contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.m, bottom = 32.dp)) {
        item(key = "strip") { WeekStrip(data, onSelect = vm::select, onShift = vm::shift) }
        item(key = "progress") {
            Column(Modifier.padding(top = Space.l, bottom = Space.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.planner_summary, progress.total, progress.done),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Kairos.colors.muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (progress.total > 0) {
                        Text("${(progress.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Kairos.colors.success.strong)
                    }
                }
                Spacer(Modifier.height(Space.s))
                KProgressBar(progress.fraction)
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                MessageState(Icons.Outlined.EventAvailable, stringResource(R.string.day_empty_title), stringResource(R.string.day_empty_body))
            }
        }
        items(ordered, key = { "${it.task.id}-${it.date}" }) { o ->
            val index = ordered.indexOf(o)
            val label = o.task.startTime?.let(timeFmt) ?: stringResource(R.string.all_day_short)
            val showLabel = index == 0 || ordered[index - 1].task.startTime != o.task.startTime
            TimelineItem(
                timeLabel = if (showLabel) label else null,
                done = o.isDone,
                isLast = index == ordered.lastIndex,
                isNow = data.selected == LocalDate.now() && o.task.startTime?.let { isNow(it, o.task.endTime) } == true,
            ) {
                TaskCard(o, data.categories[o.task.categoryId], timeFmt, vm, onOpenTask, onReschedule)
            }
        }
        item(key = "add") {
            Box(Modifier.fillMaxWidth().padding(top = Space.xl), contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = { onNewTask(data.selected) },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Kairos.colors.brand, contentColor = Kairos.colors.onBrand),
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_task)) }
            }
        }
    }
}

private fun isNow(start: LocalTime, end: LocalTime?): Boolean {
    val now = LocalTime.now()
    return !now.isBefore(start) && now.isBefore(end ?: start.plusHours(1))
}

/** Mon–Sun for the selected week. Swipe the strip or use the arrows to change week. */
@Composable
private fun WeekStrip(data: PlannerData, onSelect: (LocalDate) -> Unit, onShift: (Long) -> Unit) {
    val monday = data.selected.with(DayOfWeek.MONDAY)
    val weekdayFmt = rememberDateFormatter("EEE")
    val today = LocalDate.now()
    var drag by remember { mutableStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onHorizontalDrag = { _, d -> drag += d },
                onDragEnd = { if (drag > 120f) onShift(-1) else if (drag < -120f) onShift(1) },
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShift(-1) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous), tint = Kairos.colors.muted)
        }
        (0L until 7L).forEach { i ->
            val day = monday.plusDays(i)
            val list = data.byDate[day].orEmpty()
            val p = DaySummary.progress(list)
            val isSel = day == data.selected
            val a11y = stringResource(R.string.month_cell_a11y, day.dayOfMonth, p.done, p.total)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (isSel) Kairos.colors.brand else Color.Transparent)
                    .then(if (day == today && !isSel) Modifier.border(BorderStroke(1.dp, Kairos.colors.sun), MaterialTheme.shapes.medium) else Modifier)
                    .clickable { onSelect(day) }
                    .semantics(mergeDescendants = true) { contentDescription = a11y }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fg = if (isSel) Kairos.colors.onBrand else MaterialTheme.colorScheme.onSurface
                Text(weekdayFmt(day), style = MaterialTheme.typography.labelMedium, color = if (isSel) fg.copy(alpha = 0.8f) else Kairos.colors.muted, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = fg)
                Spacer(Modifier.height(6.dp))
                val dot = when {
                    p.total == 0 -> Color.Transparent
                    p.remaining == 0 -> if (isSel) Kairos.colors.sun else Kairos.colors.success.strong
                    else -> if (isSel) Kairos.colors.onBrand.copy(alpha = 0.6f) else Kairos.colors.muted.copy(alpha = 0.6f)
                }
                Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
            }
        }
        IconButton(onClick = { onShift(1) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next), tint = Kairos.colors.muted)
        }
    }
}

/** Left rail with a dot per time slot and a thin connecting line; the time sits above its card. */
@Composable
private fun TimelineItem(timeLabel: String?, done: Boolean, isLast: Boolean, isNow: Boolean, content: @Composable () -> Unit) {
    val lineColor = Kairos.colors.line
    val dotColor = when {
        done -> Kairos.colors.success.strong
        isNow -> Kairos.colors.sun
        else -> MaterialTheme.colorScheme.outline
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(20.dp).fillMaxHeight().drawBehind {
                val x = 6.dp.toPx()
                val top = if (timeLabel != null) 12.dp.toPx() else 0f
                if (!isLast) drawLine(lineColor, Offset(x, top), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                else drawLine(lineColor, Offset(x, 0f), Offset(x, top), strokeWidth = 2.dp.toPx())
                if (timeLabel != null) drawCircle(dotColor, radius = 5.dp.toPx(), center = Offset(x, top))
            },
        )
        Column(Modifier.weight(1f).padding(bottom = 10.dp)) {
            if (timeLabel != null) {
                Text(
                    timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isNow) Kairos.colors.motivation.strong else Kairos.colors.muted,
                    fontWeight = if (isNow) FontWeight.SemiBold else null,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    o: TaskOccurrence,
    category: Category?,
    timeFmt: (LocalTime) -> String,
    vm: PlannerViewModel,
    onOpenTask: (Long, LocalDate) -> Unit,
    onReschedule: (TaskOccurrence) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val doneLabel = stringResource(if (o.isDone) R.string.planner_mark_open else R.string.done)
    SwipeRow(
        rightLabel = doneLabel,
        rightIcon = Icons.Outlined.Check,
        rightTone = SwipeTones.complete,
        onSwipeRight = { if (!o.isSkipped) vm.setDone(o, !o.isDone) },
        leftActions = listOf(
            SwipeAction(stringResource(R.string.action_reschedule), Icons.Outlined.EventRepeat, SwipeTones.reschedule) { onReschedule(o) },
            SwipeAction(stringResource(R.string.planner_delete), Icons.Outlined.DeleteOutline, SwipeTones.delete) { vm.trash(o) },
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Kairos.colors.card,
            border = BorderStroke(1.dp, Kairos.colors.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.combinedClickable(onClick = { onOpenTask(o.task.id, o.date) }, onLongClick = { menu = true })
                    .heightIn(min = 64.dp)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskCheck(
                    checked = o.isDone,
                    onCheckedChange = { vm.setDone(o, it) },
                    label = stringResource(R.string.task_mark_done_a11y, o.task.title),
                    enabled = !o.isSkipped,
                )
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(
                        o.task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (o.isDone || o.isSkipped) TextDecoration.LineThrough else null,
                        color = if (o.isDone || o.isSkipped) Kairos.colors.muted else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (category != null) Pill(category.name, toneFor(category))
                        val end = o.task.endTime
                        if (end != null && o.task.startTime != null) {
                            Text(stringResource(R.string.planner_until, timeFmt(end)), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                        }
                        if (o.task.subtasks.isNotEmpty()) {
                            Text(
                                stringResource(R.string.subtasks_count, o.task.subtasks.count { it.done }, o.task.subtasks.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = Kairos.colors.muted,
                            )
                        }
                        if (o.isSkipped) Text(stringResource(R.string.skipped), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                        if (o.task.isRecurring) Icon(Icons.Outlined.Repeat, stringResource(R.string.repeats), Modifier.size(14.dp), tint = Kairos.colors.muted)
                        if (o.task.reminders.isNotEmpty()) Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.has_reminder), Modifier.size(14.dp), tint = Kairos.colors.muted)
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
}

@Composable
fun PriorityMark(priority: Priority) {
    if (priority == Priority.NONE || priority == Priority.LOW) return
    val tone = if (priority == Priority.HIGH) Kairos.colors.activity else Kairos.colors.info
    val text = stringResource(if (priority == Priority.HIGH) R.string.priority_high else R.string.priority_medium)
    // A small flag with a word for screen readers, so priority is never conveyed by color alone.
    Box(
        Modifier.padding(start = 8.dp).size(width = 4.dp, height = 28.dp).clip(CircleShape).background(tone.strong).semantics { contentDescription = text },
    )
}

@Composable
fun timeRange(start: LocalTime?, end: LocalTime?, fmt: (LocalTime) -> String): String? = when {
    start == null -> null
    end == null -> fmt(start)
    else -> "${fmt(start)} – ${fmt(end)}"
}

@Composable
private fun MonthView(data: PlannerData, vm: PlannerViewModel, onSelect: (LocalDate) -> Unit) {
    val first = data.from
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
    val cells: List<LocalDate?> = List(lead) { null } + generateSequence(first) { it.plusDays(1) }.takeWhile { it <= data.to }.toList()
    val weekdayFmt = rememberDateFormatter("EEEEE")
    val monthFmt = rememberDateFormatter("MMMM yyyy")
    val today = LocalDate.now()
    val month = data.byDate.filterKeys { it in data.from..data.to }.values.flatten()
    val monthProgress = DaySummary.progress(month)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
            Text(monthFmt(first), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next)) }
        }
        Surface(shape = MaterialTheme.shapes.large, color = Kairos.colors.card, border = BorderStroke(1.dp, Kairos.colors.line)) {
            Column(Modifier.padding(12.dp)) {
                Row {
                    (0L until 7L).forEach { i ->
                        Text(
                            weekdayFmt(first.with(DayOfWeek.MONDAY).plusDays(i)),
                            Modifier.weight(1f).padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = Kairos.colors.muted,
                        )
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row {
                        week.forEach { day -> MonthCell(day, data.byDate[day].orEmpty(), day == today, day == data.selected, onSelect, Modifier.weight(1f)) }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.l))
        if (monthProgress.total > 0) {
            Text(stringResource(R.string.planner_month_summary, monthProgress.done, monthProgress.total), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Space.s))
            KProgressBar(monthProgress.fraction)
            Spacer(Modifier.height(Space.l))
        }
        Text(stringResource(R.string.month_legend), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
    }
}

@Composable
private fun MonthCell(day: LocalDate?, items: List<TaskOccurrence>, isToday: Boolean, isSelected: Boolean, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    if (day == null) { Spacer(modifier.aspectRatio(1f)); return }
    val p = DaySummary.progress(items)
    val a11y = stringResource(R.string.month_cell_a11y, day.dayOfMonth, p.done, p.total)
    Column(
        modifier.aspectRatio(1f).padding(3.dp).clip(CircleShape)
            .background(if (isSelected) Kairos.colors.brand else Color.Transparent)
            .clickable { onSelect(day) }
            .semantics(mergeDescendants = true) { contentDescription = a11y },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> Kairos.colors.onBrand
                isToday -> Kairos.colors.motivation.strong
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.height(3.dp))
        // Filled green = everything done, amber strengthening with progress = planned, nothing = free.
        when {
            p.total == 0 -> Spacer(Modifier.size(6.dp))
            p.remaining == 0 -> Box(Modifier.size(6.dp).clip(CircleShape).background(Kairos.colors.success.strong))
            else -> Box(Modifier.size(6.dp).clip(CircleShape).background(Kairos.colors.sun.copy(alpha = 0.35f + 0.65f * p.fraction)))
        }
    }
}
