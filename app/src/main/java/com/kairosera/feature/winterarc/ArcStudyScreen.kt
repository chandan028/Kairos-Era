package com.kairosera.feature.winterarc

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.currentLocale
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.StudyCategory
import com.kairosera.domain.winterarc.StudyTask
import com.kairosera.ui.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** The Study Plan: today's tasks, this week, and every topic, all from the imported plan. */
@Composable
fun ArcStudyScreen(vm: ArcViewModel, initialDate: LocalDate?, onBack: (() -> Unit)?, onStartFocus: (StudyTask?) -> Unit) {
    val c = appContainer()
    val today by vm.today.collectAsStateWithLifecycle()
    val all by remember { c.winterArc.allStudy }.collectAsStateWithLifecycle(initialValue = null)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dayEpoch by rememberSaveable { mutableStateOf((initialDate ?: today).toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayEpoch)
    var editing by remember { mutableStateOf<StudyTask?>(null) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val locale = currentLocale()
    val dayFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(5_000_000) } }.getOrNull()
            }
            val result = text?.let { runCatching { c.winterArc.importPlan(it) }.onFailure { e -> SafeLog.error("plan_import", e) }.getOrNull() }
            toaster.show(
                when {
                    result == null -> context.getString(R.string.wa_study_import_failed)
                    result.tasks.isEmpty() -> context.getString(R.string.wa_study_import_none)
                    else -> context.getString(R.string.wa_study_imported, result.tasks.size, dayFmt.format(result.tasks.minOf { it.date }))
                },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.wa_study_title), onBack = onBack) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.wa_study_import)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.wa_study_add)) }, onClick = {
                        menu = false
                        editing = StudyTask(date = day, title = "", category = StudyCategory.DSA, durationMinutes = 60, source = StudyTask.SOURCE_MANUAL)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.wa_study_import)) }, onClick = {
                        menu = false
                        importer.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*"))
                    })
                    if (c.winterArc.hasBundledPlan()) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.wa_study_reimport)) }, onClick = {
                            menu = false
                            scope.launch {
                                val r = runCatching { c.winterArc.reimportBundledPlan() }.getOrNull()
                                if (r != null && r.tasks.isNotEmpty()) toaster.show(context.getString(R.string.wa_study_imported, r.tasks.size, dayFmt.format(r.tasks.minOf { it.date })))
                            }
                        })
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize().align(Alignment.CenterHorizontally).widthIn(max = ContentMaxWidth).padding(horizontal = 16.dp)) {
            ArcTabs(
                listOf(stringResource(R.string.wa_tab_today), stringResource(R.string.wa_tab_week), stringResource(R.string.wa_tab_all)),
                tab, { tab = it },
            )
            Spacer(Modifier.height(12.dp))
            val tasks = all
            when {
                tasks == null -> Unit
                tasks.isEmpty() -> EmptyPlan(onImport = { importer.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) })
                tab == 0 -> DayTab(vm, tasks, day, today, dayFmt.format(day), onShift = { dayEpoch = day.plusDays(it.toLong()).toEpochDay() }, onEdit = { editing = it }, onStartFocus = onStartFocus)
                tab == 1 -> WeekTab(vm, tasks, today, onOpenDay = { dayEpoch = it.toEpochDay(); tab = 0 })
                else -> AllTab(vm, tasks, today, onEdit = { editing = it })
            }
        }
    }
    editing?.let { t -> TaskEditor(t, onDismiss = { editing = null }, onSave = { vm.saveStudyTask(it); editing = null }, onDelete = { vm.deleteStudyTask(t.id); editing = null }) }
}

@Composable
private fun EmptyPlan(onImport: () -> Unit) {
    ArcCard(Modifier.fillMaxWidth(), color = Arc.tone(HabitKind.STUDY).soft) {
        Text(stringResource(R.string.wa_study_no_plan), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.wa_study_no_plan_body), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
        Spacer(Modifier.height(14.dp))
        ArcButton(stringResource(R.string.wa_study_import), onImport, icon = Icons.Outlined.Description, color = Arc.tone(HabitKind.STUDY).strong)
    }
}

@Composable
private fun DayTab(
    vm: ArcViewModel,
    all: List<StudyTask>,
    day: LocalDate,
    today: LocalDate,
    dayLabel: String,
    onShift: (Int) -> Unit,
    onEdit: (StudyTask) -> Unit,
    onStartFocus: (StudyTask?) -> Unit,
) {
    val tasks = all.filter { it.date == day }.sortedWith(compareBy({ it.position }, { it.id }))
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = all.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekStart.plusDays(6)) }
    val time = rememberTimeFormatter()
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ArcCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.previous)) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.wa_todays_plan), style = MaterialTheme.typography.titleMedium)
                        Text(dayLabel, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                    }
                    Text("${tasks.count { it.completed }} / ${tasks.size}", style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                    IconButton(onClick = { onShift(1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = stringResource(R.string.next)) }
                }
                ArcBar(if (tasks.isEmpty()) 0f else tasks.count { it.completed }.toFloat() / tasks.size, Arc.tone(HabitKind.STUDY).strong, height = 6.dp)
            }
        }
        if (tasks.isEmpty()) item { Text(stringResource(R.string.wa_study_none_today), color = Kairos.colors.muted, modifier = Modifier.padding(8.dp)) }
        items(tasks, key = { it.id }) { t ->
            TaskRow(t, editable = !day.isAfter(today), timeText = t.startTime?.let(time), onToggle = { vm.setStudyDone(t.id, !t.completed) }, onOpen = { onEdit(t) })
        }
        item {
            Spacer(Modifier.height(4.dp))
            ArcButton(stringResource(R.string.wa_start_focus), { onStartFocus(tasks.firstOrNull { !it.completed }) }, icon = Icons.Outlined.PlayArrow)
        }
        item {
            ArcCard(Modifier.fillMaxWidth()) {
                ArcSectionTitle(stringResource(R.string.wa_week_progress), trailing = stringResource(R.string.wa_tasks_of, week.count { it.completed }, week.size))
                ArcBar(if (week.isEmpty()) 0f else week.count { it.completed }.toFloat() / week.size, Arc.primary)
                Spacer(Modifier.height(12.dp))
                WeekDots(week, weekStart, today)
            }
        }
        item {
            ArcCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HabitBadge(Icons.Outlined.Description, Arc.tone(HabitKind.WATER), size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.wa_current_resource), style = MaterialTheme.typography.labelMedium, color = Kairos.colors.muted)
                        Text(stringResource(R.string.wa_resource_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.wa_resource_sub), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                val done = all.count { it.completed }
                Text(stringResource(R.string.wa_overall_progress) + " · " + stringResource(R.string.wa_tasks_of, done, all.size), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                Spacer(Modifier.height(6.dp))
                ArcBar(if (all.isEmpty()) 0f else done.toFloat() / all.size, Arc.tone(HabitKind.WATER).strong, height = 6.dp)
            }
        }
    }
}

@Composable
private fun WeekDots(week: List<StudyTask>, weekStart: LocalDate, today: LocalDate) {
    val locale = currentLocale()
    val dow = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0L..6L).forEach { i ->
            val d = weekStart.plusDays(i)
            val ts = week.filter { it.date == d }
            val state = when {
                ts.isNotEmpty() && ts.all { it.completed } -> com.kairosera.domain.winterarc.DayState.COMPLETE
                ts.any { it.completed } -> com.kairosera.domain.winterarc.DayState.PARTIAL
                d == today -> com.kairosera.domain.winterarc.DayState.TODAY
                else -> com.kairosera.domain.winterarc.DayState.FUTURE
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(22.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(Arc.dayColor(state)))
                Spacer(Modifier.height(4.dp))
                Text(dow.format(d), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
            }
        }
    }
}

@Composable
private fun TaskRow(t: StudyTask, editable: Boolean, timeText: String?, onToggle: () -> Unit, onOpen: () -> Unit, showDate: String? = null) {
    val tone = t.category.tone()
    ArcCard(Modifier.fillMaxWidth(), onClick = onOpen, padding = 0.dp) {
        Row(Modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().heightIn(min = 64.dp).background(tone.strong))
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
                val meta = listOfNotNull(showDate, timeText, stringResource(t.category.label()), stringResource(R.string.wa_minutes_est, t.durationMinutes)).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelMedium, color = tone.strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    t.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.completed) TextDecoration.LineThrough else null,
                    color = if (t.completed) Kairos.colors.muted else MaterialTheme.colorScheme.onSurface,
                )
                if (t.spentMinutes > 0) Text(stringResource(R.string.wa_studied_min, t.spentMinutes), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
            }
            CheckDot(t.completed, tone, t.title, if (editable) onToggle else null)
        }
    }
}

@Composable
private fun WeekTab(vm: ArcViewModel, all: List<StudyTask>, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val locale = currentLocale()
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }
    val week = all.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekStart.plusDays(6)) }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ArcCard(Modifier.fillMaxWidth()) {
                ArcSectionTitle(stringResource(R.string.wa_week_progress), trailing = stringResource(R.string.wa_tasks_of, week.count { it.completed }, week.size))
                ArcBar(if (week.isEmpty()) 0f else week.count { it.completed }.toFloat() / week.size, Arc.primary)
                Spacer(Modifier.height(12.dp))
                WeekDots(week, weekStart, today)
            }
        }
        (0L..6L).forEach { i ->
            val d = weekStart.plusDays(i)
            val ts = week.filter { it.date == d }
            if (ts.isEmpty()) return@forEach
            item(key = "h$i") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpenDay(d) }.padding(vertical = 6.dp, horizontal = 4.dp)) {
                    Text(fmt.format(d), style = MaterialTheme.typography.titleSmall, color = if (d == today) Arc.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text("${ts.count { it.completed }} / ${ts.size}", style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                }
            }
            items(ts, key = { it.id }) { t -> TaskRow(t, !d.isAfter(today), null, { vm.setStudyDone(t.id, !t.completed) }, { onOpenDay(d) }) }
        }
    }
}

@Composable
private fun AllTab(vm: ArcViewModel, all: List<StudyTask>, today: LocalDate, onEdit: (StudyTask) -> Unit) {
    var category by rememberSaveable { mutableStateOf<StudyCategory?>(null) }
    var showDone by rememberSaveable { mutableStateOf(true) }
    val locale = currentLocale()
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val shown = all.filter { (category == null || it.category == category) && (showDone || !it.completed) }
    Column {
        run {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.wa_all_categories)) }) }
                items(StudyCategory.entries) { cat ->
                    FilterChip(selected = category == cat, onClick = { category = if (category == cat) null else cat }, label = { Text(stringResource(cat.label())) })
                }
                item { FilterChip(selected = showDone, onClick = { showDone = !showDone }, label = { Text(stringResource(R.string.wa_show_done)) }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.wa_tasks_of, shown.count { it.completed }, shown.size), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { t ->
                TaskRow(t, !t.date.isAfter(today), null, { vm.setStudyDone(t.id, !t.completed) }, { onEdit(t) }, showDate = fmt.format(t.date))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TaskEditor(task: StudyTask, onDismiss: () -> Unit, onSave: (StudyTask) -> Unit, onDelete: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(task.title) }
    var minutes by rememberSaveable { mutableStateOf(task.durationMinutes.toString()) }
    var notes by rememberSaveable { mutableStateOf(task.notes) }
    var category by rememberSaveable { mutableStateOf(task.category) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task.id == 0L) stringResource(R.string.wa_study_add) else stringResource(R.string.wa_task_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it.take(300) }, label = { Text(stringResource(R.string.wa_task_title)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.wa_task_category), style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudyCategory.entries.forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(stringResource(cat.label())) })
                    }
                }
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.wa_task_duration)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(notes, { notes = it.take(4000) }, label = { Text(stringResource(R.string.wa_task_notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 10)
                if (task.id != 0L) TextButton(onClick = onDelete) { Text(stringResource(R.string.wa_task_delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(task.copy(title = title.trim(), durationMinutes = (minutes.toIntOrNull() ?: 60).coerceIn(5, 720), notes = notes, category = category))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
