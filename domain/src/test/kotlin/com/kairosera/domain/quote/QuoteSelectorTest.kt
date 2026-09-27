package com.kairosera.domain.quote

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class QuoteSelectorTest {
    @Test fun september23IsQuote266() {
        assertEquals(266, QuoteSelector.dayIndexFor(LocalDate.of(2026, 9, 23)))
    }

    @Test fun sameCalendarDayGetsSameQuoteInLeapYears() {
        assertEquals(QuoteSelector.dayIndexFor(LocalDate.of(2027, 12, 31)), QuoteSelector.dayIndexFor(LocalDate.of(2028, 12, 31)))
        assertEquals(365, QuoteSelector.dayIndexFor(LocalDate.of(2028, 12, 31)))
        assertEquals(59, QuoteSelector.dayIndexFor(LocalDate.of(2028, 2, 29)))
        assertEquals(60, QuoteSelector.dayIndexFor(LocalDate.of(2028, 3, 1)))
    }

    @Test fun selectionIsStableForTheWholeDay() {
        val quotes = (1..365).map { DailyQuote(it, "q$it", "focus", "a$it") }
        val date = LocalDate.of(2026, 9, 23)
        assertEquals("q266", QuoteSelector.select(quotes, date)?.quote)
        assertEquals(QuoteSelector.select(quotes, date), QuoteSelector.select(quotes, date))
    }

    @Test fun validatorFlagsProblems() {
        val quotes = (1..364).map { DailyQuote(it, "Quote number $it is here.", "focus", "Act $it.") } +
            DailyQuote(364, "No excuses, beast mode", "hype", "")
        val problems = QuoteValidator.validate(quotes)
        assert(problems.any { "missing days" in it })
        assert(problems.any { "duplicate days" in it })
        assert(problems.any { "banned phrase" in it })
        assert(problems.any { "unknown category" in it })
        assert(problems.any { "empty action" in it })
    }
}
