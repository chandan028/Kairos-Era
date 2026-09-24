package com.kairosera.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairosera.R
import com.kairosera.core.ui.components.KairosLogo
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.core.ui.theme.Stage

/** Type for the night stage: serif for the big moments, the system sans for everything else. */
internal object StageType {
    val display = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp, color = Stage.cream)
    val brand = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, fontSize = 30.sp, letterSpacing = 2.sp, color = Stage.cream)
    val overline = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp, color = Stage.muted)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = Stage.muted)
    val title = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Stage.cream)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Stage.muted)
    val quote = TextStyle(fontFamily = SerifFamily, fontSize = 19.sp, lineHeight = 28.sp, color = Stage.cream)
}

/** Back arrow (from step 2 on) and the step progress, in one quiet row. */
@Composable
fun KairosStepHeader(step: Int, total: Int, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Stage.cream) }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { KairosProgressIndicator(step, total) }
        Spacer(Modifier.size(48.dp))
    }
}

/** "01 of 05" over small rounded segments: gold now, muted gold done, muted blue ahead. */
@Composable
fun KairosProgressIndicator(step: Int, total: Int) {
    val a11y = stringResource(R.string.onb_step_a11y, step, total)
    Column(Modifier.clearAndSetSemantics { contentDescription = a11y }, horizontalAlignment = Alignment.Start) {
        Text(stringResource(R.string.onb_step_of, "%02d".format(step), "%02d".format(total)), style = StageType.overline.copy(fontSize = 10.sp, letterSpacing = 0.6.sp))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..total) {
                val color by animateColorAsState(
                    when {
                        i == step -> Stage.gold
                        i < step -> Stage.goldMuted
                        else -> Stage.blueMuted
                    },
                    tween(400),
                    label = "segment",
                )
                Box(Modifier.width(28.dp).height(4.dp).clip(CircleShape).background(color))
            }
        }
    }
}

/** The one gold action per screen. Presses sink slightly; nothing bounces. */
@Composable
fun KairosPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, arrow: Boolean = true, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "press")
    Box(
        modifier.fillMaxWidth().heightIn(min = 56.dp).scale(scale).clip(RoundedCornerShape(28.dp))
            .background(if (enabled) Stage.goldBrush else androidx.compose.ui.graphics.SolidColor(Stage.goldMuted))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Stage.onGold), textAlign = TextAlign.Center)
            if (arrow) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Stage.onGold, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Quiet text action under the primary one. */
@Composable
fun KairosSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, underline: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = StageType.body.copy(fontSize = 15.sp, color = Stage.cream.copy(alpha = 0.85f), textDecoration = if (underline) TextDecoration.Underline else null))
    }
}

/** A selectable focus area. Selection shows as a check and a gold border, never color alone. */
@Composable
fun KairosFocusCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(if (selected) Stage.gold else Stage.line, tween(220), label = "border")
    val fill by animateColorAsState(if (selected) tint.copy(alpha = 0.20f) else tint.copy(alpha = 0.08f), tween(220), label = "fill")
    val scale by animateFloatAsState(if (selected) 1f else 0.985f, tween(220), label = "scale")
    val state = stringResource(if (selected) R.string.onb_selected else R.string.onb_not_selected)
    Box(
        modifier.scale(scale).clip(RoundedCornerShape(20.dp)).background(Stage.night2).background(fill)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), RoundedCornerShape(20.dp))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = state }
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, style = StageType.title)
            Spacer(Modifier.height(4.dp))
            Text(description, style = StageType.small)
        }
        Box(Modifier.align(Alignment.TopEnd).size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Stage.gold), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Stage.onGold, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(Modifier.size(22.dp).border(1.5.dp, Stage.muted.copy(alpha = 0.6f), CircleShape))
            }
        }
    }
}

/** A small icon + label for the privacy strip. */
@Composable
fun KairosPrivacyItem(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
        Icon(icon, contentDescription = null, tint = Stage.cream.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = StageType.small.copy(fontSize = 12.sp, color = Stage.cream.copy(alpha = 0.85f)))
    }
}

/** A feature tile on the welcome screen. */
@Composable
fun KairosFeatureTile(icon: ImageVector, tint: Color, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Stage.night2).border(1.dp, Stage.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = StageType.title.copy(fontSize = 14.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = StageType.small.copy(fontSize = 11.sp, lineHeight = 14.sp), textAlign = TextAlign.Center)
    }
}

/** Today's thought: a gold quote mark, the line in serif, a short gold rule. */
@Composable
fun KairosQuoteCard(quote: String, modifier: Modifier = Modifier) {
    StageCard(modifier) {
        Row {
            Text("“", style = TextStyle(fontFamily = SerifFamily, fontSize = 44.sp, lineHeight = 44.sp, color = Stage.gold))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(quote, style = StageType.quote)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(28.dp).height(2.dp).clip(CircleShape).background(Stage.gold.copy(alpha = 0.7f)))
            }
        }
    }
}

/** What a Kairos Era reminder looks like in the notification shade. Illustrative, not interactive. */
@Composable
fun KairosNotificationPreview(time: String, title: String, modifier: Modifier = Modifier) {
    val a11y = stringResource(R.string.onb_rem_preview_a11y, title, time)
    Box(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = a11y }, contentAlignment = Alignment.TopCenter) {
        // A phone's top edge behind the card, and a few gold rays: the moment a reminder arrives.
        Canvas(Modifier.fillMaxWidth(0.72f).height(170.dp)) {
            val r = 36.dp.toPx()
            drawRoundRect(Stage.line, topLeft = androidx.compose.ui.geometry.Offset(0f, 18.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, size.height - 18.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(width = 3.dp.toPx()))
            drawRoundRect(Stage.line, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.4f, 28.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width * 0.2f, 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            val o = androidx.compose.ui.geometry.Offset(size.width + 8.dp.toPx(), 12.dp.toPx())
            listOf(-0.9f to 14f, -0.35f to 18f, 0.2f to 14f).forEach { (a, len) ->
                val dx = kotlin.math.cos(a); val dy = kotlin.math.sin(a)
                drawLine(Stage.gold, o + androidx.compose.ui.geometry.Offset(dx * 6.dp.toPx(), dy * 6.dp.toPx()),
                    o + androidx.compose.ui.geometry.Offset(dx * (6f + len).dp.toPx(), dy * (6f + len).dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Surface(
            Modifier.padding(top = 52.dp).fillMaxWidth(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = Stage.sheet,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Stage.night0), contentAlignment = Alignment.Center) {
                        KairosLogo(Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.app_name), style = TextStyle(fontSize = 13.sp, color = Stage.sheetMuted), modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.onb_rem_now), style = TextStyle(fontSize = 12.sp, color = Stage.sheetMuted))
                }
                Spacer(Modifier.height(8.dp))
                Text("$time · $title", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Stage.sheetInk))
                Text(stringResource(R.string.onb_rem_body), style = TextStyle(fontSize = 14.sp, color = Stage.sheetMuted))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PreviewAction(Icons.Filled.Check, stringResource(R.string.onb_rem_done), Stage.done, Modifier.weight(1f))
                    PreviewAction(Icons.Outlined.Schedule, stringResource(R.string.onb_rem_snooze), Stage.snooze, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PreviewAction(icon: ImageVector, label: String, color: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(color).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White))
    }
}

/** An icon beside a sentence, for short explanations. */
@Composable
fun StageInfoRow(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Stage.cream.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = StageType.body.copy(fontSize = 15.sp, lineHeight = 21.sp, color = Stage.cream.copy(alpha = 0.85f)))
    }
}

/** The dark rounded card used across the flow. */
@Composable
fun StageCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Stage.night2.copy(alpha = 0.92f))
            .border(1.dp, Stage.line, RoundedCornerShape(20.dp)).padding(18.dp),
    ) { content() }
}
