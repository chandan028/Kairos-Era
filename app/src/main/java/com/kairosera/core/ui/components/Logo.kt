package com.kairosera.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The Kairos mark: a sun rising behind a mountain, with a path leading toward it, inside a ring.
 * Same geometry as the launcher icon (108-unit grid, mark spans 18..90), drawn in Compose so the
 * splash can animate the sunrise. [sunRise] 0 = sun below the horizon, 1 = fully risen.
 * [pathReveal] draws the path from the bottom up.
 */
@Composable
fun KairosLogo(
    modifier: Modifier = Modifier,
    sunRise: Float = 1f,
    pathReveal: Float = 1f,
    ringAlpha: Float = 1f,
    line: Color = Color(0xFFF5EFE3),
    sun: Color = Color(0xFFE8B04B),
    mountain: Color = Color(0xFF5E7AAE),
    mountainLight: Color = Color(0xFF8FA6CF),
    description: String? = null,
) {
    Canvas(modifier.then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        val unit = size.minDimension / 72f
        withTransform({
            translate((size.width - 72f * unit) / 2f, (size.height - 72f * unit) / 2f)
            scale(unit, unit, pivot = Offset.Zero)
            translate(-18f, -18f)
        }) {
            // Sun, clipped at the horizon so it rises from behind the mountains.
            val sunY = 51f + (1f - sunRise.coerceIn(0f, 1f)) * 26f
            clipRect(left = 20f, top = 20f, right = 88f, bottom = 62f) {
                drawCircle(sun, radius = 13f, center = Offset(54f, sunY))
            }
            val back = Path().apply { moveTo(26f, 76f); lineTo(43f, 55f); lineTo(51f, 64f); lineTo(63f, 49f); lineTo(82f, 76f); close() }
            drawPath(back, mountain)
            val light = Path().apply { moveTo(63f, 49f); lineTo(82f, 76f); lineTo(70f, 76f); lineTo(60f, 60f); close() }
            drawPath(light, mountainLight)
            // Path toward the sun, revealed from the bottom.
            val reveal = pathReveal.coerceIn(0f, 1f)
            if (reveal > 0f) {
                val trail = Path().apply {
                    moveTo(49f, 84f)
                    cubicTo(50f, 77f, 60f, 76f, 57f, 69f)
                    cubicTo(55f, 65f, 53f, 63f, 55f, 59f)
                }
                clipRect(left = 0f, top = 84f - 26f * reveal, right = 108f, bottom = 90f) {
                    drawPath(trail, line, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
                }
            }
            drawCircle(line.copy(alpha = line.alpha * ringAlpha), radius = 30f, center = Offset(54f, 54f), style = Stroke(width = 2.2f))
        }
    }
}
