package com.kairosera.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.Tone
import kotlinx.coroutines.launch
import kotlin.math.abs

/** An action revealed by swiping a row to the left. */
class SwipeAction(val label: String, val icon: ImageVector, val tone: Tone, val onClick: () -> Unit)

/**
 * Row with two gestures:
 *  - swipe right past the threshold → [onSwipeRight] (e.g. complete), shown as a green check;
 *  - swipe left → reveals [leftActions] (e.g. reschedule, delete) as buttons behind the row.
 * Every gesture also exists as an accessibility action, so nothing depends on swiping.
 */
@Composable
fun SwipeRow(
    rightLabel: String,
    rightIcon: ImageVector,
    rightTone: Tone,
    onSwipeRight: () -> Unit,
    leftActions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val actionWidth = 76.dp
    val revealPx = with(density) { (actionWidth * leftActions.size).toPx() }
    val completePx = with(density) { 96.dp.toPx() }
    val maxRightPx = with(density) { 140.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val settle = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)

    Box(
        modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(rightLabel) { onSwipeRight(); true }) +
                leftActions.map { a -> CustomAccessibilityAction(a.label) { a.onClick(); true } }
        },
    ) {
        // Behind the row: completion on the left edge, actions on the right edge.
        Row(Modifier.matchParentSize().clip(MaterialTheme.shapes.medium), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).fillMaxHeight().background(if (offset.value > 0f) rightTone.soft else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (offset.value > 0f) {
                    val reached = offset.value >= completePx
                    Icon(
                        rightIcon,
                        contentDescription = null,
                        tint = rightTone.strong,
                        modifier = Modifier.padding(start = 24.dp).size(if (reached) 28.dp else 22.dp),
                    )
                }
            }
            if (offset.value < 0f) {
                Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.End) {
                    leftActions.forEach { a ->
                        Surface(
                            onClick = { scope.launch { offset.animateTo(0f, settle) }; a.onClick() },
                            color = a.tone.soft,
                            contentColor = a.tone.strong,
                            modifier = Modifier.width(actionWidth).fillMaxHeight(),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(a.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(a.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .pointerInput(leftActions.size) {
                    var crossed = false
                    detectHorizontalDragGestures(
                        onDragStart = { crossed = false },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            scope.launch {
                                val next = (offset.value + delta).coerceIn(-revealPx * 1.15f, maxRightPx)
                                offset.snapTo(next)
                                val over = next >= completePx
                                if (over != crossed) {
                                    crossed = over
                                    if (over) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val v = offset.value
                                when {
                                    v >= completePx -> { offset.animateTo(0f, settle); onSwipeRight() }
                                    v < 0f && abs(v) > revealPx / 2f -> offset.animateTo(-revealPx, settle)
                                    else -> offset.animateTo(0f, settle)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, settle) } },
                    )
                },
        ) {
            content()
        }
    }
}

/** Semantic shortcuts for the common row actions. */
object SwipeTones {
    val complete: Tone @Composable get() = Kairos.colors.success
    val reschedule: Tone @Composable get() = Kairos.colors.motivation
    val delete: Tone @Composable get() = Kairos.colors.activity
}
