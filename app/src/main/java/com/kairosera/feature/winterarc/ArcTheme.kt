package com.kairosera.feature.winterarc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.kairosera.R
import com.kairosera.core.ui.theme.Tone
import com.kairosera.domain.winterarc.DayState
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.StudyCategory
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Winter Arc's own color layer, on top of the Kairos tokens: one calm hue per habit (never neon),
 * each with a readable strong form and a soft fill, in light and dark. Day states use the same
 * family so the calendar, stats and widgets agree.
 */

private data class Pair2(val light: Tone, val dark: Tone)

private val HABIT_TONES = mapOf(
    HabitKind.WATER to Pair2(Tone(Color(0xFF2F6FD6), Color(0xFFE3EEFC)), Tone(Color(0xFF8DB6F5), Color(0xFF1F3355))),
    HabitKind.STEPS to Pair2(Tone(Color(0xFF2E8B57), Color(0xFFE0F3E7)), Tone(Color(0xFF8ED3A8), Color(0xFF1D3A2B))),
    HabitKind.ZERO_SUGAR to Pair2(Tone(Color(0xFFCF4338), Color(0xFFFCE4E1)), Tone(Color(0xFFF29A90), Color(0xFF452423))),
    HabitKind.COLD_SHOWER to Pair2(Tone(Color(0xFF1A8FAD), Color(0xFFDDF3F8)), Tone(Color(0xFF86D6E8), Color(0xFF17363F))),
    HabitKind.READING to Pair2(Tone(Color(0xFF7553C9), Color(0xFFECE5FA)), Tone(Color(0xFFC3B1F2), Color(0xFF2F2850))),
    HabitKind.STUDY to Pair2(Tone(Color(0xFFC96A12), Color(0xFFFDEBD8)), Tone(Color(0xFFF5B375), Color(0xFF42301C))),
    HabitKind.DEEP_WORK to Pair2(Tone(Color(0xFFD12F64), Color(0xFFFBE2EA)), Tone(Color(0xFFF39ABB), Color(0xFF45222F))),
    HabitKind.WAKE_EARLY to Pair2(Tone(Color(0xFF2C3A6B), Color(0xFFE1E5F2)), Tone(Color(0xFFA9B6E8), Color(0xFF252E4D))),
    HabitKind.DIGITAL_DETOX to Pair2(Tone(Color(0xFF2F8F6A), Color(0xFFDFF2EA)), Tone(Color(0xFF93D6BB), Color(0xFF1C3A30))),
    HabitKind.SPEAK to Pair2(Tone(Color(0xFF9343C2), Color(0xFFF1E3FA)), Tone(Color(0xFFD7A8F0), Color(0xFF3A2549))),
)

object Arc {
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() < 0.4f

    @Composable @ReadOnlyComposable
    fun tone(kind: HabitKind): Tone = HABIT_TONES.getValue(kind).let { if (isDark) it.dark else it.light }

    /** Bright blue for "today" and primary Winter Arc actions. */
    val primary: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF8DB6F5) else Color(0xFF2F6FD6)
    val onPrimary: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF0D1729) else Color.White
    val primarySoft: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF1F3355) else Color(0xFFE3EEFC)

    @Composable @ReadOnlyComposable
    fun dayColor(state: DayState): Color = when (state) {
        DayState.COMPLETE -> if (isDark) Color(0xFF6CC48A) else Color(0xFF3DA35D)
        DayState.PARTIAL -> if (isDark) Color(0xFFE9C46A) else Color(0xFFE9B23C)
        DayState.MISSED -> if (isDark) Color(0xFFE5837A) else Color(0xFFE57368)
        DayState.TODAY -> primary
        DayState.PAUSED -> if (isDark) Color(0xFF4A5675) else Color(0xFFC9CED9)
        DayState.FUTURE, DayState.OUTSIDE -> if (isDark) Color(0xFF26324D) else Color(0xFFE7E3DA)
    }

    /** Soft version of a day color, for consistency grids. */
    @Composable @ReadOnlyComposable
    fun daySoft(state: DayState): Color = dayColor(state).copy(alpha = if (state == DayState.FUTURE || state == DayState.OUTSIDE) 1f else 0.85f)
}

fun HabitKind.icon(): ImageVector = when (this) {
    HabitKind.WATER -> Icons.Outlined.WaterDrop
    HabitKind.STEPS -> Icons.Outlined.DirectionsRun
    HabitKind.ZERO_SUGAR -> Icons.Outlined.Block
    HabitKind.COLD_SHOWER -> Icons.Outlined.AcUnit
    HabitKind.READING -> Icons.Outlined.AutoStories
    HabitKind.STUDY -> Icons.Outlined.School
    HabitKind.DEEP_WORK -> Icons.Outlined.TrackChanges
    HabitKind.WAKE_EARLY -> Icons.Outlined.Bedtime
    HabitKind.DIGITAL_DETOX -> Icons.Outlined.Spa
    HabitKind.SPEAK -> Icons.Outlined.Mic
}

/** Short name for cards and grids. */
fun HabitKind.shortLabel(): Int = when (this) {
    HabitKind.WATER -> R.string.wa_h_water
    HabitKind.STEPS -> R.string.wa_h_steps
    HabitKind.ZERO_SUGAR -> R.string.wa_h_zero_sugar
    HabitKind.COLD_SHOWER -> R.string.wa_h_cold_shower
    HabitKind.READING -> R.string.wa_h_reading
    HabitKind.STUDY -> R.string.wa_h_study
    HabitKind.DEEP_WORK -> R.string.wa_h_deep_work
    HabitKind.WAKE_EARLY -> R.string.wa_h_wake
    HabitKind.DIGITAL_DETOX -> R.string.wa_h_detox
    HabitKind.SPEAK -> R.string.wa_h_speak
}

/** Full name for the Habits list. */
fun HabitKind.longLabel(): Int = when (this) {
    HabitKind.WATER -> R.string.wa_hl_water
    HabitKind.STEPS -> R.string.wa_hl_steps
    HabitKind.ZERO_SUGAR -> R.string.wa_hl_zero_sugar
    HabitKind.COLD_SHOWER -> R.string.wa_hl_cold_shower
    HabitKind.READING -> R.string.wa_hl_reading
    HabitKind.STUDY -> R.string.wa_hl_study
    HabitKind.DEEP_WORK -> R.string.wa_hl_deep_work
    HabitKind.WAKE_EARLY -> R.string.wa_hl_wake
    HabitKind.DIGITAL_DETOX -> R.string.wa_hl_detox
    HabitKind.SPEAK -> R.string.wa_hl_speak
}

/** The tiny labels of the consistency grid. */
fun HabitKind.gridLabel(): Int = when (this) {
    HabitKind.READING -> R.string.wa_h_short_book
    HabitKind.DEEP_WORK -> R.string.wa_h_short_focus
    HabitKind.WAKE_EARLY -> R.string.wa_h_short_wake
    HabitKind.SPEAK -> R.string.wa_h_short_speak
    else -> shortLabel()
}

fun HabitKind.commitLabel(): Int = when (this) {
    HabitKind.WATER -> R.string.wa_ob_c_water
    HabitKind.STEPS -> R.string.wa_ob_c_steps
    HabitKind.ZERO_SUGAR -> R.string.wa_ob_c_sugar
    HabitKind.COLD_SHOWER -> R.string.wa_ob_c_cold
    HabitKind.READING -> R.string.wa_ob_c_read
    HabitKind.STUDY -> R.string.wa_ob_c_study
    HabitKind.DEEP_WORK -> R.string.wa_ob_c_focus
    HabitKind.WAKE_EARLY -> R.string.wa_ob_c_wake
    HabitKind.DIGITAL_DETOX -> R.string.wa_ob_c_detox
    HabitKind.SPEAK -> R.string.wa_ob_c_speak
}

fun StudyCategory.label(): Int = when (this) {
    StudyCategory.JAVA -> R.string.wa_cat_java
    StudyCategory.DSA -> R.string.wa_cat_dsa
    StudyCategory.AI_LLM -> R.string.wa_cat_ai
    StudyCategory.SYSTEM_DESIGN -> R.string.wa_cat_design
    StudyCategory.INTERVIEW -> R.string.wa_cat_interview
}

@Composable @ReadOnlyComposable
fun StudyCategory.tone(): Tone = when (this) {
    StudyCategory.JAVA -> Arc.tone(HabitKind.STUDY)
    StudyCategory.DSA -> Arc.tone(HabitKind.WATER)
    StudyCategory.AI_LLM -> Arc.tone(HabitKind.READING)
    StudyCategory.SYSTEM_DESIGN -> Arc.tone(HabitKind.DIGITAL_DETOX)
    StudyCategory.INTERVIEW -> Arc.tone(HabitKind.DEEP_WORK)
}

/** Number formatting for Winter Arc values. */
object ArcFormat {
    fun litres(ml: Double, locale: Locale = Locale.getDefault()): String {
        val l = ml / 1000.0
        val nf = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (l % 1.0 == 0.0) 0 else 2; minimumFractionDigits = 0 }
        return nf.format((l * 100).roundToInt() / 100.0)
    }

    fun count(n: Int, locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(n)

    /** 10000 -> "10K", 6240 -> "6.2K". */
    fun compact(n: Int): String = when {
        n >= 1000 -> {
            val k = n / 1000.0
            if (k % 1.0 == 0.0 || k >= 100) "${k.roundToInt()}K" else "${(k * 10).roundToInt() / 10.0}K"
        }
        else -> n.toString()
    }

    fun hours(minutes: Int): String {
        val h = minutes / 60.0
        return if (h % 1.0 == 0.0) h.roundToInt().toString() else ((h * 10).roundToInt() / 10.0).toString()
    }

    fun clock(seconds: Int): String = "%02d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)

    fun longClock(seconds: Int): String = if (seconds >= 3600) "%d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60) else clock(seconds)
}
