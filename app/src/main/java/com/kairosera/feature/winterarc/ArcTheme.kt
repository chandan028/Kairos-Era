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
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.res.stringResource
import com.kairosera.domain.winterarc.Habit
import com.kairosera.domain.winterarc.HabitDay
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
 * Winter Arc's own color layer, on top of the winter Kairos tokens (WinterArcTheme): one cool,
 * calm hue per habit (glacier, ice, aurora, winterberry; never neon), each with a readable strong
 * form and a soft fill, in light and dark. Day states use the same family so the calendar, stats
 * and widgets agree.
 */

private data class Pair2(val light: Tone, val dark: Tone)

private val HABIT_TONES = mapOf(
    HabitKind.WATER to Pair2(Tone(Color(0xFF2479C6), Color(0xFFDCEBFA)), Tone(Color(0xFF8CC4F7), Color(0xFF173452))),
    HabitKind.STEPS to Pair2(Tone(Color(0xFF17806F), Color(0xFFD6EFEA)), Tone(Color(0xFF7FD5C6), Color(0xFF133A36))),
    HabitKind.ZERO_SUGAR to Pair2(Tone(Color(0xFFB8385B), Color(0xFFF8DEE6)), Tone(Color(0xFFF39AB2), Color(0xFF43202D))),
    HabitKind.COLD_SHOWER to Pair2(Tone(Color(0xFF0B87A8), Color(0xFFD5F0F7)), Tone(Color(0xFF7CD6EE), Color(0xFF113745))),
    HabitKind.READING to Pair2(Tone(Color(0xFF6C51C6), Color(0xFFE8E3FA)), Tone(Color(0xFFBDB0F5), Color(0xFF2A2652))),
    HabitKind.STUDY to Pair2(Tone(Color(0xFF3A50BE), Color(0xFFDFE4FA)), Tone(Color(0xFFA3B3F5), Color(0xFF1F2A57))),
    HabitKind.DEEP_WORK to Pair2(Tone(Color(0xFF9E3486), Color(0xFFF6DFF0)), Tone(Color(0xFFEA9FD6), Color(0xFF41213C))),
    HabitKind.WAKE_EARLY to Pair2(Tone(Color(0xFF1F3F6E), Color(0xFFDCE5F3)), Tone(Color(0xFFA9C2EC), Color(0xFF1D2D4C))),
    HabitKind.DIGITAL_DETOX to Pair2(Tone(Color(0xFF1C8664), Color(0xFFD5F0E5)), Tone(Color(0xFF8BDCBE), Color(0xFF133A31))),
    HabitKind.SPEAK to Pair2(Tone(Color(0xFFB45A22), Color(0xFFFAE5D8)), Tone(Color(0xFFF4B48A), Color(0xFF43291B))),
    HabitKind.CUSTOM to Pair2(Tone(Color(0xFF2479C6), Color(0xFFDCEBFA)), Tone(Color(0xFF8CC4F7), Color(0xFF173452))),
)

/** The colors a custom habit can pick: the same calm winter family as the built-in habits. */
private val CUSTOM_TONES = listOf(
    HABIT_TONES.getValue(HabitKind.WATER),
    HABIT_TONES.getValue(HabitKind.STEPS),
    HABIT_TONES.getValue(HabitKind.ZERO_SUGAR),
    HABIT_TONES.getValue(HabitKind.COLD_SHOWER),
    HABIT_TONES.getValue(HabitKind.READING),
    HABIT_TONES.getValue(HabitKind.STUDY),
    HABIT_TONES.getValue(HabitKind.DEEP_WORK),
    HABIT_TONES.getValue(HabitKind.SPEAK),
)

/** Icons a custom habit can pick, by the key stored with it. */
val CUSTOM_ICONS: List<Pair<String, ImageVector>> = listOf(
    "check" to Icons.Outlined.CheckCircle,
    "fitness" to Icons.Outlined.FitnessCenter,
    "yoga" to Icons.Outlined.SelfImprovement,
    "run" to Icons.Outlined.DirectionsBike,
    "food" to Icons.Outlined.Restaurant,
    "journal" to Icons.Outlined.EditNote,
    "language" to Icons.Outlined.Translate,
    "code" to Icons.Outlined.Code,
    "music" to Icons.Outlined.MusicNote,
    "art" to Icons.Outlined.Brush,
    "money" to Icons.Outlined.Savings,
    "heart" to Icons.Outlined.FavoriteBorder,
    "pill" to Icons.Outlined.Medication,
    "sleep" to Icons.Outlined.Bedtime,
    "snow" to Icons.Outlined.AcUnit,
    "star" to Icons.Outlined.StarOutline,
)

val CUSTOM_COLOR_COUNT: Int get() = CUSTOM_TONES.size

object Arc {
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() < 0.4f

    @Composable @ReadOnlyComposable
    fun tone(kind: HabitKind): Tone = HABIT_TONES.getValue(kind).let { if (isDark) it.dark else it.light }

    /** A habit's color: its kind's for built-in habits, the chosen one for custom habits. */
    @Composable @ReadOnlyComposable
    fun tone(habit: Habit): Tone = if (habit.isCustom) customTone(habit.color) else tone(habit.kind)

    @Composable @ReadOnlyComposable
    fun tone(day: HabitDay): Tone = tone(day.habit)

    @Composable @ReadOnlyComposable
    fun customTone(index: Int): Tone = CUSTOM_TONES[index.mod(CUSTOM_TONES.size)].let { if (isDark) it.dark else it.light }

    /** Glacier blue for "today" and primary Winter Arc actions. */
    val primary: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF8DCFFF) else Color(0xFF1F6DB8)
    val onPrimary: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF07182B) else Color.White
    val primarySoft: Color
        @Composable @ReadOnlyComposable get() = if (isDark) Color(0xFF16344F) else Color(0xFFD9EAF9)

    @Composable @ReadOnlyComposable
    fun dayColor(state: DayState): Color = when (state) {
        DayState.COMPLETE -> if (isDark) Color(0xFF7FD9B4) else Color(0xFF26966E)
        DayState.PARTIAL -> if (isDark) Color(0xFFEBCB74) else Color(0xFFE2AE3A)
        DayState.MISSED -> if (isDark) Color(0xFFEE8FA2) else Color(0xFFDD6079)
        DayState.TODAY -> primary
        DayState.PAUSED -> if (isDark) Color(0xFF3E516D) else Color(0xFFB8C7D8)
        DayState.FUTURE, DayState.OUTSIDE -> if (isDark) Color(0xFF1A2C45) else Color(0xFFDAE5F1)
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
    HabitKind.CUSTOM -> Icons.Outlined.CheckCircle
}

fun customIcon(key: String): ImageVector = CUSTOM_ICONS.firstOrNull { it.first == key }?.second ?: Icons.Outlined.CheckCircle

/** A habit's icon: built-in by kind, custom by the icon it was given. */
fun Habit.iconVector(): ImageVector = if (isCustom) customIcon(icon) else kind.icon()
fun HabitDay.iconVector(): ImageVector = habit.iconVector()

/** A habit's display names. Custom habits use the name the person typed. */
@Composable @ReadOnlyComposable
fun Habit.shortName(): String = if (isCustom) name else stringResource(kind.shortLabel())

@Composable @ReadOnlyComposable
fun Habit.longName(): String = if (isCustom) name else stringResource(kind.longLabel())

@Composable @ReadOnlyComposable
fun HabitDay.shortName(): String = habit.shortName()

@Composable @ReadOnlyComposable
fun HabitDay.longName(): String = habit.longName()

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
    HabitKind.CUSTOM -> R.string.wa_custom_habit
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
    HabitKind.CUSTOM -> R.string.wa_custom_habit
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
    HabitKind.CUSTOM -> R.string.wa_custom_habit
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
