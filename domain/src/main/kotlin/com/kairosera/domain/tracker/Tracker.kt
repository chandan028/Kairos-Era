package com.kairosera.domain.tracker

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * One engine for every tracker. Study, fitness, habits, reading goals and custom trackers
 * differ only in their template, fields and frequency, never in how they are stored or scored.
 */
enum class MeasurementType {
    CHECKBOX, NUMBER, DECIMAL, DURATION, PAGES, PERCENTAGE, RATING, TEXT, CHECKLIST;

    /** Text fields are for reflection; they never count toward completion. */
    val countsTowardProgress: Boolean get() = this != TEXT
}

enum class TrackerTemplate { STUDY, FITNESS, READING, HABIT, PROJECT, FINANCE, LEARNING, HEALTH, PERSONAL, CUSTOM }

sealed interface TrackerFrequency {
    data object Daily : TrackerFrequency
    data class SelectedDays(val days: Set<DayOfWeek>) : TrackerFrequency
    /** Complete the period when the tracker was completed on at least [times] days in the ISO week. */
    data class TimesPerWeek(val times: Int) : TrackerFrequency
    data class TimesPerMonth(val times: Int) : TrackerFrequency
}

data class TrackerField(
    val id: Long = 0,
    val label: String,
    val type: MeasurementType,
    val unit: String = "",
    /** Daily target, e.g. 8000 steps or 2.5 litres. Null means "any value counts". */
    val target: Double? = null,
    /** CHECKLIST items. */
    val options: List<String> = emptyList(),
    val enabled: Boolean = true,
    val position: Int = 0,
)

data class Tracker(
    val id: Long = 0,
    val name: String,
    val icon: String = "⭐",
    val colorArgb: Long = 0xFF2E4A7D,
    val description: String = "",
    val why: String = "",
    val gain: String = "",
    val template: TrackerTemplate = TrackerTemplate.CUSTOM,
    val frequency: TrackerFrequency = TrackerFrequency.Daily,
    val fields: List<TrackerField> = emptyList(),
    val position: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val archivedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val isSample: Boolean = false,
) {
    val isActive: Boolean get() = archivedAt == null && deletedAt == null
    val enabledFields: List<TrackerField> get() = fields.filter { it.enabled }.sortedBy { it.position }
}

/** One stored value. Numbers (incl. durations in minutes, ratings 1-5, checkbox 0/1) use [number]; text uses [text]; checklists use [checked]. */
data class FieldValue(
    val fieldId: Long,
    val number: Double? = null,
    val text: String? = null,
    val checked: Set<Int> = emptySet(),
)

data class TrackerEntry(
    val trackerId: Long,
    val date: LocalDate,
    val values: Map<Long, FieldValue>,
    val updatedAt: Instant = Instant.EPOCH,
)
