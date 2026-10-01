package com.kairosera.feature.winterarc

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairosera.R
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.Tone

/** A soft-filled, rounded Winter Arc card. Light hairline, no heavy shadow. */
@Composable
fun ArcCard(
    modifier: Modifier = Modifier,
    color: Color = Kairos.colors.card,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val border = BorderStroke(1.dp, Kairos.colors.line)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border, shadowElevation = 1.dp) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border, shadowElevation = 1.dp) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

/** Circular progress ring with content in the middle. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 6.dp,
    track: Color = Kairos.colors.track,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Round habit icon on its soft fill. */
@Composable
fun HabitBadge(icon: ImageVector, tone: Tone, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(tone.soft), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tone.strong, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The one-tap check: an empty ring that fills with the habit color. 48dp touch target.
 * [label] names the habit for screen readers.
 */
@Composable
fun CheckDot(checked: Boolean, tone: Tone, label: String, onToggle: (() -> Unit)?, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val state = stringResource(if (checked) R.string.wa_done else R.string.wa_not_yet)
    val a11y = stringResource(R.string.wa_a11y_toggle, label, state)
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .semantics {
                contentDescription = a11y
                stateDescription = state
                role = Role.Checkbox
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (checked) tone.strong else Color.Transparent)
                .border(2.dp, if (checked) tone.strong else tone.strong.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = if (Arc.isDark) Color(0xFF0D1729) else Color.White, modifier = Modifier.size(size * 0.62f))
        }
    }
}

/** Rounded bar in a habit color. */
@Composable
fun ArcBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, track: Color = Kairos.colors.track) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        if (animated > 0f) Box(Modifier.fillMaxWidth(animated).height(height).clip(CircleShape).background(color))
    }
}

/** Segmented tabs like the reference ("Today | This Week | All Topics"). */
@Composable
fun ArcTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kairos.colors.track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Arc.primary else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Arc.onPrimary else Kairos.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/** Small colored square for calendars and consistency grids. */
@Composable
fun DaySquare(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp, ring: Color? = null) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(color)
            .then(if (ring != null) Modifier.border(2.dp, ring, RoundedCornerShape(size / 4)) else Modifier),
    )
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DaySquare(color, size = 10.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Kairos.colors.muted)
    }
}

/** Big number + caption, used by the 90-day and stats summaries. */
@Composable
fun ArcStat(value: String, label: String, tone: Tone, modifier: Modifier = Modifier) {
    ArcCard(modifier, color = tone.soft, padding = 14.dp) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tone.strong, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, maxLines = 2)
    }
}

/** A plain section title in Winter Arc screens. */
@Composable
fun ArcSectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = Kairos.colors.muted)
    }
}

/** Primary Winter Arc button (blue, full width). */
@Composable
fun ArcButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, color: Color = Arc.primary, contentColor: Color = Arc.onPrimary) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) color else Kairos.colors.track,
        contentColor = if (enabled) contentColor else Kairos.colors.muted,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

/** Pill-shaped quick action ("+500 ml"). */
@Composable
fun QuickChip(text: String, tone: Tone, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (filled) tone.strong else tone.soft,
        contentColor = if (filled) (if (Arc.isDark) Color(0xFF0D1729) else Color.White) else tone.strong,
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
