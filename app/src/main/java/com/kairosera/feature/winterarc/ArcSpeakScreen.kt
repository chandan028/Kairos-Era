package com.kairosera.feature.winterarc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.ai.AnalysisStep
import com.kairosera.ai.CoachError
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.core.ui.theme.Tone
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.SpeakingSession
import com.kairosera.feature.speech.SpeechPhase
import com.kairosera.feature.speech.SpeechReportViewModel
import com.kairosera.feature.speech.SpeechViewModel
import com.kairosera.feature.speech.coachErrorText
import com.kairosera.ui.kairosViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPEAK_SECONDS = 60

/**
 * 1 Minute Speech: one random topic a day and a one-minute countdown. With the Gemma model
 * installed, the minute is recorded and Gemma, on the phone, coaches it (report screen). Without
 * the model, Start runs the plain timer as before.
 */
@Composable
fun ArcSpeakScreen(
    vm: ArcViewModel,
    onBack: (() -> Unit)?,
    onOpenReport: (Long) -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenCoachSetup: () -> Unit = {},
) {
    val tone = Arc.tone(HabitKind.SPEAK)
    val today by vm.today.collectAsStateWithLifecycle()
    val recent by remember { vm.recentSpeaking() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val speech = kairosViewModel("speech_coach") { SpeechViewModel(it) }
    val reports = kairosViewModel("speech_reports") { SpeechReportViewModel(it.speechSessions) }
    val sessions by reports.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val ui by speech.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<SpeakingSession?>(null) }
    var loaded by remember { mutableStateOf(false) }
    // The plain timer, used when the coach is not set up (or the person chose the timer only).
    var running by rememberSaveable { mutableStateOf(false) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var left by rememberSaveable { mutableIntStateOf(SPEAK_SECONDS) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(today) { session = vm.topicFor(today); loaded = true }
    LaunchedEffect(recent) {
        val id = session?.id ?: return@LaunchedEffect
        recent.firstOrNull { it.id == id }?.let { session = it }
    }

    // A long enough recording completes the day's habit, exactly like the timer did.
    val completed by speech.completedSpeaking.collectAsStateWithLifecycle()
    LaunchedEffect(completed) {
        val (id, seconds) = completed ?: return@LaunchedEffect
        vm.completeSpeaking(id, seconds)
        session?.takeIf { it.id == id }?.let { session = it.copy(completed = true, durationSeconds = seconds) }
        speech.habitRecorded()
    }
    LaunchedEffect(ui.phase) {
        val done = ui.phase as? SpeechPhase.Done ?: return@LaunchedEffect
        speech.reset()
        onOpenReport(done.sessionId)
    }

    // Leaving the app stops a recording (it would otherwise run unseen); coming back re-checks the model.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> speech.onBackground()
                Lifecycle.Event.ON_RESUME -> speech.refreshCoach()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val recording = ui.phase is SpeechPhase.Recording
    KeepScreenOn(running || recording || ui.phase is SpeechPhase.Analyzing)
    LaunchedEffect(running) {
        while (running) {
            left = (SPEAK_SECONDS - ((System.currentTimeMillis() - startedAt) / 1000).toInt()).coerceAtLeast(0)
            if (left == 0) {
                running = false
                session?.let { vm.completeSpeaking(it.id, SPEAK_SECONDS); session = it.copy(completed = true, durationSeconds = SPEAK_SECONDS) }
            }
            delay(200)
        }
    }
    val startTimer = { startedAt = System.currentTimeMillis(); left = SPEAK_SECONDS; running = true; speech.reset() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedOnce = true
        val s = session
        if (granted && s != null) speech.start(s.topic, s.id) else if (!granted) speech.permissionDenied()
    }
    val startRecording = {
        val s = session
        if (s != null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                speech.start(s.topic, s.id)
            } else {
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    val start = { if (ui.coachAvailable) startRecording() else startTimer() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.sc_title),
            subtitle = stringResource(R.string.sc_subtitle),
            onBack = onBack,
            actions = {
                IconButton(onClick = onOpenCoachSetup, enabled = !recording) {
                    Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.sc_coach_settings))
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally).widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val s = session
            ArcCard(Modifier.fillMaxWidth(), color = tone.soft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.wa_speak_topic), style = MaterialTheme.typography.labelLarge, color = tone.strong, modifier = Modifier.weight(1f))
                    if (s != null && !s.completed && !running && ui.phase is SpeechPhase.Ready) {
                        IconButton(onClick = { scope.launch { vm.shuffleTopic(today)?.let { session = it } } }) {
                            Icon(Icons.Outlined.Shuffle, contentDescription = stringResource(R.string.wa_speak_shuffle), tint = tone.strong)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        s != null -> s.topic
                        loaded -> stringResource(R.string.wa_speak_no_topics)
                        else -> "…"
                    },
                    fontFamily = SerifFamily, fontSize = 24.sp, lineHeight = 32.sp, color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(20.dp))

            when (val phase = ui.phase) {
                is SpeechPhase.Recording -> RecordingBlock(phase, tone, onStop = speech::stop, onDiscard = speech::discard)
                is SpeechPhase.Analyzing -> AnalyzingBlock(phase.step, tone, onCancel = speech::cancelAnalysis)
                is SpeechPhase.Failed -> FailedBlock(
                    error = phase.error, canRetry = phase.canRetry, tone = tone,
                    onRetry = speech::retryAnalysis,
                    onRecordAgain = { speech.reset(); startRecording() },
                    onTimerOnly = startTimer,
                    onOpenAppSettings = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }
                    },
                    onOpenCoachSetup = onOpenCoachSetup,
                    showAppSettings = askedOnce,
                )
                else -> TimerBlock(
                    s = s, tone = tone, running = running, left = left,
                    coachOn = ui.coachAvailable,
                    onStart = start,
                    onStop = {
                        running = false
                        val spoke = SPEAK_SECONDS - left
                        left = SPEAK_SECONDS
                        // Stopping early still counts if most of the minute was spoken.
                        if (spoke >= 45) session?.let { vm.completeSpeaking(it.id, spoke); session = it.copy(completed = true, durationSeconds = spoke) }
                    },
                    onRate = { r -> s?.let { vm.rateSpeaking(it.id, if (r == it.rating) null else r); session = it.copy(rating = if (r == it.rating) null else r) } },
                )
            }

            if (ui.phase is SpeechPhase.Ready && !running) {
                Spacer(Modifier.height(16.dp))
                CoachCard(ui.coachAvailable, onOpenCoachSetup)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.wa_speak_hint), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, textAlign = TextAlign.Center)

            if (sessions.isNotEmpty() && ui.phase is SpeechPhase.Ready) {
                Spacer(Modifier.height(20.dp))
                ArcSectionTitle(stringResource(R.string.sc_progress_title))
                ArcCard(Modifier.fillMaxWidth(), onClick = onOpenProgress, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HabitBadge(Icons.Outlined.Insights, tone, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sc_view_progress), style = MaterialTheme.typography.titleSmall)
                            val avg = sessions.mapNotNull { it.overallScore }.takeIf { it.isNotEmpty() }?.average()
                            Text(
                                stringResource(R.string.sc_stats_card_summary, sessions.size, avg?.let { ScoreFormat.one(it) } ?: "—"),
                                style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted,
                            )
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Kairos.colors.muted)
                    }
                }
                val latest = sessions.first()
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onOpenReport(latest.id) }) {
                    Text(stringResource(R.string.sc_last_report) + " · " + (latest.overallScore?.let { ScoreFormat.one(it) + " / 10" } ?: "—"))
                }
            }

            val past = recent.filter { it.id != s?.id && it.date != today }
            if (past.isNotEmpty() && ui.phase is SpeechPhase.Ready) {
                Spacer(Modifier.height(12.dp))
                ArcSectionTitle(stringResource(R.string.wa_speak_recent))
                val fmt = rememberMediumDateFormatter()
                ArcCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    past.take(6).forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.topic, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Spacer(Modifier.size(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmt(r.date), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
                                Text(
                                    if (r.completed) "✓" + (r.rating?.let { " " + "★".repeat(it) } ?: "") else stringResource(R.string.wa_missed),
                                    style = MaterialTheme.typography.labelSmall, color = if (r.completed) tone.strong else Kairos.colors.muted,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** "7.2" in the person's locale digits are fine; one decimal. */
object ScoreFormat {
    fun one(v: Double): String = String.format(java.util.Locale.getDefault(), "%.1f", v)
}

@Composable
private fun TimerBlock(
    s: SpeakingSession?,
    tone: Tone,
    running: Boolean,
    left: Int,
    coachOn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRate: (Int) -> Unit,
) {
    val shown = if (running) left else if (s?.completed == true) 0 else SPEAK_SECONDS
    ProgressRing(
        fraction = if (running) 1f - left / SPEAK_SECONDS.toFloat() else if (s?.completed == true) 1f else 0f,
        color = tone.strong, size = 220.dp, stroke = 12.dp, track = tone.soft,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = tone.strong, modifier = Modifier.size(28.dp))
            Text(
                ArcFormat.clock(shown), fontSize = 48.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    when {
        running -> ArcButton(stringResource(R.string.wa_speak_stop), onStop, icon = Icons.Outlined.Stop, color = tone.soft, contentColor = tone.strong)
        s != null && s.completed -> {
            Text(stringResource(R.string.wa_speak_done), style = MaterialTheme.typography.titleMedium, color = tone.strong)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.wa_speak_rate), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
            Rating(s.rating, tone.strong, onRate)
            TextButton(onClick = onStart) {
                Icon(if (coachOn) Icons.Outlined.Mic else Icons.Outlined.Refresh, contentDescription = null)
                Text(stringResource(R.string.wa_speak_again), modifier = Modifier.padding(start = 6.dp))
            }
        }
        else -> ArcButton(stringResource(R.string.wa_speak_start), onStart, icon = Icons.Outlined.Mic, enabled = s != null, color = tone.strong)
    }
}

@Composable
private fun RecordingBlock(phase: SpeechPhase.Recording, tone: Tone, onStop: () -> Unit, onDiscard: () -> Unit) {
    val left = (phase.maxSeconds - phase.elapsedSeconds).coerceAtLeast(0)
    Text(stringResource(R.string.sc_speak_now), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tone.strong, letterSpacing = 2.sp)
    Spacer(Modifier.height(12.dp))
    ProgressRing(fraction = phase.elapsedSeconds / phase.maxSeconds.toFloat(), color = tone.strong, size = 220.dp, stroke = 12.dp, track = tone.soft) {
        Text(
            ArcFormat.clock(left), fontSize = 52.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(RecordRed))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.sc_recording), style = MaterialTheme.typography.labelLarge, color = RecordRed)
    }
    Spacer(Modifier.height(18.dp))
    ArcButton(stringResource(R.string.wa_speak_stop), onStop, icon = Icons.Outlined.Stop, color = tone.soft, contentColor = tone.strong)
    TextButton(onClick = onDiscard) { Text(stringResource(R.string.sc_discard), color = Kairos.colors.muted) }
}

private val RecordRed = Color(0xFFD64545)

@Composable
private fun AnalyzingBlock(step: AnalysisStep, tone: Tone, onCancel: () -> Unit) {
    Text(stringResource(R.string.sc_analyzing), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tone.strong, letterSpacing = 2.sp)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.sc_processing), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
    Spacer(Modifier.height(22.dp))
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(120.dp), color = tone.strong, trackColor = tone.soft, strokeWidth = 8.dp)
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = tone.strong, modifier = Modifier.size(34.dp))
    }
    Spacer(Modifier.height(16.dp))
    Text(
        when (step) {
            AnalysisStep.LoadingModel -> stringResource(R.string.sc_step_loading)
            is AnalysisStep.Listening -> stringResource(R.string.sc_step_listening, step.part, step.parts)
            AnalysisStep.WritingFeedback -> stringResource(R.string.sc_step_writing)
        },
        style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(20.dp))
    ArcCard(Modifier.fillMaxWidth(), color = tone.soft, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = tone.strong)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.sc_never_leaves), style = MaterialTheme.typography.bodyMedium)
        }
    }
    TextButton(onClick = onCancel) { Text(stringResource(R.string.sc_cancel), color = Kairos.colors.muted) }
}

@Composable
private fun FailedBlock(
    error: CoachError,
    canRetry: Boolean,
    tone: Tone,
    onRetry: () -> Unit,
    onRecordAgain: () -> Unit,
    onTimerOnly: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenCoachSetup: () -> Unit,
    showAppSettings: Boolean,
) {
    ArcCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = tone.strong)
            Spacer(Modifier.width(10.dp))
            Text(coachErrorText(error), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
    }
    Spacer(Modifier.height(16.dp))
    val modelProblem = error in setOf(
        CoachError.MODEL_MISSING, CoachError.MODEL_MALFORMED, CoachError.MODEL_INVALID_EXTENSION,
        CoachError.UNSUPPORTED_DEVICE, CoachError.INFERENCE_FAILED, CoachError.TIMEOUT, CoachError.LOW_MEMORY,
    )
    if (canRetry) {
        ArcButton(stringResource(R.string.sc_retry_analysis), onRetry, icon = Icons.Outlined.Refresh, color = tone.strong)
        Spacer(Modifier.height(8.dp))
    }
    if (error == CoachError.MIC_PERMISSION) {
        ArcButton(stringResource(R.string.wa_speak_start), onRecordAgain, icon = Icons.Outlined.Mic, color = tone.strong)
        if (showAppSettings) TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.sc_open_settings)) }
        TextButton(onClick = onTimerOnly) { Text(stringResource(R.string.sc_timer_instead)) }
    } else {
        ArcButton(
            stringResource(R.string.sc_record_again), onRecordAgain, icon = Icons.Outlined.Mic,
            color = if (canRetry) tone.soft else tone.strong, contentColor = if (canRetry) tone.strong else Arc.onPrimary,
        )
    }
    if (modelProblem) TextButton(onClick = onOpenCoachSetup) { Text(stringResource(R.string.sc_coach_settings)) }
}

@Composable
private fun CoachCard(available: Boolean, onSetup: () -> Unit) {
    val tone = Arc.tone(HabitKind.SPEAK)
    if (available) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = tone.strong, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sc_coach_ready), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted, textAlign = TextAlign.Center)
        }
    } else {
        ArcCard(Modifier.fillMaxWidth(), color = Arc.primarySoft, onClick = onSetup, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Arc.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sc_coach_off_title), style = MaterialTheme.typography.titleSmall, color = Arc.primary)
                    Text(stringResource(R.string.sc_coach_off_body), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sc_coach_setup), style = MaterialTheme.typography.labelLarge, color = Arc.primary)
            }
        }
    }
}

@Composable
private fun Rating(value: Int?, color: Color, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.Center) {
        (1..5).forEach { i ->
            val label = stringResource(R.string.wa_speak_rating_a11y, i)
            IconButton(onClick = { onRate(i) }, modifier = Modifier.semantics { contentDescription = label }) {
                Icon(if (value != null && i <= value) Icons.Outlined.Star else Icons.Outlined.StarOutline, contentDescription = null, tint = color)
            }
        }
    }
}
