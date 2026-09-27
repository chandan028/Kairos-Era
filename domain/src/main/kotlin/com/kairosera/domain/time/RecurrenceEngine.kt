package com.kairosera.domain.time

import com.kairosera.domain.model.Frequency
import com.kairosera.domain.model.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure recurrence math on local dates. Occurrences are calendar dates, so they are
 * unaffected by time zones and daylight-saving changes; only reminder *instants* are.
 */
object RecurrenceEngine {

    /** Hard stop for any scan so a malformed rule can never loop forever. */
    private const val MAX_SCAN_DAYS = 366L * 12

    /** True if a task starting on [start] with [rule] (null = one-time) occurs on [date]. */
    fun occursOn(start: LocalDate, rule: RepeatRule?, date: LocalDate): Boolean {
        if (rule == null) return date == start
        if (!matchesIgnoringCount(start, rule, date)) return false
        val count = rule.occurrenceCount ?: return true
        return indexOf(start, rule, date) < count
    }

    /** All occurrence dates in [from]..[to] (inclusive), in order. */
    fun occurrencesBetween(start: LocalDate, rule: RepeatRule?, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to < from) return emptyList()
        if (rule == null) return if (start in from..to) listOf(start) else emptyList()
        return occurrences(start, rule)
            .dropWhile { it < from }
            .takeWhile { it <= to }
            .toList()
    }

    /** First occurrence on or after [date], or null if the series has ended. */
    fun nextOnOrAfter(start: LocalDate, rule: RepeatRule?, date: LocalDate): LocalDate? {
        if (rule == null) return start.takeIf { it >= date }
        return occurrences(start, rule).firstOrNull { it >= date }
    }

    /** The last date a series can occur on, if it is bounded. */
    fun lastPossibleDate(start: LocalDate, rule: RepeatRule?): LocalDate? {
        if (rule == null) return start
        val byCount = rule.occurrenceCount?.let { occurrences(start, rule).lastOrNull() }
        return listOfNotNull(rule.endDate, byCount).minOrNull()
    }

    /** Lazily generated occurrence dates, honouring interval, weekdays, end date and count. */
    fun occurrences(start: LocalDate, rule: RepeatRule): Sequence<LocalDate> {
        val raw: Sequence<LocalDate> = when (rule.frequency) {
            Frequency.DAILY -> generateSequence(start) { it.plusDays(rule.interval.toLong()) }
            Frequency.WEEKLY -> weekly(start, rule)
            Frequency.MONTHLY -> generateSequence(0L) { it + rule.interval }
                .map { monthly(start, it) }
            Frequency.YEARLY -> generateSequence(0L) { it + rule.interval }
                .map { yearly(start, it) }
        }
        val scanLimit = start.plusDays(MAX_SCAN_DAYS * rule.interval.coerceAtMost(10))
        var seq = raw.takeWhile { it <= scanLimit }
        rule.endDate?.let { end -> seq = seq.takeWhile { it <= end } }
        rule.occurrenceCount?.let { count -> seq = seq.take(count) }
        return seq
    }

    private fun weekly(start: LocalDate, rule: RepeatRule): Sequence<LocalDate> {
        val days = rule.weekdays.ifEmpty { setOf(start.dayOfWeek) }.sortedBy { it.value }
        val firstWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return generateSequence(firstWeek) { it.plusWeeks(rule.interval.toLong()) }
            .flatMap { weekStart -> days.asSequence().map { weekStart.plusDays((it.value - 1).toLong()) } }
            .filter { it >= start }
    }

    private fun monthly(start: LocalDate, monthsAhead: Long): LocalDate {
        val ym = YearMonth.from(start).plusMonths(monthsAhead)
        return ym.atDay(minOf(start.dayOfMonth, ym.lengthOfMonth()))
    }

    private fun yearly(start: LocalDate, yearsAhead: Long): LocalDate {
        val ym = YearMonth.of(start.year + yearsAhead.toInt(), start.month)
        return ym.atDay(minOf(start.dayOfMonth, ym.lengthOfMonth()))
    }

    private fun matchesIgnoringCount(start: LocalDate, rule: RepeatRule, date: LocalDate): Boolean {
        if (date < start) return false
        if (rule.endDate != null && date > rule.endDate) return false
        val interval = rule.interval.toLong()
        return when (rule.frequency) {
            Frequency.DAILY -> ChronoUnit.DAYS.between(start, date) % interval == 0L
            Frequency.WEEKLY -> {
                val days = rule.weekdays.ifEmpty { setOf(start.dayOfWeek) }
                val weeks = ChronoUnit.WEEKS.between(
                    start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                )
                date.dayOfWeek in days && weeks % interval == 0L
            }
            Frequency.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(date))
                months % interval == 0L && date == monthly(start, months)
            }
            Frequency.YEARLY -> {
                val years = (date.year - start.year).toLong()
                years % interval == 0L && date == yearly(start, years)
            }
        }
    }

    private fun indexOf(start: LocalDate, rule: RepeatRule, date: LocalDate): Int =
        occurrences(start, rule.copy(occurrenceCount = null)).takeWhile { it < date }.count()
}
