package com.kairosera.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Recurrence rule, stored separately from the occurrences it generates.
 *
 * - WEEKLY with empty [weekdays] repeats on the start date's weekday.
 * - MONTHLY repeats on the start date's day of month, clamped to the month's last day (31st -> 30th/28th).
 * - YEARLY repeats on the start date's month/day; Feb 29 falls back to Feb 28 in non-leap years.
 * - [endDate] and [occurrenceCount] are both optional limits; the earlier one wins.
 */
data class RepeatRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val endDate: LocalDate? = null,
    val occurrenceCount: Int? = null,
) {
    init {
        require(interval in 1..MAX_INTERVAL) { "interval out of range" }
        require(occurrenceCount == null || occurrenceCount in 1..MAX_COUNT) { "occurrenceCount out of range" }
    }

    companion object {
        const val MAX_INTERVAL = 365
        const val MAX_COUNT = 5000
    }
}
