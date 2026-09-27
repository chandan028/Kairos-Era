package com.kairosera.feature.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kairosera.R
import com.kairosera.core.settings.OnboardingStep
import com.kairosera.core.ui.theme.Stage
import androidx.compose.ui.util.lerp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Where the sun sits in the sunrise photo (fractions of its width and height). */
private const val SUN_X = 0.56f
private const val SUN_Y = 0.57f

/**
 * How the photo is framed for a step, in pixels of the screen. [sunY] places the sun; [veil]
 * dims the whole photo; from [shadeFrom] down the photo fades to [shade] night, and above
 * [clearFrom] it fades to [topShade] night, so text there stays readable. The photo never
 * competes with words: wherever a step has text, it is dimmed.
 */
private data class BackdropLook(
    val sunY: Float,
    val veil: Float,
    val shadeFrom: Float,
    val shade: Float,
    val clearFrom: Float = 0f,
    val topShade: Float = 0f,
)

/**
 * Welcome and Ready anchor the sun to their layouts (a clear band below the logo, a gap below the
 * greeting), measured from the top of the content under the step header; the text-heavy steps
 * anchor it low and keep the photo quiet behind their cards.
 */
private fun lookFor(step: OnboardingStep, h: Float, contentTop: Float, dp: Float) = when (step) {
    // The logo and wordmark keep a dark sky behind them; the glow opens up just below the tagline.
    OnboardingStep.WELCOME -> (contentTop + 296 * dp).let {
        BackdropLook(sunY = it, veil = 0f, shadeFrom = it - 6 * dp, shade = 0.9f, clearFrom = contentTop + 212 * dp, topShade = 0.82f)
    }
    OnboardingStep.FOCUS -> BackdropLook(sunY = h * 0.72f, veil = 0.5f, shadeFrom = h - 230 * dp, shade = 0.92f)
    OnboardingStep.FIRST_ACTION -> BackdropLook(sunY = h * 0.76f, veil = 0.5f, shadeFrom = h - 230 * dp, shade = 0.92f)
    OnboardingStep.REMINDERS -> BackdropLook(sunY = h * 0.66f, veil = 0.55f, shadeFrom = h * 0.55f, shade = 0.92f)
    // The greeting sits on dark sky; the sun is up in the gap before the preview card.
    OnboardingStep.READY -> (contentTop + 206 * dp).let {
        BackdropLook(sunY = it, veil = 0f, shadeFrom = it + 12 * dp, shade = 0.9f, clearFrom = contentTop + 128 * dp, topShade = 0.82f)
    }
}

/**
 * The sunrise photo behind the whole flow. It stays put while steps slide over it and only
 * re-frames (the sun drifting, the veil deepening) as the step changes; instantly when the
 * person has turned animations off.
 */
@Composable
internal fun SunriseBackdrop(step: OnboardingStep, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    // Animate the step as a number and blend the two neighbouring framings while drawing, so the
    // framing is always computed from the real canvas size.
    val t by animateFloatAsState(step.ordinal.toFloat(), if (reduceMotion) snap() else tween(700), label = "backdrop")
    val topInset = WindowInsets.statusBars.getTop(LocalDensity.current)
    val photo = ImageBitmap.imageResource(R.drawable.onboarding_sunrise)
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        val contentTop = topInset + HEADER_HEIGHT.toPx()
        val steps = OnboardingStep.entries
        val a = lookFor(steps[floor(t).toInt().coerceIn(0, steps.lastIndex)], size.height, contentTop, density)
        val b = lookFor(steps[ceil(t).toInt().coerceIn(0, steps.lastIndex)], size.height, contentTop, density)
        val f = t - floor(t)
        val sunY = lerp(a.sunY, b.sunY, f)
        val veil = lerp(a.veil, b.veil, f)
        val shadeFrom = lerp(a.shadeFrom, b.shadeFrom, f)
        val shade = lerp(a.shade, b.shade, f)
        val clearFrom = lerp(a.clearFrom, b.clearFrom, f)
        val topShade = lerp(a.topShade, b.topShade, f)
        val w = size.width
        val h = size.height
        drawRect(Stage.night0)

        // Cover the width (and most of the height on tall screens), then slide so the sun is at sunY.
        val scale = max(w / photo.width, h * 0.8f / photo.height)
        val pw = photo.width * scale
        val ph = photo.height * scale
        val x = (w / 2f - SUN_X * pw).coerceIn(minOf(w - pw, 0f), 0f)
        val y = sunY - SUN_Y * ph
        drawImage(
            photo,
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
            dstSize = IntSize(pw.roundToInt(), ph.roundToInt()),
            filterQuality = FilterQuality.High,
        )
        // Melt the photo's top and bottom edges into the night so no seam shows.
        val edge = ph * 0.1f
        drawRect(Brush.verticalGradient(listOf(Stage.night0, Color.Transparent), startY = y, endY = y + edge))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Stage.night0), startY = y + ph - edge, endY = y + ph))

        if (veil > 0f) drawRect(Stage.night0.copy(alpha = veil))
        // Shade where the text sits (clamped: clear above shadeFrom, full shade below the ramp).
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Stage.night0.copy(alpha = shade)), startY = shadeFrom, endY = shadeFrom + 110.dp.toPx()))
        if (topShade > 0f) {
            drawRect(Brush.verticalGradient(listOf(Stage.night0.copy(alpha = topShade), Color.Transparent), startY = clearFrom - 90.dp.toPx(), endY = clearFrom))
        }
        // A soft band under the status bar and step header.
        drawRect(Brush.verticalGradient(listOf(Stage.night0.copy(alpha = 0.6f), Color.Transparent), startY = 0f, endY = h * 0.14f), size = Size(w, h * 0.14f), topLeft = Offset.Zero)
    }
}
