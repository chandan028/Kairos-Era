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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairosera.R
import com.kairosera.core.settings.ThemeMode

/*
 * Kairos design tokens, in three layers:
 *  1. Primitives (raw brand values below; never used directly by screens).
 *  2. Semantic roles: the Material color scheme plus [KairosColors] for meaning
 *     (done, info, motivation, learning, activity).
 *  3. Components read only semantic roles, so light, dark and future themes stay consistent.
 *
 * Color is semantic, not decoration: green = done/progress, blue = information,
 * amber = motivation, purple = learning, coral = activity. Navy + cream carry the brand.
 */
private object Palette {
    val Navy950 = Color(0xFF0B1322)
    val Navy900 = Color(0xFF0D1729) // night page
    val Navy850 = Color(0xFF15233D) // night card
    val Navy800 = Color(0xFF1B2B48) // night raised surface
    val Navy700 = Color(0xFF1E2D4F) // Deep Navy, the brand color
    val Navy600 = Color(0xFF2E3F66)
    val Navy300 = Color(0xFF8E9AB5)
    val Navy100 = Color(0xFFDCE1EC)

    val Cream50 = Color(0xFFFDFAF4)
    val Cream100 = Color(0xFFF5EFE3) // Warm Cream, the page
    val Cream200 = Color(0xFFEDE5D5)
    val Cream300 = Color(0xFFDDD3C0)
    val Cream400 = Color(0xFFB9AE99)

    val Ink = Color(0xFF1A2233)
    val InkMuted = Color(0xFF5B6170)
    val Paper = Color(0xFFF4EFE5)
    val PaperMuted = Color(0xFFAAB4C7)

    val Blue = Color(0xFF3A67AE); val BlueSoft = Color(0xFFDDE7F7); val BlueNight = Color(0xFF9DBBEB); val BlueNightSoft = Color(0xFF22345A)
    val Green = Color(0xFF36724E); val GreenSoft = Color(0xFFDCEDE1); val GreenNight = Color(0xFF9FD0AE); val GreenNightSoft = Color(0xFF1E3A30)
    val Amber = Color(0xFF8A5D0C); val AmberSoft = Color(0xFFF8E7C2); val AmberBright = Color(0xFFE8B04B)
    val AmberNight = Color(0xFFF0C572); val AmberNightSoft = Color(0xFF3D3220)
    val Purple = Color(0xFF62519F); val PurpleSoft = Color(0xFFE6E1F5); val PurpleNight = Color(0xFFC2B7EC); val PurpleNightSoft = Color(0xFF2E2A4E)
    val Coral = Color(0xFFA94E39); val CoralSoft = Color(0xFFF7DDD5); val CoralNight = Color(0xFFF0AC9A); val CoralNightSoft = Color(0xFF45282A)
    val Error = Color(0xFFB3261E)
}

/** One semantic hue: [strong] for text, icons and bars (meets 4.5:1 on the page); [soft] for quiet fills. */
@Immutable
data class Tone(val strong: Color, val soft: Color)

@Immutable
data class KairosColors(
    val success: Tone,
    val info: Tone,
    val motivation: Tone,
    val learning: Tone,
    val activity: Tone,
    /** Brand block color (hero cards, primary buttons, the quote screen). */
    val brand: Color,
    val onBrand: Color,
    /** Accent used on brand blocks (the rising sun). */
    val sun: Color,
    /** Raised card on the page. */
    val card: Color,
    /** Hairline between rows, and progress tracks. */
    val line: Color,
    val track: Color,
    val muted: Color,
    /** Warm gold: the one accent for progress, today and the selected day. [accentText] is its readable form for text. */
    val accent: Color,
    val accentText: Color,
    val accentSoft: Color,
    /** Soft blue, the calm second accent. */
    val calm: Color,
    /** Activity intensity, none to a full day: navy, soft blue, blue, gold. */
    val heat: List<Color>,
)

private val LightKairos = KairosColors(
    success = Tone(Palette.Green, Palette.GreenSoft),
    info = Tone(Palette.Blue, Palette.BlueSoft),
    motivation = Tone(Palette.Amber, Palette.AmberSoft),
    learning = Tone(Palette.Purple, Palette.PurpleSoft),
    activity = Tone(Palette.Coral, Palette.CoralSoft),
    brand = Palette.Navy700,
    onBrand = Palette.Cream100,
    sun = Palette.AmberBright,
    card = Palette.Cream50,
    line = Palette.Cream200,
    track = Palette.Cream200,
    muted = Palette.InkMuted,
    accent = Color(0xFFD9A441),
    accentText = Palette.Amber,
    accentSoft = Color(0xFFF6E7C4),
    calm = Color(0xFF4F72C4),
    heat = listOf(Palette.Cream200, Color(0xFFC9D5EE), Color(0xFF7E9BD8), Color(0xFFD9A441)),
)

private val DarkKairos = KairosColors(
    success = Tone(Palette.GreenNight, Palette.GreenNightSoft),
    info = Tone(Palette.BlueNight, Palette.BlueNightSoft),
    motivation = Tone(Palette.AmberNight, Palette.AmberNightSoft),
    learning = Tone(Palette.PurpleNight, Palette.PurpleNightSoft),
    activity = Tone(Palette.CoralNight, Palette.CoralNightSoft),
    brand = Palette.Navy800,
    onBrand = Palette.Paper,
    sun = Palette.AmberBright,
    card = Palette.Navy850,
    line = Color(0xFF26324D),
    track = Color(0xFF26324D),
    muted = Palette.PaperMuted,
    accent = Color(0xFFF3C96A),
    accentText = Color(0xFFF3C96A),
    accentSoft = Color(0xFF3A3322),
    calm = Color(0xFF6D8EDB),
    heat = listOf(Color(0xFF1E2C47), Color(0xFF2E4270), Color(0xFF5876BF), Color(0xFFF3C96A)),
)

private val LightColors = lightColorScheme(
    primary = Palette.Navy700,
    onPrimary = Palette.Cream100,
    primaryContainer = Palette.Navy100,
    onPrimaryContainer = Palette.Navy700,
    secondary = Palette.Amber,
    onSecondary = Color.White,
    secondaryContainer = Palette.AmberSoft,
    onSecondaryContainer = Color(0xFF3A2804),
    tertiary = Palette.Green,
    onTertiary = Color.White,
    tertiaryContainer = Palette.GreenSoft,
    onTertiaryContainer = Color(0xFF0E2A1A),
    background = Palette.Cream100,
    onBackground = Palette.Ink,
    surface = Palette.Cream100,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Cream200,
    onSurfaceVariant = Palette.InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.Cream50,
    surfaceContainer = Palette.Cream50,
    surfaceContainerHigh = Palette.Cream50,
    surfaceContainerHighest = Palette.Cream200,
    inverseSurface = Palette.Navy700,
    inverseOnSurface = Palette.Cream100,
    inversePrimary = Palette.AmberBright,
    outline = Palette.Cream400,
    outlineVariant = Palette.Cream300,
    error = Palette.Error,
    scrim = Palette.Navy950,
)

private val DarkColors = darkColorScheme(
    primary = Palette.Paper,
    onPrimary = Palette.Navy900,
    primaryContainer = Palette.Navy600,
    onPrimaryContainer = Palette.Paper,
    secondary = Palette.AmberNight,
    onSecondary = Color(0xFF3A2804),
    secondaryContainer = Palette.AmberNightSoft,
    onSecondaryContainer = Palette.AmberNight,
    tertiary = Palette.GreenNight,
    onTertiary = Color(0xFF0E2A1A),
    tertiaryContainer = Palette.GreenNightSoft,
    onTertiaryContainer = Palette.GreenNight,
    background = Palette.Navy900,
    onBackground = Palette.Paper,
    surface = Palette.Navy900,
    onSurface = Palette.Paper,
    surfaceVariant = Color(0xFF26324D),
    onSurfaceVariant = Palette.PaperMuted,
    surfaceContainerLowest = Palette.Navy950,
    surfaceContainerLow = Palette.Navy850,
    surfaceContainer = Palette.Navy850,
    surfaceContainerHigh = Palette.Navy800,
    surfaceContainerHighest = Palette.Navy600,
    inverseSurface = Palette.Paper,
    inverseOnSurface = Palette.Navy900,
    inversePrimary = Palette.Navy700,
    outline = Palette.Navy300,
    outlineVariant = Color(0xFF2E3A56),
    scrim = Color.Black,
)

/** Lora, a calm book serif, for the few moments that should feel personal: greeting, quotes, big numbers. */
@OptIn(ExperimentalTextApi::class)
val SerifFamily = FontFamily(
    Font(R.font.lora, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lora, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.lora, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/*
 * Few sizes on purpose. Every Material slot maps onto one of five steps:
 *  Hero 32 (serif)  · Large 24 (serif) · Title 18 · Body 16/14 · Small 13/12.
 */
private val KairosTypography = Typography().let { base ->
    val hero = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.3).sp, lineHeightStyle = Trim)
    val large = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp, lineHeightStyle = Trim)
    val title = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val sub = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val small = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp)
    val labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp)
    base.copy(
        displayLarge = hero.copy(fontSize = 44.sp, lineHeight = 52.sp),
        displayMedium = hero.copy(fontSize = 40.sp, lineHeight = 48.sp),
        displaySmall = hero,
        headlineLarge = hero,
        headlineMedium = hero.copy(fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = large,
        titleLarge = title,
        titleMedium = sub,
        titleSmall = label.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = body,
        bodyMedium = bodySmall,
        bodySmall = small,
        labelLarge = label,
        labelMedium = labelSmall,
        labelSmall = labelSmall.copy(fontSize = 11.sp),
    )
}

/** Section label: small, spaced capitals for "TODAY", "NEXT UP" etc. */
val OverlineStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, lineHeight = 16.sp)

/** Serif quote style, for the daily thought and "why" statements. */
val QuoteStyle = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp)

private val KairosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Spacing scale (4-pt grid). Screens use these instead of ad-hoc numbers. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 20.dp
}

private val LocalKairosColors = staticCompositionLocalOf { LightKairos }

/** Accessor for Kairos-specific semantic colors: `Kairos.colors.success.strong`. */
object Kairos {
    val colors: KairosColors
        @Composable @ReadOnlyComposable get() = LocalKairosColors.current
}

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
    CompositionLocalProvider(LocalKairosColors provides if (dark) DarkKairos else LightKairos) {
        MaterialTheme(colorScheme = colors, typography = KairosTypography, shapes = KairosShapes, content = content)
    }
}
