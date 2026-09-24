package com.kairosera.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairosera.R
import com.kairosera.core.ui.components.KairosLogo
import com.kairosera.core.ui.theme.SerifFamily
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay

private val SplashNavy = Color(0xFF1E2D4F)
private val SplashCream = Color(0xFFF5EFE3)

/**
 * Brand moment after the system splash: the sun rises behind the mountain, the path draws in,
 * the name fades up with "• • •", then everything fades into the app. About 1.5 seconds in total,
 * skipped entirely when the person has turned animations off.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val context = LocalContext.current
    val reducedMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val sun = remember { Animatable(0f) }
    val trail = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            onFinished()
            return@LaunchedEffect
        }
        listOf(
            async { sun.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) },
            async { delay(250); trail.animateTo(1f, tween(550, easing = LinearEasing)) },
            async { delay(450); words.animateTo(1f, tween(450)) },
        ).awaitAll()
        delay(350)
        fade.animateTo(0f, tween(300))
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().alpha(fade.value).background(SplashNavy),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KairosLogo(
                Modifier.size(132.dp),
                sunRise = sun.value,
                pathReveal = trail.value,
                description = stringResource(R.string.app_name),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.app_name_display),
                style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = 6.sp),
                color = SplashCream,
                modifier = Modifier.graphicsLayer { alpha = words.value; translationY = (1f - words.value) * 12.dp.toPx() },
            )
            Spacer(Modifier.height(20.dp))
            LoadingDots(Modifier.alpha(words.value))
        }
    }
}

@Composable
private fun LoadingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val a = transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(6.dp).graphicsLayer { alpha = a.value }.clip(CircleShape).background(SplashCream))
        }
    }
}
