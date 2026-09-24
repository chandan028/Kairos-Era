package com.kairosera.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.kairosera.core.ui.theme.Stage

/**
 * A quiet sunrise over layered mountains: the brand's sun, mountain and path, as scenery.
 * Pure vector, so it is sharp on every screen and costs no image assets. [glow] 0..1 lets the
 * caller breathe the sun's light slowly; [horizon] is where the land meets the sky (fraction of
 * height). The bottom fades into the page so content can sit on top.
 */
@Composable
fun SunriseScene(
    modifier: Modifier = Modifier,
    horizon: Float = 0.66f,
    sunX: Float = 0.5f,
    glow: Float = 1f,
    trees: Boolean = true,
    path: Boolean = false,
    fadeTop: Boolean = true,
) {
    // Clipped: the sun's glow must never spill past the scene onto the page.
    Canvas(modifier.clipToBounds()) {
        val w = size.width
        val h = size.height
        val hy = h * horizon

        // Sky: night overhead warming toward the horizon.
        drawRect(
            Brush.verticalGradient(
                0f to Stage.night0,
                0.45f * horizon to Color(0xFF17223F),
                0.8f * horizon to Color(0xFF3A3552),
                horizon to Color(0xFF9A6A4E),
                (horizon + 0.02f).coerceAtMost(1f) to Color(0xFF3A3552),
                1f to Stage.night0,
                startY = 0f, endY = h,
            ),
        )
        // Sun glow and disk, the disk clipped by the horizon so it reads as rising.
        val sun = Offset(w * sunX, hy)
        val g = glow.coerceIn(0f, 1f)
        drawCircle(
            Brush.radialGradient(listOf(Stage.gold.copy(alpha = 0.42f * g), Stage.goldDeep.copy(alpha = 0.12f * g), Color.Transparent), center = sun, radius = w * 0.55f),
            radius = w * 0.55f, center = sun,
        )
        clipRect(0f, 0f, w, hy) {
            drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE3A3), Stage.gold), startY = hy - w * 0.07f, endY = hy), radius = w * 0.065f, center = sun)
        }

        ridge(FAR, hy, w, h, Color(0xFF36456A))
        ridge(MID, hy, w, h, Color(0xFF26345A))
        ridge(NEAR, hy, w, h, Color(0xFF16223A))
        if (trees) pines(hy, w, h)
        if (path) trail(hy, w, h)

        // Fade into the page at the bottom (and softly at the top) so text can sit over it.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Stage.night0), startY = h * 0.72f, endY = h))
        if (fadeTop) drawRect(Brush.verticalGradient(listOf(Stage.night0, Color.Transparent), startY = 0f, endY = h * 0.4f))
    }
}

/** Ridge profiles as (x, height above horizon in units of the horizon band). */
private val FAR = listOf(0f to 0.10f, 0.12f to 0.20f, 0.22f to 0.14f, 0.34f to 0.30f, 0.46f to 0.16f, 0.58f to 0.26f, 0.70f to 0.12f, 0.82f to 0.24f, 0.93f to 0.15f, 1f to 0.19f)
private val MID = listOf(0f to 0.02f, 0.10f to 0.12f, 0.20f to 0.05f, 0.30f to 0.15f, 0.42f to 0.02f, 0.60f to -0.02f, 0.72f to 0.10f, 0.84f to 0.03f, 0.92f to 0.14f, 1f to 0.06f)
private val NEAR = listOf(0f to -0.06f, 0.14f to -0.02f, 0.30f to -0.10f, 0.50f to -0.14f, 0.70f to -0.10f, 0.86f to -0.03f, 1f to -0.08f)

private fun DrawScope.ridge(points: List<Pair<Float, Float>>, hy: Float, w: Float, h: Float, color: Color) {
    val band = hy * 0.55f
    val p = Path().apply {
        moveTo(0f, h)
        points.forEach { (x, y) -> lineTo(x * w, hy - y * band) }
        lineTo(w, h)
        close()
    }
    drawPath(p, color)
}

/** Small pines along both edges, darker than the nearest ridge. */
private fun DrawScope.pines(hy: Float, w: Float, h: Float) {
    val color = Color(0xFF0E1628)
    val base = hy + (h - hy) * 0.12f
    val spots = listOf(0.02f to 1.0f, 0.07f to 1.35f, 0.12f to 0.9f, 0.17f to 1.15f, 0.83f to 1.1f, 0.88f to 1.4f, 0.93f to 0.95f, 0.98f to 1.2f)
    spots.forEach { (x, s) ->
        val th = w * 0.09f * s
        val tw = th * 0.42f
        val cx = x * w
        for (i in 0..2) {
            val top = base - th + i * th * 0.28f
            val half = tw * (0.45f + i * 0.28f)
            drawPath(Path().apply { moveTo(cx, top); lineTo(cx - half, top + th * 0.42f); lineTo(cx + half, top + th * 0.42f); close() }, color)
        }
        drawRect(color, topLeft = Offset(cx - tw * 0.06f, base - th * 0.05f), size = androidx.compose.ui.geometry.Size(tw * 0.12f, th * 0.2f))
    }
    drawRect(color, Rect(0f, base, w, h).topLeft, androidx.compose.ui.geometry.Size(w, h - base))
}

/** A thin winding path from the foreground toward the sun. */
private fun DrawScope.trail(hy: Float, w: Float, h: Float) {
    val p = Path().apply {
        moveTo(w * 0.42f, h)
        cubicTo(w * 0.40f, h * 0.9f, w * 0.60f, hy + (h - hy) * 0.45f, w * 0.52f, hy + (h - hy) * 0.22f)
        cubicTo(w * 0.48f, hy + (h - hy) * 0.12f, w * 0.52f, hy + 4f, w * 0.5f, hy + 2f)
    }
    drawPath(p, Stage.gold.copy(alpha = 0.35f), style = Stroke(width = w * 0.008f, cap = StrokeCap.Round))
}
