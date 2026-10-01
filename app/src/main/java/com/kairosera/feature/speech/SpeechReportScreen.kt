package com.kairosera.feature.speech

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.ai.CoachError
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.core.ui.theme.Tone
import com.kairosera.data.speech.SpeechSession
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.feature.winterarc.Arc
import com.kairosera.feature.winterarc.ArcButton
import com.kairosera.feature.winterarc.ArcCard
import com.kairosera.feature.winterarc.ArcTabs
import com.kairosera.feature.winterarc.HabitBadge
import com.kairosera.feature.winterarc.ProgressRing
import com.kairosera.feature.winterarc.ScoreFormat
import com.kairosera.speech.Pace
import com.kairosera.speech.SpeechMetrics
import com.kairosera.ui.kairosViewModel

/** Readable text for every coach error. */
@Composable
fun coachErrorText(e: CoachError): String = stringResource(
    when (e) {
        CoachError.MIC_PERMISSION -> R.string.sc_err_mic_permission
        CoachError.MIC_UNAVAILABLE -> R.string.sc_err_mic_unavailable
        CoachError.RECORDING_FAILED -> R.string.sc_err_recording_failed
        CoachError.INTERRUPTED -> R.string.sc_err_interrupted
        CoachError.TOO_SHORT -> R.string.sc_err_too_short
        CoachError.NO_SPEECH -> R.string.sc_err_no_speech
        CoachError.MODEL_MISSING -> R.string.sc_err_model_missing
        CoachError.MODEL_INVALID_EXTENSION -> R.string.sc_err_model_extension
        CoachError.MODEL_MALFORMED -> R.string.sc_err_model_malformed
        CoachError.LOW_STORAGE -> R.string.sc_err_low_storage
        CoachError.LOW_MEMORY -> R.string.sc_err_low_memory
        CoachError.UNSUPPORTED_DEVICE -> R.string.sc_err_unsupported
        CoachError.INFERENCE_FAILED -> R.string.sc_err_inference
        CoachError.BAD_RESPONSE -> R.string.sc_err_bad_response
        CoachError.TIMEOUT -> R.string.sc_err_timeout
        CoachError.IMPORT_FAILED -> R.string.sc_err_import
    },
)

/** Calm, distinct tones for the five scores, from the Winter Arc habit family. */
private data class Category(val label: Int, val icon: ImageVector, val kind: HabitKind)

private val CATEGORIES = listOf(
    Category(R.string.sc_clarity, Icons.Outlined.ChatBubbleOutline, HabitKind.WATER),
    Category(R.string.sc_structure, Icons.AutoMirrored.Outlined.FormatListBulleted, HabitKind.READING),
    Category(R.string.sc_vocabulary, Icons.AutoMirrored.Outlined.MenuBook, HabitKind.STEPS),
    Category(R.string.sc_grammar, Icons.Outlined.School, HabitKind.SPEAK),
    Category(R.string.sc_conciseness, Icons.Outlined.TrackChanges, HabitKind.ZERO_SUGAR),
)

private fun SpeechSession.scores() = listOf(clarityScore, structureScore, vocabularyScore, grammarScore, concisenessScore)

/**
 * The Speech Report from the wireframe: Overview (score ring, five scores, measured stats),
 * What You Did Well, How to Improve, and Next. Everything shown comes from the saved session.
 */
@Composable
fun SpeechReportScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onPractiseAgain: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val vm = kairosViewModel("speech_reports") { SpeechReportViewModel(it.speechSessions) }
    val loaded by remember(sessionId) { vm.session(sessionId) }.collectAsStateWithLifecycle(initialValue = LoadingMarker)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fmt = rememberMediumDateFormatter()

    Column(Modifier.fillMaxSize()) {
        val s = loaded
        ScreenHeader(
            title = stringResource(R.string.sc_report_title),
            subtitle = (s as? SpeechSession)?.let { fmt(it.date) },
            onBack = onBack,
            actions = {
                if (s is SpeechSession) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.sc_delete_report))
                    }
                }
            },
        )
        when {
            s === LoadingMarker -> { LoadingState(Modifier.fillMaxWidth().height(240.dp)); return@Column }
            s !is SpeechSession -> {
                Text(stringResource(R.string.sc_not_found), modifier = Modifier.padding(24.dp), color = Kairos.colors.muted)
                return@Column
            }
        }
        val session = s as SpeechSession
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally).widthIn(max = 640.dp),
        ) {
            ArcTabs(
                listOf(
                    stringResource(R.string.sc_tab_overview), stringResource(R.string.sc_tab_well),
                    stringResource(R.string.sc_tab_improve), stringResource(R.string.sc_tab_next),
                ),
                tab, { tab = it },
            )
            Spacer(Modifier.height(14.dp))
            when (tab) {
                0 -> Overview(session, onDetails = { tab = 2 }, onDone = onBack)
                1 -> WellTab(session)
                2 -> ImproveTab(session)
                else -> NextTab(session, onPractiseAgain, onOpenProgress)
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.sc_delete_report_q)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(sessionId); onBack() }) { Text(stringResource(R.string.sc_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.sc_cancel)) } },
        )
    }
}

/** Distinguishes "still loading" from "deleted" (null) in the session flow. */
private object LoadingMarker

@Composable
private fun Overview(s: SpeechSession, onDetails: () -> Unit, onDone: () -> Unit) {
    val ringTone = Arc.tone(HabitKind.WATER)
    ArcCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        val score = s.overallScore
        val a11y = stringResource(R.string.sc_score_a11y, score?.let { ScoreFormat.one(it) } ?: stringResource(R.string.sc_na))
        Box(Modifier.fillMaxWidth().semantics { contentDescription = a11y }, contentAlignment = Alignment.Center) {
            ProgressRing(fraction = ((score ?: 0.0) / 10.0).toFloat(), color = ringTone.strong, size = 180.dp, stroke = 14.dp, track = ringTone.soft) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(score?.let { ScoreFormat.one(it) } ?: "—", fontFamily = SerifFamily, fontSize = 52.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.sc_out_of_ten), style = MaterialTheme.typography.titleMedium, color = Kairos.colors.muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(
                when {
                    score == null -> R.string.sc_headline_mid
                    score >= 8.0 -> R.string.sc_headline_high
                    score >= 6.0 -> R.string.sc_headline_mid
                    else -> R.string.sc_headline_low
                },
            ),
            fontFamily = SerifFamily, fontSize = 20.sp, lineHeight = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (s.summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(s.summary, style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.sc_detailed), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        TextButton(onClick = onDetails) { Text(stringResource(R.string.sc_view_details) + " →") }
    }
    val scores = s.scores()
    CATEGORIES.chunked(2).forEachIndexed { row, pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEachIndexed { i, c -> ScoreCard(c, scores[row * 2 + i], Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
    if (scores.any { it == null }) {
        Text(stringResource(R.string.sc_na_hint), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        Spacer(Modifier.height(10.dp))
    }
    StatsCard(s)
    Spacer(Modifier.height(14.dp))
    Transcript(s.transcript)
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.sc_ran_on, s.backend), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
    Spacer(Modifier.height(14.dp))
    ArcButton(stringResource(R.string.sc_done), onDone)
}

@Composable
private fun ScoreCard(c: Category, score: Int?, modifier: Modifier) {
    val tone = Arc.tone(c.kind)
    val label = stringResource(c.label)
    val na = stringResource(R.string.sc_na)
    ArcCard(modifier.semantics(mergeDescendants = true) {}, color = tone.soft, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Kairos.colors.card.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Icon(c.icon, contentDescription = null, tint = tone.strong, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Kairos.colors.muted, maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(score?.toString() ?: na, style = MaterialTheme.typography.titleLarge, color = tone.strong, fontWeight = FontWeight.SemiBold)
                    if (score != null) Text(" / 10", style = MaterialTheme.typography.bodySmall, color = tone.strong, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun StatsCard(s: SpeechSession) {
    var info by remember { mutableStateOf(false) }
    ArcCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Insights, contentDescription = null, tint = Arc.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sc_stats), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = { info = !info }) { Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.sc_fillers_info), tint = Kairos.colors.muted) }
        }
        Row(Modifier.fillMaxWidth()) {
            Stat(Icons.Outlined.Timer, stringResource(R.string.sc_seconds, s.durationSeconds), stringResource(R.string.sc_duration), Modifier.weight(1f))
            val pace = SpeechMetrics.pace(s.wordsPerMinute)
            Stat(
                Icons.Outlined.GraphicEq,
                when (pace) {
                    Pace.SLOW -> stringResource(R.string.sc_pace_slow)
                    Pace.FAST -> stringResource(R.string.sc_pace_fast)
                    Pace.COMFORTABLE -> stringResource(R.string.sc_pace_normal)
                    null -> stringResource(R.string.sc_na)
                },
                s.wordsPerMinute?.let { stringResource(R.string.sc_wpm, it) } ?: stringResource(R.string.sc_pace),
                Modifier.weight(1f),
            )
            Stat(Icons.Outlined.ChatBubbleOutline, s.fillerSounds?.toString() ?: stringResource(R.string.sc_na), stringResource(R.string.sc_fillers), Modifier.weight(1f))
        }
        s.longPauses?.let {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.sc_pauses, it), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (info) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sc_fillers_info), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Arc.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Transcript(text: String) {
    if (text.isBlank()) return
    var open by rememberSaveable { mutableStateOf(false) }
    ArcCard(Modifier.fillMaxWidth(), onClick = { open = !open }, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = Kairos.colors.muted)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sc_transcript), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(stringResource(if (open) R.string.sc_hide else R.string.sc_show), style = MaterialTheme.typography.labelLarge, color = Arc.primary)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TabHeader(icon: ImageVector, title: String, subtitle: String, tone: Tone) {
    ArcCard(Modifier.fillMaxWidth(), color = tone.soft, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tone.strong, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontFamily = SerifFamily, fontSize = 24.sp, lineHeight = 30.sp, modifier = Modifier.semantics { heading() })
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun WellTab(s: SpeechSession) {
    val tone = Arc.tone(HabitKind.STEPS)
    TabHeader(Icons.Outlined.Spa, stringResource(R.string.sc_well_title), stringResource(R.string.sc_well_sub), tone)
    if (s.strengths.isEmpty()) {
        Text(stringResource(R.string.sc_none_listed), color = Kairos.colors.muted)
        return
    }
    val icons = listOf(Icons.Outlined.ChatBubbleOutline, Icons.AutoMirrored.Outlined.MenuBook, Icons.Outlined.TrackChanges, Icons.Outlined.EmojiEvents)
    val kinds = listOf(HabitKind.WATER, HabitKind.STEPS, HabitKind.READING, HabitKind.SPEAK)
    s.strengths.forEachIndexed { i, text ->
        ArcCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitBadge(icons[i % icons.size], Arc.tone(kinds[i % kinds.size]), size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImproveTab(s: SpeechSession) {
    val tone = Arc.tone(HabitKind.ZERO_SUGAR)
    TabHeader(Icons.Outlined.TrendingUp, stringResource(R.string.sc_improve_title), stringResource(R.string.sc_improve_sub), tone)
    if (s.improvements.isEmpty()) Text(stringResource(R.string.sc_none_listed), color = Kairos.colors.muted)
    val kinds = listOf(HabitKind.WATER, HabitKind.READING, HabitKind.SPEAK, HabitKind.STEPS)
    s.improvements.forEachIndexed { i, text ->
        val t = Arc.tone(kinds[i % kinds.size])
        ArcCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(t.soft), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = t.strong, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    val fillers = s.fillerWords.orEmpty()
    if (fillers.isNotEmpty()) {
        ArcCard(Modifier.fillMaxWidth(), color = Arc.tone(HabitKind.READING).soft, padding = 14.dp) {
            Text(stringResource(R.string.sc_fillers_heard), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fillers.distinct().take(8).forEach { w -> Chip(w, tone) }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sc_try_pause), style = MaterialTheme.typography.bodyMedium, color = Arc.tone(HabitKind.STEPS).strong)
        }
        Spacer(Modifier.height(10.dp))
    }
    if (s.nextExercise.isNotBlank()) {
        val warm = Arc.tone(HabitKind.SPEAK)
        ArcCard(Modifier.fillMaxWidth(), color = warm.soft, padding = 14.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = warm.strong)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.sc_try_next), style = MaterialTheme.typography.titleSmall, color = warm.strong)
                    Text(s.nextExercise, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, tone: Tone) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(tone.soft).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = tone.strong)
    }
}

@Composable
private fun NextTab(s: SpeechSession, onPractiseAgain: () -> Unit, onOpenProgress: () -> Unit) {
    val warm = Arc.tone(HabitKind.SPEAK)
    ArcCard(Modifier.fillMaxWidth(), color = warm.soft, padding = 20.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = warm.strong, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sc_next_title), fontFamily = SerifFamily, fontSize = 22.sp, lineHeight = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.sc_next_sub), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(12.dp))
    if (s.nextExercise.isNotBlank()) {
        ArcCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitBadge(Icons.Outlined.TrackChanges, Arc.tone(HabitKind.READING), size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.sc_next_exercise), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                    Text(s.nextExercise, fontFamily = SerifFamily, fontSize = 19.sp, lineHeight = 25.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    ArcCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text(stringResource(R.string.sc_rules), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        listOf(
            R.string.sc_rule1 to R.string.sc_rule1_sub,
            R.string.sc_rule2 to R.string.sc_rule2_sub,
            R.string.sc_rule3 to R.string.sc_rule3_sub,
        ).forEachIndexed { i, (title, sub) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Arc.primarySoft), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = Arc.primary, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    ArcButton(stringResource(R.string.sc_practise_again), onPractiseAgain, icon = Icons.Outlined.Mic)
    Spacer(Modifier.height(10.dp))
    ArcButton(stringResource(R.string.sc_view_progress), onOpenProgress, icon = Icons.Outlined.Insights, color = Kairos.colors.card, contentColor = Arc.primary)
}
