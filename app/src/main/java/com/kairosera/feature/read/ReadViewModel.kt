package com.kairosera.feature.read

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.ui.components.UiState
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.reading.ReadingSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ReadingTotals(val pagesToday: Int, val minutesToday: Int = 0, val pagesWeek: Int, val minutesWeek: Int, val finishedThisYear: Int, val readingDaysWeek: Int)

data class ReadData(val today: LocalDate, val books: List<Book>, val totals: ReadingTotals) {
    fun byStatus(status: BookStatus) = books.filter { it.status == status }
}

object ReadingTotalsCalculator {
    fun compute(books: List<Book>, sessions: List<ReadingSession>, today: LocalDate): ReadingTotals {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = sessions.filter { it.date in weekStart..today }
        return ReadingTotals(
            pagesToday = sessions.filter { it.date == today }.sumOf { it.pages },
            minutesToday = sessions.filter { it.date == today }.sumOf { it.minutes },
            pagesWeek = week.sumOf { it.pages },
            minutesWeek = week.sumOf { it.minutes },
            finishedThisYear = books.count { it.status == BookStatus.FINISHED && it.finishedDate?.year == today.year },
            readingDaysWeek = week.map { it.date }.distinct().size,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadViewModel(private val c: AppContainer) : ViewModel() {
    private val retry = MutableStateFlow(0)

    val state: StateFlow<UiState<ReadData>> = retry.flatMapLatest {
        val today = LocalDate.now()
        combine(c.books.observeBooks(), c.books.observeSessionsBetween(today.minusDays(7), today)) { books, sessions ->
            UiState.Ready(ReadData(today, books, ReadingTotalsCalculator.compute(books, sessions, today))) as UiState<ReadData>
        }.catch { emit(UiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun retry() { retry.value++ }
}
