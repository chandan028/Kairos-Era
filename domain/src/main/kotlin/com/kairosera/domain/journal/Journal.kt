package com.kairosera.domain.journal

import java.time.Instant
import java.time.LocalDate

/** How the day felt, best first. Stored by name; the order is the scale used for averages. */
enum class Mood(val emoji: String, val score: Int) {
    GREAT("😄", 5),
    GOOD("🙂", 4),
    OKAY("😐", 3),
    LOW("😔", 2),
    HARD("😣", 1),
}

/**
 * One reflection for one day. Every part is optional: a single line, a mood tap or a full page
 * all count. [tomorrow] is the "one thing for tomorrow" the person may turn into a task.
 */
data class JournalEntry(
    val id: Long = 0,
    val date: LocalDate,
    val mood: Mood? = null,
    val text: String = "",
    val win: String = "",
    val lesson: String = "",
    val gratitude: String = "",
    val tomorrow: String = "",
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val deletedAt: Instant? = null,
    val isSample: Boolean = false,
) {
    val isBlank: Boolean get() = mood == null && listOf(text, win, lesson, gratitude, tomorrow).all { it.isBlank() }

    /** The first thing written, for lists and the calendar. Never includes more than [max] characters. */
    fun excerpt(max: Int = 140): String =
        listOf(text, win, lesson, gratitude, tomorrow).firstOrNull { it.isNotBlank() }?.trim()?.replace(Regex("\\s+"), " ")?.let {
            if (it.length <= max) it else it.take(max - 1).trimEnd() + "…"
        }.orEmpty()

    companion object {
        const val MAX_TEXT = 10_000
        const val MAX_LINE = 500
    }
}
