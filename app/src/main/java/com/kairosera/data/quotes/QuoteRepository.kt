package com.kairosera.data.quotes

import android.content.Context
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.domain.quote.DailyQuote
import com.kairosera.domain.quote.QuoteSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Reads the bundled 365 quotes once. A missing or broken file degrades to a single built-in line. */
class QuoteRepository(private val context: Context) {
    private val mutex = Mutex()
    private var cache: List<DailyQuote>? = null
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun all(): List<DailyQuote> = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(PATH).bufferedReader().use { json.decodeFromString<List<DailyQuote>>(it.readText()) }
            }.onFailure { SafeLog.error("quotes_load_failed", it) }.getOrDefault(emptyList())
        }.also { cache = it }
    }

    suspend fun forDate(date: LocalDate): DailyQuote =
        QuoteSelector.select(all(), date) ?: FALLBACK.copy(dayOfYear = QuoteSelector.dayIndexFor(date))

    companion object {
        const val PATH = "motivation/quotes.json"
        val FALLBACK = DailyQuote(
            dayOfYear = 0,
            quote = "Do the small thing today that makes tomorrow easier.",
            category = "consistency",
            actionPrompt = "Pick one small task and finish it.",
        )
    }
}
