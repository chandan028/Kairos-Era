package com.kairosera.feature.speech

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.LoadingState
import com.kairosera.core.ui.components.rememberMediumDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.data.speech.SpeechSession
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.feature.more.SubScreen
import com.kairosera.feature.winterarc.Arc
import com.kairosera.feature.winterarc.ArcCard
import com.kairosera.feature.winterarc.ScoreFormat
import com.kairosera.ui.kairosViewModel

/** Summary numbers from saved reports only. Sessions without a score are left out. */
data class SpeechProgress(val count: Int, val average: Double?, val best: Double?, val sinceFirst: Double?, val chart: List<Double>) {
    companion object {
        fun of(newestFirst: List<SpeechSession>): SpeechProgress {
            val scored = newestFirst.mapNotNull { it.overallScore }.reversed() // oldest → newest
            fun r(v: Double) = Math.round(v * 10) / 10.0
            return SpeechProgress(
                count = newestFirst.size,
                average = scored.takeIf { it.isNotEmpty() }?.average()?.let(::r),
                best = scored.maxOrNull(),
                sinceFirst = if (scored.size >= 2) r(scored.last() - scored.first()) else null,
                chart = scored.takeLast(30),
            )
        }
    }
}

@Composable
fun SpeechProgressScreen(onBack: () -> Unit, onOpenReport: (Long) -> Unit) {
    val vm = kairosViewModel("speech_reports") { SpeechReportViewModel(it.speechSessions) }
    val sessions by vm.all.collectAsStateWithLifecycle(initialValue = null)
    val fmt = rememberMediumDateFormatter()
    SubScreen(stringResource(R.string.sc_progress_title), onBack) {
        val list = sessions
        if (list == null) { LoadingState(); return@SubScreen }
        if (list.isEmpty()) {
            Text(stringResource(R.string.sc_progress_empty), color = Kairos.colors.muted, modifier = Modifier.padding(24.dp))
            return@SubScreen
        }
        val p = SpeechProgress.of(list)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Num(stringResource(R.string.sc_avg), p.average?.let(ScoreFormat::one) ?: "—", Modifier.weight(1f))
                    Num(stringResource(R.string.sc_best), p.best?.let(ScoreFormat::one) ?: "—", Modifier.weight(1f))
                    Num(stringResource(R.string.sc_speeches), p.count.toString(), Modifier.weight(1f))
                    Num(
                        stringResource(R.string.sc_since_first),
                        p.sinceFirst?.let { (if (it > 0) "+" else "") + ScoreFormat.one(it) } ?: "—",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (p.chart.size >= 2) {
                    ScoreChart(p.chart)
                    Spacer(Modifier.height(16.dp))
                }
                Text(stringResource(R.string.sc_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
            }
            items(list, key = { it.id }) { s ->
                ArcCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { onOpenReport(s.id) }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt(s.date), style = MaterialTheme.typography.labelMedium, color = Kairos.colors.muted)
                            Text(s.topic.ifBlank { stringResource(R.string.sc_title) }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            s.overallScore?.let(ScoreFormat::one) ?: stringResource(R.string.sc_na),
                            fontFamily = SerifFamily, fontSize = 24.sp, color = Arc.tone(HabitKind.WATER).strong,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Num(label: String, value: String, modifier: Modifier) {
    ArcCard(modifier.semantics(mergeDescendants = true) {}, padding = 10.dp) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted, maxLines = 2)
    }
}

/** A plain line of real scores on a 0–10 scale, oldest on the left. */
@Composable
private fun ScoreChart(scores: List<Double>) {
    val tone = Arc.tone(HabitKind.WATER)
    val grid = Kairos.colors.line
    val a11y = stringResource(R.string.sc_chart_a11y, scores.size, ScoreFormat.one(scores.last()))
    ArcCard(Modifier.fillMaxWidth().semantics { contentDescription = a11y }, padding = 16.dp) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val w = size.width
            val h = size.height
            listOf(0.0, 5.0, 10.0).forEach { v ->
                val y = (h - v / 10.0 * h).toFloat()
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
            val step = w / (scores.size - 1)
            val pts = scores.mapIndexed { i, s -> Offset(i * step, (h - s.coerceIn(0.0, 10.0) / 10.0 * h).toFloat()) }
            val path = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(path, tone.strong, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            pts.forEach { drawCircle(tone.strong, 4.dp.toPx(), it) }
        }
    }
}
