package com.kairosera.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairosera.core.ui.theme.Kairos

/**
 * Round completion check with a small, satisfying moment: the ring fills green, bounces once,
 * and the tick draws itself. 48dp touch target around a 26dp circle.
 */
@Composable
fun TaskCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 26.dp,
) {
    val haptics = LocalHapticFeedback.current
    val green = Kairos.colors.success.strong
    val ring = Kairos.colors.muted.copy(alpha = 0.55f)
    val tickColor = Kairos.colors.card
    val fill by animateFloatAsState(if (checked) 1f else 0f, tween(220), label = "fill")
    val tick = remember { Animatable(if (checked) 1f else 0f) }
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked) {
            if (tick.value < 1f) {
                bounce.snapTo(0.8f)
                bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            }
            tick.animateTo(1f, tween(260, delayMillis = 60))
        } else {
            tick.snapTo(0f)
        }
    }
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 22.dp),
                onValueChange = {
                    if (it) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(it)
                },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size).scale(bounce.value)) {
            val r = this.size.minDimension / 2f
            val stroke = 2.dp.toPx()
            if (fill < 1f) drawCircle(ring, radius = r - stroke / 2f, style = Stroke(stroke))
            if (fill > 0f) drawCircle(green, radius = r * fill)
            if (tick.value > 0f) {
                val w = this.size.width
                val path = Path().apply {
                    moveTo(w * 0.28f, w * 0.52f)
                    lineTo(w * 0.44f, w * 0.67f)
                    lineTo(w * 0.73f, w * 0.36f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * tick.value, partial, true)
                drawPath(partial, tickColor, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (!enabled) drawCircle(ring.copy(alpha = 0.2f), radius = r, center = Offset(r, r))
        }
    }
}
