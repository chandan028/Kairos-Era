package com.kairosera.domain.stats

import com.kairosera.domain.journal.Mood
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The ranges people can look back over, each ending today. */
enum class StatsRange(val days: Int) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),
    YEAR(365),
}

/**
 * What happened on one day, reduced to counts. It carries no titles or text, so it is safe to
 * compute and hold in memory for a whole year.
 */
data class DayActivity(
    val date: LocalDate,
    val tasksDone: Int = 0,
    val tasksTotal: Int = 0,
    val trackersDue: Int = 0,
    /** Sum of the day scores (0..1 each) of the trackers due that day. */
    val trackerProgress: Double = 0.0,
    /** Tracker entries with something logged, due or not (study, fitness and habits included). */
    val trackersLogged: Int = 0,
    val studySessions: Int = 0,
    /** Study topics marked done that day. */
    val studyTopics: Int = 0,
    val studyMinutes: Int = 0,
    val readingSessions: Int = 0,
    val pages: Int = 0,
    val readingMinutes: Int = 0,
    val workouts: Int = 0,
    val activeMinutes: Int = 0,
    val habitsDone: Int = 0,
    /** The longest single study or reading session, in minutes. */
    val longestSession: Int = 0,
    val journaled: Boolean = false,
    val mood: Mood? = null,
) {
    val planned: Int get() = tasksTotal + trackersDue

    /** Share of the day's plan that got done, or null when nothing was planned (an empty day is not a failure). */
    val planScore: Double? get() = if (planned == 0) null else ((tasksDone + trackerProgress) / planned).coerceIn(0.0, 1.0)

    /** Meaningful things done or written. Planning alone is not an action. */
    val actions: Int get() = tasksDone + trackersLogged + studyTopics + readingSessions + if (journaled) 1 else 0

    /** "Showed up": at least one meaningful action. */
    val isActive: Boolean get() = actions > 0

    /** 0 = nothing, 1 = a little (1-2 actions), 2 = steady (3-5), 3 = a full day (6+). Fixed steps, so it never flatters. */
    val level: Int get() = when (actions) {
        0 -> 0
        in 1..2 -> 1
        in 3..5 -> 2
        else -> 3
    }

    val studied: Boolean get() = studySessions > 0 || studyTopics > 0
}

/** A personal best and the day it happened (for a week record, the Monday of that week). */
data class Record(val value: Int, val date: LocalDate)

data class PersonalRecords(
    val longestStreak: Record?,
    val mostTasksInDay: Record?,
    val mostPagesInWeek: Record?,
    val longestSession: Record?,
) {
    val isEmpty: Boolean get() = listOf(longestStreak, mostTasksInDay, mostPagesInWeek, longestSession).all { it == null }
}

data class StatsSummary(
    val from: LocalDate,
    val to: LocalDate,
    /** Days in the range since the person started, up to today. Consistency is measured against these only. */
    val daysCounted: Int,
    val activeDays: Int,
    val tasksDone: Int,
    val tasksPlanned: Int,
    val studySessions: Int,
    val studyTopics: Int,
    val studyMinutes: Int,
    val readingSessions: Int,
    val readingDays: Int,
    val pages: Int,
    val readingMinutes: Int,
    val workouts: Int,
    val activeMinutes: Int,
    val reflections: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    /** Average mood on a 1..5 scale over days with a mood; null when none was recorded. */
    val averageMood: Double?,
    val moodDays: Int,
) {
    /** Active days over counted days. Null before there is a single day to count. */
    val consistency: Double? get() = if (daysCounted == 0) null else activeDays.toDouble() / daysCounted
    val hasActivity: Boolean get() = activeDays > 0
}

/** A plain reading of the last two weeks, from fixed rules. Never a guess about the person. */
sealed interface Momentum {
    /** Fewer than two full weeks of history yet. */
    data class JustStarted(val activeDays: Int, val days: Int) : Momentum
    data class Rising(val lastWeek: Int, val weekBefore: Int) : Momentum
    data class Steady(val lastWeek: Int) : Momentum
    data class Easing(val lastWeek: Int) : Momentum
    data object Quiet : Momentum
}

object StatsCalculator {
    /**
     * Summarises [days] (any order) between [from] and [today]. Days before [startedOn] (the
     * first day the person used the app) and days after today are left out, so a new user is not
     * measured against weeks before they arrived and a future plan never lowers the numbers.
     */
    fun summarize(days: List<DayActivity>, from: LocalDate, today: LocalDate, startedOn: LocalDate? = null): StatsSummary {
        val start = maxOf(from, startedOn ?: from)
        val window = days.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }.sortedBy { it.date }
        val active = window.filter { it.isActive }.map { it.date }.toSet()
        val moods = window.mapNotNull { it.mood?.score }
        val counted = if (start.isAfter(today)) 0 else (ChronoUnit.DAYS.between(start, today) + 1).toInt()
        return StatsSummary(
            from = from,
            to = today,
            daysCounted = counted,
            activeDays = active.size,
            tasksDone = window.sumOf { it.tasksDone },
            tasksPlanned = window.sumOf { it.tasksTotal },
            studySessions = window.sumOf { it.studySessions },
            studyTopics = window.sumOf { it.studyTopics },
            studyMinutes = window.sumOf { it.studyMinutes },
            readingSessions = window.sumOf { it.readingSessions },
            readingDays = window.count { it.readingSessions > 0 },
            pages = window.sumOf { it.pages },
            readingMinutes = window.sumOf { it.readingMinutes },
            workouts = window.sumOf { it.workouts },
            activeMinutes = window.sumOf { it.activeMinutes },
            reflections = window.count { it.journaled },
            currentStreak = currentStreak(active, today),
            bestStreak = longestRun(start, today, active)?.value ?: 0,
            averageMood = if (moods.isEmpty()) null else moods.average(),
            moodDays = moods.size,
        )
    }

    /** Active days in a row ending today, or yesterday, so the morning never reads as a broken streak. */
    fun currentStreak(active: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in active) today else today.minusDays(1)
        var n = 0
        while (d in active) { n++; d = d.minusDays(1) }
        return n
    }

    fun records(days: List<DayActivity>, from: LocalDate, today: LocalDate): PersonalRecords {
        val window = days.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        val active = window.filter { it.isActive }.map { it.date }.toSet()
        val weeks = window.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .map { (monday, list) -> Record(list.sumOf { it.pages }, monday) }
        return PersonalRecords(
            longestStreak = longestRun(from, today, active)?.takeIf { it.value >= 2 },
            mostTasksInDay = window.maxWithOrNull(compareBy<DayActivity> { it.tasksDone }.thenBy { it.date })?.takeIf { it.tasksDone > 0 }?.let { Record(it.tasksDone, it.date) },
            mostPagesInWeek = weeks.maxWithOrNull(compareBy<Record> { it.value }.thenBy { it.date })?.takeIf { it.value > 0 },
            longestSession = window.maxWithOrNull(compareBy<DayActivity> { it.longestSession }.thenBy { it.date })?.takeIf { it.longestSession > 0 }?.let { Record(it.longestSession, it.date) },
        )
    }

    /** Compares the last 7 days with the 7 before them. [startedOn] keeps a new user out of "the week before". */
    fun momentum(days: List<DayActivity>, today: LocalDate, startedOn: LocalDate?): Momentum {
        val active = days.filter { it.isActive && !it.date.isAfter(today) }.map { it.date }.toSet()
        fun count(fromAgo: Long, toAgo: Long) = (toAgo downTo fromAgo).count { today.minusDays(it) in active }
        val lastWeek = count(0, 6)
        val weekBefore = count(7, 13)
        val start = startedOn ?: days.minOfOrNull { it.date } ?: today
        val known = (ChronoUnit.DAYS.between(start, today) + 1).coerceAtLeast(1)
        if (known < 14) {
            val d = known.toInt().coerceAtMost(14)
            val n = (0 until d).count { today.minusDays(it.toLong()) in active }
            return if (n == 0) Momentum.Quiet else Momentum.JustStarted(n, d)
        }
        return when {
            lastWeek == 0 -> Momentum.Quiet
            lastWeek > weekBefore -> Momentum.Rising(lastWeek, weekBefore)
            lastWeek == weekBefore || lastWeek >= 5 -> Momentum.Steady(lastWeek)
            else -> Momentum.Easing(lastWeek)
        }
    }

    private fun longestRun(from: LocalDate, to: LocalDate, active: Set<LocalDate>): Record? {
        var best: Record? = null
        var run = 0
        var d = from
        while (!d.isAfter(to)) {
            run = if (d in active) run + 1 else 0
            if (run > 0 && run >= (best?.value ?: 0)) best = Record(run, d.minusDays(run - 1L))
            d = d.plusDays(1)
        }
        return best
    }
}
