package com.kairosera.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairosera.core.settings.ThemeMode

/*
 * Kairos palette: dawn ink (primary), rising sun (secondary), mountain sage (tertiary),
 * on a warm paper background. Few colors, used with meaning.
 */
private val Ink = Color(0xFF2E4A7D)
private val InkLight = Color(0xFFAFC6F5)
private val Sun = Color(0xFFD9772B)
private val SunLight = Color(0xFFFFB77C)
private val Sage = Color(0xFF4F7F62)
private val SageLight = Color(0xFFA6D2B4)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FB),
    onPrimaryContainer = Color(0xFF0F2549),
    secondary = Sun,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3CC),
    onSecondaryContainer = Color(0xFF3A1D05),
    tertiary = Sage,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3EBDB),
    onTertiaryContainer = Color(0xFF0E2A1A),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFEDE7DE),
    onSurfaceVariant = Color(0xFF4D4740),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2EB),
    surfaceContainer = Color(0xFFF2ECE4),
    surfaceContainerHigh = Color(0xFFECE6DD),
    surfaceContainerHighest = Color(0xFFE6E0D7),
    outline = Color(0xFF7F776E),
    outlineVariant = Color(0xFFD3CBC0),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = InkLight,
    onPrimary = Color(0xFF0F2549),
    primaryContainer = Color(0xFF2A4270),
    onPrimaryContainer = Color(0xFFDCE6FB),
    secondary = SunLight,
    onSecondary = Color(0xFF4A2605),
    secondaryContainer = Color(0xFF6A3A12),
    onSecondaryContainer = Color(0xFFFFE3CC),
    tertiary = SageLight,
    onTertiary = Color(0xFF0E2A1A),
    tertiaryContainer = Color(0xFF2F5A40),
    onTertiaryContainer = Color(0xFFD3EBDB),
    background = Color(0xFF131519),
    onBackground = Color(0xFFE6E2DC),
    surface = Color(0xFF131519),
    onSurface = Color(0xFFE6E2DC),
    surfaceVariant = Color(0xFF3A3834),
    onSurfaceVariant = Color(0xFFCBC5BC),
    surfaceContainerLowest = Color(0xFF0E1013),
    surfaceContainerLow = Color(0xFF1A1C20),
    surfaceContainer = Color(0xFF1F2125),
    surfaceContainerHigh = Color(0xFF292B30),
    surfaceContainerHighest = Color(0xFF34363B),
    outline = Color(0xFF959088),
    outlineVariant = Color(0xFF45433F),
)

private val KairosTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(letterSpacing = 1.sp),
    )
}

/** Section label style: small caps-like overline used for "TODAY", "NEXT UP" etc. */
val OverlineStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)

private val KairosShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun KairosTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = KairosTypography, shapes = KairosShapes, content = content)
}
