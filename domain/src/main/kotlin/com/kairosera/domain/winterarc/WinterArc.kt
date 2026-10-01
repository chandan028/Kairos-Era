package com.kairosera.domain.winterarc

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/*
 * Winter Arc: a 90-day discipline challenge layered on top of Kairos Era. Everything here is plain
 * data and arithmetic so the rules (what day it is, what counts as done, streaks) are testable and
 * the same everywhere: dashboard, calendar, statistics and widgets.
 */

enum class ArcStatus { ACTIVE, PAUSED, COMPLETED, ENDED }

/** One pause, inclusive. [end] is null while the pause is still running. */
data class ArcPause(val start: LocalDate, val end: LocalDate?) {
    fun contains(date: LocalDate, today: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end ?: today)
}

data class WinterArc(
    val id: Long = 0,
    val startDate: LocalDate,
    val durationDays: Int = DEFAULT_DURATION,
    val status: ArcStatus = ArcStatus.ACTIVE,
    val pauses: List<ArcPause> = emptyList(),
    val createdAt: Instant = Instant.EPOCH,
) {
    val isPaused: Boolean get() = status == ArcStatus.PAUSED

    /** Days skipped by pauses strictly before [date]. */
    fun pausedDaysBefore(date: LocalDate, today: LocalDate): Int = pauses.sumOf { p ->
        val end = minOf(p.end ?: today, date.minusDays(1))
        if (end.isBefore(p.start)) 0 else ChronoUnit.DAYS.between(p.start, end).toInt() + 1
    }

    fun isPausedOn(date: LocalDate, today: LocalDate): Boolean = pauses.any { it.contains(date, today) }

    /**
     * The challenge day [date] falls on (1-based), not clamped: 0 or less is before the start,
     * more than [durationDays] is after the end. Paused days do not advance the count.
     */
    fun rawDayNumber(date: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(startDate, date).toInt() - pausedDaysBefore(date, today) + 1

    /** "Day X / 90" for display: clamped to 1..[durationDays]. */
    fun dayNumber(date: LocalDate, today: LocalDate): Int = rawDayNumber(date, today).coerceIn(1, durationDays)

    /** The last day of the challenge, moved later by every paused day so far. */
    fun endDate(today: LocalDate): LocalDate {
        val paused = pauses.sumOf { p -> ChronoUnit.DAYS.between(p.start, p.end ?: today).toInt() + 1 }
        return startDate.plusDays((durationDays - 1 + paused).toLong())
    }

    /** True for dates that count toward the challenge (inside it and not paused). */
    fun counts(date: LocalDate, today: LocalDate): Boolean {
        if (date.isBefore(startDate) || isPausedOn(date, today)) return false
        return rawDayNumber(date, today) in 1..durationDays
    }

    /** Every counting date up to and including [until], in order. */
    fun countingDates(until: LocalDate, today: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = startDate
        val last = minOf(until, endDate(today))
        while (!d.isAfter(last)) {
            if (counts(d, today)) out.add(d)
            d = d.plusDays(1)
        }
        return out
    }

    fun hasStarted(today: LocalDate): Boolean = !today.isBefore(startDate)
    fun isFinished(today: LocalDate): Boolean = today.isAfter(endDate(today))

    fun paused(on: LocalDate) = copy(status = ArcStatus.PAUSED, pauses = pauses + ArcPause(on, null))

    fun resumed(on: LocalDate): WinterArc {
        val open = pauses.lastOrNull()?.takeIf { it.end == null } ?: return copy(status = ArcStatus.ACTIVE)
        // Resuming on the day the pause began cancels it; otherwise the pause ends yesterday.
        val rest = pauses.dropLast(1)
        val closed = if (!on.isAfter(open.start)) rest else rest + open.copy(end = on.minusDays(1))
        return copy(status = ArcStatus.ACTIVE, pauses = closed)
    }

    companion object {
        const val DEFAULT_DURATION = 90

        /** Pauses as "start:end" epoch days separated by ';' (end empty while running). */
        fun encodePauses(pauses: List<ArcPause>): String =
            pauses.joinToString(";") { "${it.start.toEpochDay()}:${it.end?.toEpochDay() ?: ""}" }

        fun decodePauses(raw: String): List<ArcPause> = raw.split(';').mapNotNull { part ->
            val bits = part.split(':')
            val start = bits.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            ArcPause(LocalDate.ofEpochDay(start), bits.getOrNull(1)?.toLongOrNull()?.let(LocalDate::ofEpochDay))
        }
    }
}

/** How a habit is measured. */
enum class HabitType { AMOUNT, CHECK, TIME }

/**
 * The ten built-in Winter Arc habits, plus [CUSTOM] for the ones a person adds themselves. Keys are
 * stored in the database, so never rename an entry. Display names come from string resources; the
 * private habit is only ever "Digital Detox". A custom habit carries its own name, type and target.
 */
enum class HabitKind(val type: HabitType, val defaultTarget: Double, val unit: String, val category: String) {
    WATER(HabitType.AMOUNT, 3000.0, "ml", "health"),
    STEPS(HabitType.AMOUNT, 10_000.0, "steps", "health"),
    ZERO_SUGAR(HabitType.CHECK, 1.0, "", "health"),
    COLD_SHOWER(HabitType.CHECK, 1.0, "", "health"),
    READING(HabitType.AMOUNT, 30.0, "min", "mind"),
    STUDY(HabitType.AMOUNT, 60.0, "min", "career"),
    DEEP_WORK(HabitType.AMOUNT, 120.0, "min", "career"),
    WAKE_EARLY(HabitType.TIME, 300.0, "min_of_day", "discipline"),
    DIGITAL_DETOX(HabitType.CHECK, 1.0, "", "discipline"),
    SPEAK(HabitType.CHECK, 1.0, "", "mind"),
    CUSTOM(HabitType.CHECK, 1.0, "", "custom"),
    ;

    companion object {
        /** The habits Winter Arc ships with, in their default order. */
        val BUILT_IN: List<HabitKind> = entries.filter { it != CUSTOM }

        /** A built-in habit by its stored key; custom habit ids return null. */
        fun fromKey(key: String): HabitKind? = BUILT_IN.firstOrNull { it.name == key }
    }
}

/**
 * One habit. Built-in habits are identified by their kind; custom ones by [id] ("CUSTOM_…") and
 * carry their own [name], [type] (a yes/no check or an amount toward [target] in [unit]), and an
 * [icon] key and [color] index the app draws them with.
 */
data class Habit(
    val kind: HabitKind,
    val target: Double = kind.defaultTarget,
    val active: Boolean = true,
    val sortOrder: Int = kind.ordinal,
    val id: String = kind.name,
    val name: String = "",
    val type: HabitType = kind.type,
    val unit: String = kind.unit,
    val icon: String = "",
    val color: Int = 0,
) {
    val isCustom: Boolean get() = kind == HabitKind.CUSTOM

    companion object {
        const val CUSTOM_PREFIX = "CUSTOM_"
        const val NAME_MAX = 40
        const val UNIT_MAX = 12

        fun isCustomId(id: String): Boolean = id.startsWith(CUSTOM_PREFIX)

        /** A new custom habit. Amounts need a positive target; checks always target 1. */
        fun custom(id: String, name: String, type: HabitType, target: Double, unit: String, icon: String, color: Int, sortOrder: Int): Habit {
            require(isCustomId(id)) { "custom habit ids start with $CUSTOM_PREFIX" }
            val t = if (type == HabitType.AMOUNT) target.coerceIn(1.0, 1_000_000.0) else 1.0
            return Habit(
                kind = HabitKind.CUSTOM, target = t, sortOrder = sortOrder, id = id,
                name = name.trim().take(NAME_MAX), type = if (type == HabitType.TIME) HabitType.CHECK else type,
                unit = if (type == HabitType.AMOUNT) unit.trim().take(UNIT_MAX) else "", icon = icon, color = color,
            )
        }
    }
}

/** One day of one habit. [value]: ml, steps, minutes, or minute-of-day for the wake time. */
data class HabitLog(
    val habitId: String,
    val date: LocalDate,
    val value: Double = 0.0,
    val completed: Boolean = false,
    val notes: String = "",
    /** Running, in km, recorded beside steps. */
    val extra: Double = 0.0,
)

enum class StudyCategory { JAVA, DSA, AI_LLM, SYSTEM_DESIGN, INTERVIEW }

data class StudyTask(
    val id: Long = 0,
    val date: LocalDate,
    val title: String,
    val category: StudyCategory,
    val durationMinutes: Int,
    val completed: Boolean = false,
    val notes: String = "",
    val position: Int = 0,
    /** Where the task came from: "plan" (imported) or "manual". Re-imports only replace "plan" rows. */
    val source: String = SOURCE_PLAN,
    /** Minutes actually studied with the session timer. */
    val spentMinutes: Int = 0,
    val startTime: LocalTime? = null,
) {
    companion object {
        const val SOURCE_PLAN = "plan"
        const val SOURCE_MANUAL = "manual"
    }
}

data class FocusSession(
    val id: Long = 0,
    val date: LocalDate,
    val durationSeconds: Int,
    val completed: Boolean,
    val interruptions: Int = 0,
    val task: String = "",
    /** "focus" for Deep Work, "study" when started from the study plan. */
    val kind: String = KIND_FOCUS,
    val startedAt: Instant = Instant.EPOCH,
    /** The study task this session was spent on; its minutes are then kept on the task itself. */
    val studyTaskId: Long? = null,
) {
    val minutes: Int get() = durationSeconds / 60

    companion object {
        const val KIND_FOCUS = "focus"
        const val KIND_STUDY = "study"
    }
}

data class SpeakingSession(
    val id: Long = 0,
    val date: LocalDate,
    val topic: String,
    val durationSeconds: Int = 0,
    val completed: Boolean = false,
    val rating: Int? = null,
)

/** Everything about one day that the habit rules read. Reading minutes come from the Read section. */
data class DayInputs(
    val date: LocalDate,
    val logs: Map<String, HabitLog> = emptyMap(),
    val readingMinutes: Int = 0,
    val readingPages: Int = 0,
    val studyTasksDone: Int = 0,
    val studyTasksTotal: Int = 0,
    val studyMinutes: Int = 0,
    val focusMinutes: Int = 0,
    val focusSessions: Int = 0,
    val interruptions: Int = 0,
    val spoke: Boolean = false,
)

/** One habit's state on one day, ready to draw: [progress] is 0..1. [habit] says how to draw it. */
data class HabitDay(
    val kind: HabitKind,
    val value: Double,
    val target: Double,
    val done: Boolean,
    val logged: Boolean,
    val habit: Habit = Habit(kind),
) {
    val habitId: String get() = habit.id
    val type: HabitType get() = habit.type

    val progress: Float get() = when {
        done -> 1f
        target <= 0 -> 0f
        type == HabitType.TIME -> 0f
        else -> (value / target).toFloat().coerceIn(0f, 1f)
    }
}

data class DailySummary(
    val date: LocalDate,
    val completedHabits: Int,
    val totalHabits: Int,
    val habits: List<HabitDay>,
) {
    val completionPercentage: Int get() = if (totalHabits == 0) 0 else (completedHabits * 100 / totalHabits)
    val fraction: Float get() = if (totalHabits == 0) 0f else completedHabits.toFloat() / totalHabits
}

enum class DayState { COMPLETE, PARTIAL, MISSED, PAUSED, TODAY, FUTURE, OUTSIDE }

object HabitRules {
    /** Wake before 5:00 counts. */
    const val WAKE_LIMIT_MINUTE = 5 * 60

    /** A day is "complete" at this share of habits or more (8 of 10). Shown in the app beside the calendar. */
    const val COMPLETE_SHARE = 0.8

    fun evaluate(habit: Habit, day: DayInputs): HabitDay {
        val log = day.logs[habit.id]
        val target = habit.target
        return when (habit.kind) {
            HabitKind.WATER, HabitKind.STEPS -> {
                val v = log?.value ?: 0.0
                HabitDay(habit.kind, v, target, v >= target, v > 0, habit)
            }
            HabitKind.READING -> {
                val v = day.readingMinutes.toDouble()
                HabitDay(habit.kind, v, target, v >= target || log?.completed == true, v > 0 || day.readingPages > 0, habit)
            }
            HabitKind.STUDY -> {
                // With a plan for the day, finishing every task is the goal. Without one, study time is.
                if (day.studyTasksTotal > 0) {
                    HabitDay(habit.kind, day.studyTasksDone.toDouble(), day.studyTasksTotal.toDouble(), day.studyTasksDone >= day.studyTasksTotal, day.studyTasksDone > 0 || day.studyMinutes > 0, habit)
                } else {
                    val v = day.studyMinutes.toDouble()
                    HabitDay(habit.kind, v, target, v >= target || log?.completed == true, v > 0, habit)
                }
            }
            HabitKind.DEEP_WORK -> {
                val v = day.focusMinutes.toDouble()
                HabitDay(habit.kind, v, target, v >= target || log?.completed == true, day.focusSessions > 0, habit)
            }
            HabitKind.WAKE_EARLY -> {
                val minute = log?.value
                val logged = log != null && log.value >= 0 && log.notes != CLEARED
                HabitDay(habit.kind, minute ?: -1.0, target, logged && minute!! < target, logged, habit)
            }
            HabitKind.SPEAK -> HabitDay(habit.kind, if (day.spoke) 1.0 else 0.0, 1.0, day.spoke, day.spoke, habit)
            HabitKind.CUSTOM -> if (habit.type == HabitType.AMOUNT) {
                val v = log?.value ?: 0.0
                HabitDay(habit.kind, v, target, v >= target || log?.completed == true, v > 0 || log?.completed == true, habit)
            } else {
                val done = log?.completed == true
                HabitDay(habit.kind, if (done) 1.0 else 0.0, 1.0, done, log != null, habit)
            }
            HabitKind.ZERO_SUGAR, HabitKind.COLD_SHOWER, HabitKind.DIGITAL_DETOX -> {
                val done = log?.completed == true
                HabitDay(habit.kind, if (done) 1.0 else 0.0, 1.0, done, log != null, habit)
            }
        }
    }

    fun summarize(habits: List<Habit>, day: DayInputs): DailySummary {
        val active = habits.filter { it.active }.sortedBy { it.sortOrder }
        val days = active.map { evaluate(it, day) }
        return DailySummary(day.date, days.count { it.done }, days.size, days)
    }

    /** How many habits make a complete day: 8 of 10, 4 of 5, 1 of 1. */
    fun completeThreshold(total: Int): Int = if (total <= 0) 0 else kotlin.math.ceil(total * COMPLETE_SHARE - 1e-9).toInt().coerceAtLeast(1)

    fun isComplete(s: DailySummary): Boolean = s.totalHabits > 0 && s.completedHabits >= completeThreshold(s.totalHabits)

    fun stateOf(arc: WinterArc?, date: LocalDate, today: LocalDate, summary: DailySummary?): DayState = when {
        arc == null || date.isBefore(arc.startDate) || date.isAfter(arc.endDate(today)) -> DayState.OUTSIDE
        arc.isPausedOn(date, today) -> DayState.PAUSED
        date.isAfter(today) -> DayState.FUTURE
        summary != null && isComplete(summary) -> DayState.COMPLETE
        date == today -> DayState.TODAY
        summary != null && summary.completedHabits > 0 -> DayState.PARTIAL
        else -> DayState.MISSED
    }

    /** Marker for a wake time that was set and then removed. */
    const val CLEARED = "cleared"
}

data class Streaks(val current: Int, val longest: Int)

object ArcStats {
    /**
     * Streaks of complete days over counting dates (pauses are skipped, not broken). Today only
     * extends the current streak once it is complete; an unfinished today never breaks it.
     */
    fun streaks(dates: List<LocalDate>, complete: (LocalDate) -> Boolean, today: LocalDate): Streaks {
        var longest = 0
        var run = 0
        for (d in dates) {
            if (complete(d)) { run++; longest = maxOf(longest, run) } else if (d != today) run = 0
        }
        // The current streak is the run that reaches the latest counting day (or yesterday, if today is still open).
        var current = 0
        for (d in dates.asReversed()) {
            if (complete(d)) current++ else if (d == today) continue else break
        }
        return Streaks(current, longest)
    }

    /** Consecutive days, ending today or yesterday, on which [done] held. */
    fun habitStreak(dates: List<LocalDate>, done: (LocalDate) -> Boolean, today: LocalDate): Int {
        var n = 0
        for (d in dates.asReversed()) {
            if (done(d)) n++ else if (d == today) continue else break
        }
        return n
    }
}

/**
 * Picks today's speaking topic. No topic repeats until every topic in the pool has been used;
 * then the cycle starts again, still avoiding the most recent ones.
 */
object TopicPicker {
    fun pick(pool: List<String>, used: List<String>, random: kotlin.random.Random, avoid: String? = null): String? {
        if (pool.isEmpty()) return null
        val usedSet = used.toSet()
        var fresh = pool.filter { it !in usedSet && it != avoid }
        if (fresh.isEmpty()) {
            // Pool exhausted: allow repeats, but not the last few dozen topics.
            val recent = used.takeLast(minOf(used.size, pool.size / 3)).toSet()
            fresh = pool.filter { it !in recent && it != avoid }
            if (fresh.isEmpty()) fresh = pool.filter { it != avoid }.ifEmpty { pool }
        }
        return fresh[random.nextInt(fresh.size)]
    }
}

/** "4:32 AM" style minute-of-day helpers. */
object WakeTime {
    fun toMinute(t: LocalTime): Int = t.hour * 60 + t.minute
    fun toTime(minute: Int): LocalTime = LocalTime.of((minute / 60).coerceIn(0, 23), (minute % 60).coerceIn(0, 59))
}
