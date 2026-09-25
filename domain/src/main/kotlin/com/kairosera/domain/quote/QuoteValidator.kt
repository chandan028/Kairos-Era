package com.kairosera.domain.quote

/**
 * Validates the bundled quote set. Used by unit tests and by `tools/validate_quotes.py`'s
 * Kotlin twin so a bad edit to quotes.json fails the build instead of reaching users.
 */
object QuoteValidator {
    const val MAX_QUOTE_LENGTH = 140
    const val MAX_ACTION_LENGTH = 90

    private val bannedPhrases = listOf(
        "no excuses", "beast mode", "you must", "win every day", "grind", "hustle",
        "never give up", "pain is weakness", "100%",
    )

    /** A trailing "— Name" or "- Some Name" looks like an attribution to a person. */
    private val attribution = Regex("""[-\u2013\u2014]\s*[A-Z][\w.]*(\s+[A-Z][\w.]*){0,2}\s*$""")

    fun validate(quotes: List<DailyQuote>): List<String> {
        val problems = mutableListOf<String>()
        if (quotes.size != QuoteSelector.QUOTE_COUNT) problems += "expected ${QuoteSelector.QUOTE_COUNT} quotes, found ${quotes.size}"
        val days = quotes.map { it.dayOfYear }
        val expected = (1..QuoteSelector.QUOTE_COUNT).toSet()
        (expected - days.toSet()).takeIf { it.isNotEmpty() }?.let { problems += "missing days: ${it.sorted()}" }
        days.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.takeIf { it.isNotEmpty() }
            ?.let { problems += "duplicate days: ${it.sorted()}" }
        val categories = QuoteCategory.entries.map { it.key }.toSet()
        val seen = HashMap<String, Int>()
        for (q in quotes) {
            val tag = "day ${q.dayOfYear}"
            if (q.quote.isBlank()) problems += "$tag: empty quote"
            if (q.actionPrompt.isBlank()) problems += "$tag: empty action prompt"
            if (q.quote.length > MAX_QUOTE_LENGTH) problems += "$tag: quote longer than $MAX_QUOTE_LENGTH"
            if (q.actionPrompt.length > MAX_ACTION_LENGTH) problems += "$tag: action longer than $MAX_ACTION_LENGTH"
            if (q.category !in categories) problems += "$tag: unknown category '${q.category}'"
            val lower = (q.quote + " " + q.actionPrompt).lowercase()
            bannedPhrases.filter { it in lower }.forEach { problems += "$tag: contains banned phrase '$it'" }
            if (attribution.containsMatchIn(q.quote)) {
                problems += "$tag: looks like an attribution; quotes must be original and unattributed"
            }
            val key = normalize(q.quote)
            seen[key]?.let { problems += "$tag: duplicates day $it" }
            seen[key] = q.dayOfYear
        }
        return problems
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
