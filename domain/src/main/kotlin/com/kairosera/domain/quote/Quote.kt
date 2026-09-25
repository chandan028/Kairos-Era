package com.kairosera.domain.quote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.MonthDay

@Serializable
data class DailyQuote(
    val dayOfYear: Int,
    val quote: String,
    val category: String,
    val actionPrompt: String,
)

@Serializable
enum class QuoteCategory {
    @SerialName("discipline") DISCIPLINE,
    @SerialName("consistency") CONSISTENCY,
    @SerialName("learning") LEARNING,
    @SerialName("courage") COURAGE,
    @SerialName("focus") FOCUS,
    @SerialName("patience") PATIENCE,
    @SerialName("failure") FAILURE,
    @SerialName("work") WORK,
    @SerialName("health") HEALTH,
    @SerialName("reading") READING,
    @SerialName("self-respect") SELF_RESPECT,
    @SerialName("time") TIME,
    @SerialName("growth") GROWTH,
    @SerialName("reflection") REFLECTION,
    @SerialName("beginnings") BEGINNINGS,
    @SerialName("finishing") FINISHING,
    @SerialName("resilience") RESILIENCE,
    ;

    val key: String get() = name.lowercase().replace('_', '-')
}

/**
 * The quote for a date is chosen by calendar day, not at random, so reopening the app
 * shows the same quote all day.
 *
 * Days are counted on a non-leap calendar, so every month/day always gets the same quote
 * in every year (23 September is always #266). 29 February shares 28 February's quote.
 */
object QuoteSelector {
    const val QUOTE_COUNT = 365
    private const val REFERENCE_NON_LEAP_YEAR = 2025

    fun dayIndexFor(date: LocalDate): Int {
        val md = MonthDay.from(date)
        val safe = if (md == MonthDay.of(2, 29)) MonthDay.of(2, 28) else md
        return safe.atYear(REFERENCE_NON_LEAP_YEAR).dayOfYear
    }

    fun select(quotes: List<DailyQuote>, date: LocalDate): DailyQuote? {
        if (quotes.isEmpty()) return null
        val index = dayIndexFor(date)
        return quotes.firstOrNull { it.dayOfYear == index } ?: quotes[(index - 1) % quotes.size]
    }
}
