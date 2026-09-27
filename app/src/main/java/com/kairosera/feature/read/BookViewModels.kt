package com.kairosera.feature.read

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.data.repository.withStatus
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookNote
import com.kairosera.domain.reading.BookNoteKind
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.reading.ReadingSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

sealed interface BookState {
    data object Loading : BookState
    data object Missing : BookState
    data object Error : BookState
    data class Ready(val book: Book) : BookState
}

/** The reflection fields of a book, edited together and saved after a short pause. */
data class Insights(
    val whyStarted: String = "", val expected: String = "", val learned: String = "", val applied: String = "",
    val changed: String = "", val skills: String = "", val keyIdeas: String = "", val finalTakeaway: String = "",
    val recommendAgain: Boolean? = null,
)

class BookDetailViewModel(private val c: AppContainer, val bookId: Long) : ViewModel() {
    val today: LocalDate = LocalDate.now()

    val state: StateFlow<BookState> = c.books.observeBook(bookId)
        .map { b -> if (b == null || b.deletedAt != null) BookState.Missing else BookState.Ready(b) }
        .catch { emit(BookState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookState.Loading)

    val sessions: StateFlow<List<ReadingSession>> = c.books.observeSessions(bookId).catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notes: StateFlow<List<BookNote>> = c.books.observeNotes(bookId).catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _insights = MutableStateFlow<Insights?>(null)
    val insights: StateFlow<Insights?> = _insights.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val b = runCatching { c.books.get(bookId) }.getOrNull() ?: return@launch
            _insights.value = Insights(b.whyStarted, b.expected, b.learned, b.applied, b.changed, b.skills, b.keyIdeas, b.finalTakeaway, b.recommendAgain)
        }
    }

    fun editInsights(change: (Insights) -> Insights) {
        val current = _insights.value ?: return
        _insights.value = change(current)
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(500); persistInsights() }
    }

    fun flush() {
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            c.appScope.launch { persistInsights() }
        }
    }

    private suspend fun persistInsights() {
        val i = _insights.value ?: return
        runCatching {
            val b = c.books.get(bookId) ?: return
            c.books.save(
                b.copy(
                    whyStarted = i.whyStarted, expected = i.expected, learned = i.learned, applied = i.applied,
                    changed = i.changed, skills = i.skills, keyIdeas = i.keyIdeas, finalTakeaway = i.finalTakeaway,
                    recommendAgain = i.recommendAgain, updatedAt = Instant.now(c.clock),
                ),
            )
        }.onFailure { SafeLog.error("book_insights_save_failed", it) }
    }

    fun setStatus(status: BookStatus) {
        viewModelScope.launch {
            flushNow()
            runCatching {
                val b = c.books.get(bookId) ?: return@runCatching
                c.books.save(b.withStatus(status, LocalDate.now(), Instant.now(c.clock)))
            }
        }
    }

    fun logSession(pages: Int, minutes: Int, date: LocalDate) {
        viewModelScope.launch { runCatching { c.books.logSession(bookId, pages, minutes, date, Instant.now(c.clock)) } }
    }

    fun deleteSession(id: Long) { viewModelScope.launch { runCatching { c.books.deleteSession(id) } } }

    fun saveNote(kind: BookNoteKind, text: String, page: Int?, id: Long = 0) {
        val clean = text.trim().take(4000)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching { c.books.saveNote(BookNote(id = id, bookId = bookId, kind = kind, text = clean, page = page, createdAt = Instant.now(c.clock))) }
        }
    }

    fun deleteNote(id: Long) { viewModelScope.launch { runCatching { c.books.deleteNote(id) } } }

    fun moveToTrash(onDone: () -> Unit) {
        viewModelScope.launch {
            flushNow()
            runCatching { c.books.moveToTrash(bookId, Instant.now(c.clock)) }.onSuccess { onDone() }
        }
    }

    private suspend fun flushNow() {
        if (saveJob?.isActive == true) { saveJob?.cancel(); persistInsights() }
    }

    override fun onCleared() { flush(); super.onCleared() }
}

data class BookForm(
    val title: String = "",
    val author: String = "",
    val totalPages: String = "",
    val currentPage: String = "",
    val status: BookStatus = BookStatus.WANT_TO_READ,
    val targetDate: LocalDate? = null,
    val coverColor: Long = 0xFF2E4A7D,
)

enum class BookFormError { TITLE_EMPTY, PAGE_BEYOND_TOTAL }

class BookEditorViewModel(private val c: AppContainer, private val bookId: Long?) : ViewModel() {
    val isNew = bookId == null
    private var original: Book? = null
    private val _form = MutableStateFlow(BookForm())
    val form: StateFlow<BookForm> = _form.asStateFlow()
    private val _ready = MutableStateFlow(bookId == null)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _saved = MutableStateFlow<Long?>(null)
    val saved: StateFlow<Long?> = _saved.asStateFlow()
    private val _errors = MutableStateFlow<Set<BookFormError>>(emptySet())
    val errors: StateFlow<Set<BookFormError>> = _errors.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        if (bookId != null) viewModelScope.launch {
            val b = runCatching { c.books.get(bookId) }.getOrNull()
            if (b != null) {
                original = b
                _form.value = BookForm(
                    b.title, b.author, b.totalPages.takeIf { it > 0 }?.toString().orEmpty(), b.currentPage.takeIf { it > 0 }?.toString().orEmpty(),
                    b.status, b.targetDate, b.coverColor,
                )
            }
            _ready.value = true
        }
    }

    fun edit(change: (BookForm) -> BookForm) {
        _form.update(change)
        if (_errors.value.isNotEmpty()) _errors.value = validate(_form.value)
    }

    private fun validate(f: BookForm): Set<BookFormError> = buildSet {
        if (f.title.isBlank()) add(BookFormError.TITLE_EMPTY)
        val total = f.totalPages.toIntOrNull() ?: 0
        val current = f.currentPage.toIntOrNull() ?: 0
        if (total > 0 && current > total) add(BookFormError.PAGE_BEYOND_TOTAL)
    }

    fun save() {
        val f = _form.value
        val problems = validate(f)
        _errors.value = problems
        if (problems.isNotEmpty()) return
        viewModelScope.launch {
            val now = Instant.now(c.clock)
            val today = LocalDate.now()
            val base = original ?: Book(title = "", createdAt = now)
            val book = base.copy(
                title = f.title.trim().take(200),
                author = f.author.trim().take(120),
                totalPages = (f.totalPages.toIntOrNull() ?: 0).coerceIn(0, 100_000),
                currentPage = (f.currentPage.toIntOrNull() ?: 0).coerceIn(0, 100_000),
                targetDate = f.targetDate,
                coverColor = f.coverColor,
                updatedAt = now,
            ).let { if (it.status != f.status || original == null) it.withStatus(f.status, today, now) else it }
            runCatching { c.books.save(book) }
                .onSuccess { _saved.value = it }
                .onFailure { SafeLog.error("book_save_failed", it); _failed.value = true }
        }
    }

    fun dismissFailure() { _failed.value = false }
}
