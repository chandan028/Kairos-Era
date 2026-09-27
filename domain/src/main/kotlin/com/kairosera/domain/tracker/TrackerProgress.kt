package com.kairosera.domain.tracker

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** How far one day's entry got, from 0.0 to 1.0, plus whether it counts as "done". */
data class DayScore(val fraction: Double, val done: Boolean)

object TrackerScoring {
    const val MAX_RATING = 5.0

    /** Progress of one field for one day. Null when the field does not count or has nothing to measure. */
    fun fieldFraction(field: TrackerField, value: FieldValue?): Double? {
        if (!field.type.countsTowardProgress) return null
        return when (field.type) {
            MeasurementType.CHECKBOX -> if ((value?.number ?: 0.0) > 0.0) 1.0 else 0.0
            MeasurementType.CHECKLIST -> if (field.options.isEmpty()) null else (value?.checked?.count { it in field.options.indices } ?: 0).toDouble() / field.options.size
            MeasurementType.RATING -> value?.number?.let { if (field.target != null) ratio(it, field.target) else if (it > 0) 1.0 else 0.0 } ?: 0.0
            MeasurementType.PERCENTAGE -> ratio(value?.number ?: 0.0, field.target ?: 100.0)
            else -> {
                val n = value?.number ?: 0.0
                val target = field.target
                if (target != null && target > 0) ratio(n, target) else if (n > 0) 1.0 else 0.0
            }
        }
    }

    /**
     * A day's score is the average of its counting fields. It is "done" when every counting field
     * is complete, or, when the tracker has several fields, when the first counting field (the main
     * goal) is complete. Missing optional fields never make a real effort count as failure.
     */
    fun score(tracker: Tracker, entry: TrackerEntry?): DayScore {
        val counting = tracker.enabledFields.mapNotNull { f -> fieldFraction(f, entry?.values?.get(f.id))?.let { f to it } }
        if (counting.isEmpty()) {
            val anything = entry?.values?.values?.any { !it.text.isNullOrBlank() || (it.number ?: 0.0) != 0.0 || it.checked.isNotEmpty() } == true
            return DayScore(if (anything) 1.0 else 0.0, anything)
        }
        val avg = counting.sumOf { it.second } / counting.size
        val done = counting.all { it.second >= 1.0 } || counting.first().second >= 1.0
        return DayScore(avg.coerceIn(0.0, 1.0), done)
    }

    private fun ratio(v: Double, target: Double) = if (target <= 0) 0.0 else (v / target).coerceIn(0.0, 1.0)
}

/** Reflection-first statistics. No penalties, no guilt: a miss only ends a streak, it never erases history. */
data class TrackerStats(
    val currentStreak: Int,
    val bestStreak: Int,
    /** Share of due periods in the window that were completed, 0.0 - 1.0. */
    val completionRate: Double,
    val activeDays: Int,
    /** True when yesterday was due and not done; used for a gentle "missed yesterday" note. */
    val missedYesterday: Boolean,
    /** "day", "week" or "month": the unit the streak counts in. */
    val unit: StreakUnit,
)

enum class StreakUnit { DAY, WEEK, MONTH }

object TrackerStatsCalculator {

    /**
     * @param doneDates dates whose score was "done"
     * @param activeDates dates with any entry at all
     * @param today the current local date; today not yet done never breaks a streak
     */
    fun compute(
        frequency: TrackerFrequency,
        startDate: LocalDate,
        doneDates: Set<LocalDate>,
        activeDates: Set<LocalDate>,
        today: LocalDate,
        windowDays: Long = 30,
    ): TrackerStats {
        val windowStart = maxOf(startDate, today.minusDays(windowDays - 1))
        return when (frequency) {
            TrackerFrequency.Daily -> dayBased(DayOfWeek.entries.toSet(), startDate, doneDates, activeDates, today, windowStart)
            is TrackerFrequency.SelectedDays -> dayBased(frequency.days.ifEmpty { DayOfWeek.entries.toSet() }, startDate, doneDates, activeDates, today, windowStart)
            is TrackerFrequency.TimesPerWeek -> periodBased(
                StreakUnit.WEEK, frequency.times, startDate, doneDates, activeDates, today, windowStart,
                periodStart = { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) },
                next = { it.plusWeeks(1) },
            )
            is TrackerFrequency.TimesPerMonth -> periodBased(
                StreakUnit.MONTH, frequency.times, startDate, doneDates, activeDates, today, windowStart,
                periodStart = { YearMonth.from(it).atDay(1) },
                next = { it.plusMonths(1) },
            )
        }
    }

    private fun dayBased(
        days: Set<DayOfWeek>,
        start: LocalDate,
        done: Set<LocalDate>,
        active: Set<LocalDate>,
        today: LocalDate,
        windowStart: LocalDate,
    ): TrackerStats {
        fun due(d: LocalDate) = d.dayOfWeek in days && d >= start
        // Current streak: walk back from today (today only counts if done) over due days.
        var current = 0
        var d = today
        if (due(d) && d !in done) d = d.minusDays(1)
        var guard = 0
        while (d >= start && guard++ < MAX_SCAN) {
            if (due(d)) { if (d in done) current++ else break }
            d = d.minusDays(1)
        }
        // Best streak over all history.
        var best = 0
        var run = 0
        d = start
        guard = 0
        while (d <= today && guard++ < MAX_SCAN) {
            if (due(d)) {
                if (d in done) { run++; best = maxOf(best, run) } else if (d != today) run = 0
            }
            d = d.plusDays(1)
        }
        val dueInWindow = generateSequence(windowStart) { it.plusDays(1) }.takeWhile { it <= today }
            .filter { due(it) && (it != today || it in done) }.toList()
        val rate = if (dueInWindow.isEmpty()) 0.0 else dueInWindow.count { it in done }.toDouble() / dueInWindow.size
        val yesterday = today.minusDays(1)
        return TrackerStats(
            currentStreak = current,
            bestStreak = maxOf(best, current),
            completionRate = rate,
            activeDays = active.count { it in windowStart..today },
            missedYesterday = due(yesterday) && yesterday !in done,
            unit = StreakUnit.DAY,
        )
    }

    private fun periodBased(
        unit: StreakUnit,
        times: Int,
        start: LocalDate,
        done: Set<LocalDate>,
        active: Set<LocalDate>,
        today: LocalDate,
        windowStart: LocalDate,
        periodStart: (LocalDate) -> LocalDate,
        next: (LocalDate) -> LocalDate,
    ): TrackerStats {
        val needed = times.coerceAtLeast(1)
        fun complete(p: LocalDate): Boolean {
            val end = next(p)
            return done.count { it >= p && it < end } >= needed
        }
        val currentPeriod = periodStart(today)
        val periods = generateSequence(periodStart(start)) { next(it) }.takeWhile { it <= currentPeriod }.take(MAX_SCAN).toList()
        var best = 0
        var run = 0
        for (p in periods) {
            if (complete(p)) { run++; best = maxOf(best, run) } else if (p != currentPeriod) run = 0
        }
        var current = 0
        for (p in periods.asReversed()) {
            if (complete(p)) current++ else if (p != currentPeriod) break
        }
        val windowPeriods = periods.filter { next(it) > windowStart && (it != currentPeriod || complete(it)) }
        val rate = if (windowPeriods.isEmpty()) 0.0 else windowPeriods.count { complete(it) }.toDouble() / windowPeriods.size
        return TrackerStats(
            currentStreak = current,
            bestStreak = maxOf(best, current),
            completionRate = rate,
            activeDays = active.count { it in windowStart..today },
            missedYesterday = false,
            unit = unit,
        )
    }

    fun isDue(frequency: TrackerFrequency, date: LocalDate): Boolean = when (frequency) {
        TrackerFrequency.Daily, is TrackerFrequency.TimesPerWeek, is TrackerFrequency.TimesPerMonth -> true
        is TrackerFrequency.SelectedDays -> frequency.days.isEmpty() || date.dayOfWeek in frequency.days
    }

    private const val MAX_SCAN = 366 * 20
}
