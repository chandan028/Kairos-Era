package com.kairosera.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.ui.components.UiState
import com.kairosera.domain.journal.JournalEntry
import com.kairosera.domain.journal.Mood
import com.kairosera.domain.model.Task
import com.kairosera.domain.stats.StatsCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

data class JournalData(
    val today: LocalDate,
    val todayEntry: JournalEntry?,
    /** Entries matching the search, newest first, grouped by month. */
    val months: List<Pair<YearMonth, List<JournalEntry>>>,
    val total: Int,
    val streak: Int,
    val thisMonth: Int,
    val query: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(private val c: AppContainer) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val _query = MutableStateFlow("")

    val state: StateFlow<UiState<JournalData>> = retry.flatMapLatest {
        combine(c.journal.observeEntries(), _query) { entries, q ->
            val today = LocalDate.now()
            val visible = entries.filterNot { it.isBlank }
            val matches = if (q.isBlank()) visible else visible.filter { it.matches(q.trim()) }
            UiState.Ready(
                JournalData(
                    today = today,
                    todayEntry = entries.firstOrNull { it.date == today },
                    months = matches.groupBy { YearMonth.from(it.date) }.toList(),
                    total = visible.size,
                    streak = StatsCalculator.currentStreak(visible.map { it.date }.toSet(), today),
                    thisMonth = visible.count { YearMonth.from(it.date) == YearMonth.from(today) },
                    query = q,
                ),
            ) as UiState<JournalData>
        }.catch { emit(UiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun search(q: String) { _query.value = q.take(100) }
    fun retry() { retry.value++ }

    private fun JournalEntry.matches(q: String) =
        listOf(text, win, lesson, gratitude, tomorrow).any { it.contains(q, ignoreCase = true) }
}

data class JournalForm(
    val mood: Mood? = null,
    val text: String = "",
    val win: String = "",
    val lesson: String = "",
    val gratitude: String = "",
    val tomorrow: String = "",
    /** Turn [tomorrow] into a task for the next day when saving. Not stored with the entry. */
    val planTomorrow: Boolean = false,
)

private fun JournalEntry.toForm() = JournalForm(mood, text, win, lesson, gratitude, tomorrow)

/**
 * Edits the reflection for [date] (or the entry [entryId]). Opening a day that already has an
 * entry continues it, so a day never ends up with two half reflections.
 */
class JournalEditorViewModel(private val c: AppContainer, private val entryId: Long?, date: LocalDate?, presetMood: Mood?) : ViewModel() {
    private var original: JournalEntry? = null
    private val _date = MutableStateFlow(date ?: LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()
    private val _form = MutableStateFlow(JournalForm(mood = presetMood))
    val form: StateFlow<JournalForm> = _form.asStateFlow()
    private var loadedForm = JournalForm()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _done = MutableStateFlow<Outcome?>(null)
    val done: StateFlow<Outcome?> = _done.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    enum class Outcome { SAVED, SAVED_WITH_TASK, TRASHED }

    val isExisting: Boolean get() = original != null
    val hasChanges: Boolean get() = _form.value.copy(planTomorrow = false) != loadedForm

    init {
        viewModelScope.launch {
            val existing = runCatching { if (entryId != null) c.journal.get(entryId) else c.journal.forDate(_date.value) }.getOrNull()
                ?.takeIf { it.deletedAt == null }
            if (existing != null) {
                original = existing
                _date.value = existing.date
                loadedForm = existing.toForm()
                _form.value = loadedForm.copy(mood = presetMood ?: existing.mood)
            }
            _ready.value = true
        }
    }

    fun edit(change: (JournalForm) -> JournalForm) = _form.update(change)

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            val now = Instant.now(c.clock)
            val d = _date.value
            val entry = (original ?: JournalEntry(date = d, createdAt = now)).copy(
                mood = f.mood, text = f.text.trimEnd(), win = f.win.trim(), lesson = f.lesson.trim(),
                gratitude = f.gratitude.trim(), tomorrow = f.tomorrow.trim(), updatedAt = now,
            )
            runCatching {
                if (entry.isBlank && original == null) return@runCatching Outcome.SAVED
                c.journal.save(entry)
                val task = entry.tomorrow.takeIf { f.planTomorrow && it.isNotBlank() }
                if (task != null) {
                    c.saveTask(Task(title = task.take(200), date = d.plusDays(1), createdAt = now, updatedAt = now))
                    Outcome.SAVED_WITH_TASK
                } else {
                    Outcome.SAVED
                }
            }.onSuccess { _done.value = it }
                .onFailure { SafeLog.error("journal_save_failed", it); _failed.value = true }
        }
    }

    fun moveToTrash() {
        val id = original?.id ?: return
        viewModelScope.launch {
            runCatching { c.journal.moveToTrash(id, Instant.now(c.clock)) }
                .onSuccess { _done.value = Outcome.TRASHED }
                .onFailure { SafeLog.error("journal_trash_failed", it); _failed.value = true }
        }
    }

    fun restore() {
        val id = original?.id ?: return
        c.appScope.launch { runCatching { c.journal.restore(id, Instant.now(c.clock)) } }
    }

    fun dismissFailure() { _failed.value = false }
}
