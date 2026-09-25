package com.kairosera.feature.insights

import com.kairosera.AppContainer
import com.kairosera.domain.activity.ActivityHistory
import com.kairosera.domain.activity.History
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/** Reads one window of history from Room (date-range queries only) and turns it into activity. */
object HistorySource {
    fun observe(c: AppContainer, from: LocalDate, to: LocalDate): Flow<History> {
        val trackerFlow = combine(c.trackers.observeActive(), c.trackers.observeEntries(from, to)) { t, e -> t to e }
        val readingFlow = combine(c.books.observeSessionsBetween(from, to), c.books.observeBooks()) { s, b -> s to b }
        return combine(
            c.observeDayPlan(from, to), trackerFlow, c.study.observeTargetsBetween(from, to), readingFlow, c.journal.observeBetween(from, to),
        ) { plan, (trackers, entries), targets, (sessions, books), journal ->
            ActivityHistory.build(from, to, plan, trackers, entries, targets, sessions, books, journal)
        }
    }

    /** The first day with anything in [history], or the install day if earlier: where counting starts. */
    fun startedOn(c: AppContainer, history: History): LocalDate {
        val firstData = history.days.firstOrNull { it.occurrences.isNotEmpty() || it.items.isNotEmpty() }?.date
        return listOfNotNull(c.installedOn, firstData).min()
    }
}

/**
 * Today's date in the phone's own time zone. Re-checked every minute, so midnight, a daylight
 * saving change or travelling to another zone moves every screen that uses it to the new day.
 */
fun todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(60_000)
    }
}.distinctUntilChanged()
