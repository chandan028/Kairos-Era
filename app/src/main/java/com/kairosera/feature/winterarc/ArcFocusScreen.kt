package com.kairosera.feature.winterarc

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.winterarc.FocusSession
import com.kairosera.domain.winterarc.HabitKind
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

/**
 * A focus session: choose a length and what you are working on, then a large countdown with
 * Pause, Complete and Cancel. Nothing watches the phone; interruptions are counted by hand.
 * Started from the Study Plan it records study time against that task.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArcFocusScreen(vm: ArcViewModel, isStudy: Boolean, studyTaskId: Long?, studyTitle: String?, studyMinutes: Int?, onClose: () -> Unit) {
    val tone = Arc.tone(if (isStudy) HabitKind.STUDY else HabitKind.DEEP_WORK)
    val home by vm.home.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current

    var task by rememberSaveable { mutableStateOf(studyTitle.orEmpty()) }
    var lengthMin by rememberSaveable { mutableIntStateOf((studyMinutes ?: 50).coerceIn(5, 180)) }
    var started by rememberSaveable { mutableStateOf(false) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var runStart by rememberSaveable { mutableLongStateOf(0L) }
    var banked by rememberSaveable { mutableLongStateOf(0L) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var interruptions by rememberSaveable { mutableIntStateOf(0) }
    var confirmLeave by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val elapsedMs = banked + if (started && !paused) now - runStart else 0L
    val totalMs = lengthMin * 60_000L
    val leftSec = ((totalMs - elapsedMs).coerceAtLeast(0) / 1000).toInt()

    KeepScreenOn(started && !paused)
    LaunchedEffect(started, paused) {
        while (started && !paused) { now = System.currentTimeMillis(); delay(250) }
    }

    fun finish(completed: Boolean) {
        val seconds = (elapsedMs / 1000).toInt()
        vm.saveFocus(
            FocusSession(
                date = LocalDate.now(), durationSeconds = seconds, completed = completed, interruptions = interruptions,
                task = task.trim(), kind = if (isStudy) FocusSession.KIND_STUDY else FocusSession.KIND_FOCUS,
                startedAt = Instant.ofEpochMilli(startedAt), studyTaskId = studyTaskId,
            ),
        )
        if (seconds >= 30) toaster.show(context.getString(R.string.wa_focus_saved, (seconds + 30) / 60))
        onClose()
    }

    LaunchedEffect(leftSec, started) { if (started && leftSec == 0) finish(true) }
    BackHandler { if (started) confirmLeave = true else onClose() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (started) confirmLeave = true else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(stringResource(if (isStudy) R.string.wa_h_study else R.string.wa_focus_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).widthIn(max = 520.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!started) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(task, { task = it.take(200) }, label = { Text(stringResource(R.string.wa_focus_task)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.wa_focus_length), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted, modifier = Modifier.fillMaxWidth())
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(25, 50, 60, 90) + listOfNotNull(studyMinutes?.takeIf { it in 5..180 })).distinct().sorted().forEach { m ->
                        FilterChip(selected = lengthMin == m, onClick = { lengthMin = m }, label = { Text("$m min") })
                    }
                }
                Spacer(Modifier.height(28.dp))
            } else {
                Spacer(Modifier.height(24.dp))
                if (task.isNotBlank()) Text(task, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            ProgressRing(
                fraction = if (started) elapsedMs.toFloat() / totalMs else 0f, color = tone.strong, size = 260.dp, stroke = 12.dp, track = tone.soft,
            ) {
                Text(
                    ArcFormat.longClock(if (started) leftSec else lengthMin * 60),
                    fontSize = 56.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(16.dp))
            if (started && interruptions > 0) Text(stringResource(R.string.wa_focus_interruptions, interruptions), color = Kairos.colors.muted)
            if (!started) {
                home?.let { d ->
                    val mins = if (isStudy) d.inputs.studyMinutes else d.inputs.focusMinutes
                    Text(stringResource(R.string.wa_focus_today, mins, d.target(if (isStudy) HabitKind.STUDY else HabitKind.DEEP_WORK).toInt()), color = Kairos.colors.muted)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.wa_focus_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, textAlign = TextAlign.Center)
            }
        }
        if (!started) {
            ArcButton(stringResource(R.string.wa_focus_start), {
                val t = System.currentTimeMillis()
                startedAt = t; runStart = t; now = t; banked = 0; started = true; paused = false
            }, icon = Icons.Outlined.PlayArrow, color = tone.strong)
        } else {
            OutlinedButton(onClick = { interruptions++ }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.wa_focus_interrupt)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcButton(
                    stringResource(if (paused) R.string.wa_focus_resume else R.string.wa_focus_pause),
                    {
                        val t = System.currentTimeMillis()
                        if (paused) { runStart = t; now = t; paused = false } else { banked += t - runStart; now = t; paused = true }
                    },
                    Modifier.weight(1f), icon = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    color = tone.soft, contentColor = tone.strong,
                )
                ArcButton(stringResource(R.string.wa_focus_complete), { finish(true) }, Modifier.weight(1f), icon = Icons.Outlined.Check, color = tone.strong)
            }
            TextButton(onClick = { confirmLeave = true }) {
                Icon(Icons.Outlined.Close, contentDescription = null)
                Text(stringResource(R.string.wa_focus_cancel))
            }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.wa_focus_leave)) },
            text = { Text(stringResource(R.string.wa_focus_leave_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; finish(false) }) { Text(stringResource(R.string.wa_focus_cancel)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.wa_focus_keep)) } },
        )
    }
}
