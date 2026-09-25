package com.kairosera.domain.reading

import java.time.Instant
import java.time.LocalDate

enum class BookStatus { WANT_TO_READ, READING, PAUSED, FINISHED }

/**
 * A book is more than pages: the insight fields follow READ -> UNDERSTAND -> REMEMBER -> APPLY.
 */
data class Book(
    val id: Long = 0,
    val title: String,
    val author: String = "",
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val status: BookStatus = BookStatus.WANT_TO_READ,
    val startDate: LocalDate? = null,
    val targetDate: LocalDate? = null,
    val finishedDate: LocalDate? = null,
    val coverColor: Long = 0xFF2E4A7D,
    val whyStarted: String = "",
    val expected: String = "",
    val learned: String = "",
    val applied: String = "",
    val changed: String = "",
    val skills: String = "",
    val keyIdeas: String = "",
    val finalTakeaway: String = "",
    val recommendAgain: Boolean? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val deletedAt: Instant? = null,
    val isSample: Boolean = false,
) {
    val progress: Double get() = if (totalPages <= 0) 0.0 else (currentPage.toDouble() / totalPages).coerceIn(0.0, 1.0)
    val isActive: Boolean get() = deletedAt == null
}

data class ReadingSession(
    val id: Long = 0,
    val bookId: Long,
    val date: LocalDate,
    val pages: Int,
    val minutes: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
)

enum class BookNoteKind { NOTE, HIGHLIGHT, LESSON }

data class BookNote(
    val id: Long = 0,
    val bookId: Long,
    val kind: BookNoteKind,
    val text: String,
    val page: Int? = null,
    val createdAt: Instant = Instant.EPOCH,
)

object ReadingMath {
    /** Applies a session: pages move the bookmark forward (never past the end, never backwards). */
    fun afterSession(book: Book, pages: Int, date: LocalDate, now: Instant): Book {
        val safePages = pages.coerceAtLeast(0)
        val max = if (book.totalPages > 0) book.totalPages else Int.MAX_VALUE
        return book.copy(
            currentPage = (book.currentPage + safePages).coerceAtMost(max),
            status = if (book.status == BookStatus.WANT_TO_READ || book.status == BookStatus.PAUSED) BookStatus.READING else book.status,
            startDate = book.startDate ?: date,
            updatedAt = now,
        )
    }

    fun pagesOn(sessions: List<ReadingSession>, date: LocalDate): Int = sessions.filter { it.date == date }.sumOf { it.pages }
    fun minutesOn(sessions: List<ReadingSession>, date: LocalDate): Int = sessions.filter { it.date == date }.sumOf { it.minutes }

    /** Pages per day still needed to finish by the target date, or null when there is no target. */
    fun pagesPerDayToTarget(book: Book, today: LocalDate): Int? {
        val target = book.targetDate ?: return null
        if (book.totalPages <= 0) return null
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, target).toInt() + 1
        val left = (book.totalPages - book.currentPage).coerceAtLeast(0)
        if (left == 0) return 0
        return if (daysLeft <= 0) left else (left + daysLeft - 1) / daysLeft
    }
}
