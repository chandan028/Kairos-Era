package com.kairosera.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.ui.components.UiState
import com.kairosera.domain.model.TaskOccurrence
import com.kairosera.domain.quote.DailyQuote
import com.kairosera.domain.time.DayPart
import com.kairosera.domain.time.Greeting
import com.kairosera.domain.usecase.DayProgress
import com.kairosera.domain.usecase.DaySummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeData(
    val date: LocalDate,
    val dayPart: DayPart,
    val progress: DayProgress,
    val nextUp: List<TaskOccurrence>,
    val quote: DailyQuote,
    val hasSamples: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val retry = MutableStateFlow(0)

    /** Ticks once a minute while Home is visible, so the greeting and "next up" follow the clock and midnight. */
    private val now = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MS)
        }
    }

    val state: StateFlow<UiState<HomeData>> = retry.flatMapLatest {
        now.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { date ->
            val quoteFlow = flow { emit(c.quotes.forDate(date)) }
            combine(c.observeDayPlan(date), now, quoteFlow) { plan, time, quote ->
                val today = plan[date].orEmpty()
                UiState.Ready(
                    HomeData(
                        date = date,
                        dayPart = Greeting.partOf(time.toLocalTime()),
                        progress = DaySummary.progress(today),
                        nextUp = DaySummary.nextUp(today, time.toLocalTime()),
                        quote = quote,
                        hasSamples = today.any { it.task.isSample },
                    ),
                ) as UiState<HomeData>
            }
        }.catch { emit(UiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), UiState.Loading)

    fun retry() { retry.value++ }

    fun setDone(occurrence: TaskOccurrence, done: Boolean) {
        viewModelScope.launch { runCatching { c.setOccurrenceDone(occurrence.task.id, occurrence.date, done) } }
    }

    fun toggleFavorite(day: Int) {
        viewModelScope.launch { c.settings.toggleFavoriteQuote(day) }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val STOP_MS = 5_000L
    }
}

