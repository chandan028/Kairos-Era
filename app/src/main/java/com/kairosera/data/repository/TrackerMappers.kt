package com.kairosera.data.repository

import com.kairosera.core.database.BookEntity
import com.kairosera.core.database.BookNoteEntity
import com.kairosera.core.database.ReadingSessionEntity
import com.kairosera.core.database.StudyTargetEntity
import com.kairosera.core.database.StudyTopicEntity
import com.kairosera.core.database.TrackerEntity
import com.kairosera.core.database.TrackerFieldEntity
import com.kairosera.core.database.TrackerValueEntity
import com.kairosera.core.database.TrackerWithFields
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookNote
import com.kairosera.domain.reading.BookNoteKind
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.reading.ReadingSession
import com.kairosera.domain.tracker.FieldValue
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.StudyTarget
import com.kairosera.domain.tracker.StudyTopic
import com.kairosera.domain.tracker.TopicStatus
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerEntry
import com.kairosera.domain.tracker.TrackerField
import com.kairosera.domain.tracker.TrackerFrequency
import com.kairosera.domain.tracker.TrackerTemplate

private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

internal fun TrackerWithFields.toDomain(): Tracker {
    val t = tracker
    val frequency = when (t.frequency) {
        "SELECTED_DAYS" -> t.frequencyDays.toWeekdays().takeIf { it.isNotEmpty() }?.let { TrackerFrequency.SelectedDays(it) } ?: TrackerFrequency.Daily
        "TIMES_PER_WEEK" -> TrackerFrequency.TimesPerWeek(t.frequencyTimes.coerceIn(1, 7))
        "TIMES_PER_MONTH" -> TrackerFrequency.TimesPerMonth(t.frequencyTimes.coerceIn(1, 31))
        else -> TrackerFrequency.Daily
    }
    return Tracker(
        id = t.id,
        name = t.name,
        icon = t.icon,
        colorArgb = t.colorArgb,
        description = t.description,
        why = t.why,
        gain = t.gain,
        template = enumOr(t.template, TrackerTemplate.CUSTOM),
        frequency = frequency,
        fields = fields.sortedBy { it.position }.map { it.toDomain() },
        position = t.position,
        createdAt = t.createdAt.toInstant(),
        updatedAt = t.updatedAt.toInstant(),
        archivedAt = t.archivedAt?.toInstant(),
        deletedAt = t.deletedAt?.toInstant(),
        isSample = t.isSample,
    )
}

internal fun TrackerFieldEntity.toDomain() = TrackerField(
    id = id,
    label = label,
    type = enumOr(type, MeasurementType.NUMBER),
    unit = unit,
    target = target,
    options = options.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
    enabled = enabled,
    position = position,
)

internal fun Tracker.toEntity(): TrackerEntity {
    val (kind, days, times) = when (val f = frequency) {
        TrackerFrequency.Daily -> Triple("DAILY", 0, 0)
        is TrackerFrequency.SelectedDays -> Triple("SELECTED_DAYS", f.days.toMask(), 0)
        is TrackerFrequency.TimesPerWeek -> Triple("TIMES_PER_WEEK", 0, f.times)
        is TrackerFrequency.TimesPerMonth -> Triple("TIMES_PER_MONTH", 0, f.times)
    }
    return TrackerEntity(
        id = id, name = name, icon = icon, colorArgb = colorArgb, description = description, why = why, gain = gain,
        template = template.name, frequency = kind, frequencyDays = days, frequencyTimes = times, position = position,
        createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
        archivedAt = archivedAt?.toEpochMilli(), deletedAt = deletedAt?.toEpochMilli(), isSample = isSample,
    )
}

internal fun TrackerField.toEntity(trackerId: Long, position: Int) = TrackerFieldEntity(
    id = id, trackerId = trackerId, label = label, type = type.name, unit = unit, target = target,
    options = options.joinToString("\n") { it.replace('\n', ' ') }, enabled = enabled, position = position,
)

internal fun List<TrackerValueEntity>.toEntries(): List<TrackerEntry> =
    groupBy { it.trackerId to it.date }.map { (key, rows) ->
        TrackerEntry(
            trackerId = key.first,
            date = key.second.toDate(),
            values = rows.associate { r ->
                r.fieldId to FieldValue(
                    fieldId = r.fieldId,
                    number = r.number,
                    text = r.text,
                    checked = r.checked.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
                )
            },
            updatedAt = rows.maxOf { it.updatedAt }.toInstant(),
        )
    }

internal fun StudyTopicEntity.toDomain() = StudyTopic(
    id = id, trackerId = trackerId, parentId = parentId, title = title, status = enumOr(status, TopicStatus.NOT_STARTED),
    notes = notes, resources = resources, questions = questions, practiceDone = practiceDone,
    confidence = confidence.coerceIn(0, 5), position = position, updatedAt = updatedAt.toInstant(),
)

internal fun StudyTopic.toEntity() = StudyTopicEntity(
    id = id, trackerId = trackerId, parentId = parentId, title = title, status = status.name, notes = notes,
    resources = resources, questions = questions, practiceDone = practiceDone, confidence = confidence,
    position = position, updatedAt = updatedAt.toEpochMilli(),
)

internal fun StudyTargetEntity.toDomain() = StudyTarget(
    id = id, trackerId = trackerId, date = date.toDate(), topicId = topicId, title = title, done = done, position = position,
)

internal fun StudyTarget.toEntity() = StudyTargetEntity(
    id = id, trackerId = trackerId, date = date.toEpochDay(), topicId = topicId, title = title, done = done, position = position,
)

internal fun BookEntity.toDomain() = Book(
    id = id, title = title, author = author, totalPages = totalPages, currentPage = currentPage,
    status = enumOr(status, BookStatus.WANT_TO_READ), startDate = startDate?.toDate(), targetDate = targetDate?.toDate(),
    finishedDate = finishedDate?.toDate(), coverColor = coverColor, whyStarted = whyStarted, expected = expected,
    learned = learned, applied = applied, changed = changed, skills = skills, keyIdeas = keyIdeas,
    finalTakeaway = finalTakeaway, recommendAgain = recommendAgain, createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(), deletedAt = deletedAt?.toInstant(), isSample = isSample,
)

internal fun Book.toEntity() = BookEntity(
    id = id, title = title, author = author, totalPages = totalPages, currentPage = currentPage, status = status.name,
    startDate = startDate?.toEpochDay(), targetDate = targetDate?.toEpochDay(), finishedDate = finishedDate?.toEpochDay(),
    coverColor = coverColor, whyStarted = whyStarted, expected = expected, learned = learned, applied = applied,
    changed = changed, skills = skills, keyIdeas = keyIdeas, finalTakeaway = finalTakeaway, recommendAgain = recommendAgain,
    createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(), deletedAt = deletedAt?.toEpochMilli(), isSample = isSample,
)

internal fun ReadingSessionEntity.toDomain() = ReadingSession(
    id = id, bookId = bookId, date = date.toDate(), pages = pages, minutes = minutes, createdAt = createdAt.toInstant(),
)

internal fun BookNoteEntity.toDomain() = BookNote(
    id = id, bookId = bookId, kind = enumOr(kind, BookNoteKind.NOTE), text = text, page = page, createdAt = createdAt.toInstant(),
)

internal fun BookNote.toEntity() = BookNoteEntity(
    id = id, bookId = bookId, kind = kind.name, text = text, page = page, createdAt = createdAt.toEpochMilli(),
)
