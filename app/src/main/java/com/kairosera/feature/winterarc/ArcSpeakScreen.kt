package com.kairosera.feature.winterarc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.SpeakingSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPEAK_SECONDS = 60

/**
 * One random topic a day and a one-minute countdown. Shuffle is allowed until the first
 * attempt; after that the topic is today's, with an optional 1 to 5 rating.
 */
@Composable
fun ArcSpeakScreen(vm: ArcViewModel, onBack: (() -> Unit)?) {
    val tone = Arc.tone(HabitKind.SPEAK)
    val today by vm.today.collectAsStateWithLifecycle()
    val recent by remember { vm.recentSpeaking() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<SpeakingSession?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(false) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var left by rememberSaveable { mutableIntStateOf(SPEAK_SECONDS) }

    LaunchedEffect(today) { session = vm.topicFor(today); loaded = true }
    // Keep the shown session in step with what is saved (completion, rating).
    LaunchedEffect(recent) {
        val id = session?.id ?: return@LaunchedEffect
        recent.firstOrNull { it.id == id }?.let { session = it }
    }
    KeepScreenOn(running)
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

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.wa_speak_title), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally).widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val s = session
            ArcCard(Modifier.fillMaxWidth(), color = tone.soft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.wa_speak_topic), style = MaterialTheme.typography.labelLarge, color = tone.strong, modifier = Modifier.weight(1f))
                    if (s != null && !s.completed && !running) {
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
            Spacer(Modifier.height(28.dp))
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
                running -> ArcButton(stringResource(R.string.wa_speak_stop), {
                    running = false
                    val spoke = SPEAK_SECONDS - left
                    left = SPEAK_SECONDS
                    // Stopping early still counts if most of the minute was spoken.
                    if (spoke >= 45) session?.let { vm.completeSpeaking(it.id, spoke); session = it.copy(completed = true, durationSeconds = spoke) }
                }, icon = Icons.Outlined.Stop, color = tone.soft, contentColor = tone.strong)
                s != null && s.completed -> {
                    Text(stringResource(R.string.wa_speak_done), style = MaterialTheme.typography.titleMedium, color = tone.strong)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.wa_speak_rate), style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
                    Rating(s.rating, tone.strong) { r -> vm.rateSpeaking(s.id, if (r == s.rating) null else r); session = s.copy(rating = if (r == s.rating) null else r) }
                    TextButton(onClick = { startedAt = System.currentTimeMillis(); left = SPEAK_SECONDS; running = true }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Text(stringResource(R.string.wa_speak_again), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                else -> ArcButton(
                    stringResource(R.string.wa_speak_start),
                    { startedAt = System.currentTimeMillis(); left = SPEAK_SECONDS; running = true },
                    icon = Icons.Outlined.Mic, enabled = s != null, color = tone.strong,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.wa_speak_hint), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, textAlign = TextAlign.Center)
            val past = recent.filter { it.id != s?.id && it.date != today }
            if (past.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
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

@Composable
private fun Rating(value: Int?, color: androidx.compose.ui.graphics.Color, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.Center) {
        (1..5).forEach { i ->
            val label = stringResource(R.string.wa_speak_rating_a11y, i)
            IconButton(onClick = { onRate(i) }, modifier = Modifier.semantics { contentDescription = label }) {
                Icon(if (value != null && i <= value) Icons.Outlined.Star else Icons.Outlined.StarOutline, contentDescription = null, tint = color)
            }
        }
    }
}
