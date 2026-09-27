package com.kairosera.data.repository

import com.kairosera.core.database.CategoryEntity
import com.kairosera.core.database.OccurrenceStateEntity
import com.kairosera.core.database.ReminderEntity
import com.kairosera.core.database.ScheduledNotificationEntity
import com.kairosera.core.database.SubtaskEntity
import com.kairosera.core.database.TaskEntity
import com.kairosera.core.database.TaskWithDetails
import com.kairosera.domain.model.Category
import com.kairosera.domain.model.Frequency
import com.kairosera.domain.model.NotificationStatus
import com.kairosera.domain.model.OccurrenceState
import com.kairosera.domain.model.OccurrenceStatus
import com.kairosera.domain.model.Priority
import com.kairosera.domain.model.Reminder
import com.kairosera.domain.model.ReminderSound
import com.kairosera.domain.model.ReminderTiming
import com.kairosera.domain.model.RepeatRule
import com.kairosera.domain.model.ScheduledNotification
import com.kairosera.domain.model.Subtask
import com.kairosera.domain.model.Task
import com.kairosera.domain.time.RecurrenceEngine
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal fun LocalTime.toMinute(): Int = hour * 60 + minute
internal fun Int.toLocalTime(): LocalTime = LocalTime.of((this / 60).coerceIn(0, 23), (this % 60).coerceIn(0, 59))
internal fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)
internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

private inline fun <reified E : Enum<E>> safeEnum(name: String?, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

internal fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
internal fun Int.toWeekdays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

internal fun TaskWithDetails.toDomain(): Task {
    val t = task
    val rule = t.repeatFrequency?.let { f ->
        runCatching {
            RepeatRule(
                frequency = safeEnum(f, Frequency.DAILY),
                interval = t.repeatInterval.coerceIn(1, RepeatRule.MAX_INTERVAL),
                weekdays = t.repeatWeekdays.toWeekdays(),
                endDate = t.repeatEndDate?.toDate(),
                occurrenceCount = t.repeatCount?.coerceIn(1, RepeatRule.MAX_COUNT),
            )
        }.getOrNull()
    }
    return Task(
        id = t.id,
        title = t.title,
        description = t.description,
        notes = t.notes,
        date = t.date.toDate(),
        startTime = t.startMinute?.toLocalTime(),
        endTime = t.endMinute?.toLocalTime(),
        priority = safeEnum(t.priority, Priority.NONE),
        categoryId = t.categoryId,
        repeatRule = rule,
        reminders = reminders.map { it.toDomain() },
        subtasks = subtasks.sortedBy { it.position }.map { Subtask(it.id, it.title, it.done, it.position) },
        tags = tags.map { it.tag }.sorted(),
        createdAt = t.createdAt.toInstant(),
        updatedAt = t.updatedAt.toInstant(),
        archivedAt = t.archivedAt?.toInstant(),
        deletedAt = t.deletedAt?.toInstant(),
        isSample = t.isSample,
    )
}

internal fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    taskId = taskId,
    timing = if (kind == KIND_BEFORE_START) {
        ReminderTiming.BeforeStart(minutesBefore ?: 0)
    } else {
        ReminderTiming.AtTime((minuteOfDay ?: 0).toLocalTime(), dayOffset)
    },
    persistent = persistent,
    sound = ReminderSound.fromKey(sound),
)

internal fun Reminder.toEntity(taskId: Long): ReminderEntity = when (val t = timing) {
    is ReminderTiming.AtTime -> ReminderEntity(id, taskId, KIND_AT_TIME, t.time.toMinute(), t.dayOffset, null, persistent, sound.key)
    is ReminderTiming.BeforeStart -> ReminderEntity(id, taskId, KIND_BEFORE_START, null, 0, t.minutes, persistent, sound.key)
}

internal fun Task.toEntity(completedAt: Long?): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    notes = notes,
    date = date.toEpochDay(),
    startMinute = startTime?.toMinute(),
    endMinute = endTime?.toMinute(),
    priority = priority.name,
    categoryId = categoryId,
    repeatFrequency = repeatRule?.frequency?.name,
    repeatInterval = repeatRule?.interval ?: 1,
    repeatWeekdays = repeatRule?.weekdays?.toMask() ?: 0,
    repeatEndDate = repeatRule?.endDate?.toEpochDay(),
    repeatCount = repeatRule?.occurrenceCount,
    seriesEnd = RecurrenceEngine.lastPossibleDate(date, repeatRule)?.toEpochDay(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    completedAt = completedAt,
    archivedAt = archivedAt?.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    isSample = isSample,
)

internal fun Subtask.toEntity(taskId: Long) = SubtaskEntity(id, taskId, title, done, position)

internal fun OccurrenceStateEntity.toDomain() =
    OccurrenceState(taskId, date.toDate(), safeEnum(status, OccurrenceStatus.DONE), changedAt.toInstant())

internal fun CategoryEntity.toDomain() = Category(id, name, colorArgb, icon, position)
internal fun Category.toEntity() = CategoryEntity(id, name, colorArgb, icon, position)

internal fun ScheduledNotificationEntity.toDomain() = ScheduledNotification(
    id = id,
    reminderId = reminderId,
    taskId = taskId,
    occurrenceDate = occurrenceDate.toDate(),
    triggerAt = triggerAt.toInstant(),
    status = safeEnum(status, NotificationStatus.CANCELLED),
    updatedAt = updatedAt.toInstant(),
)

internal fun ScheduledNotification.toEntity() = ScheduledNotificationEntity(
    id = id,
    reminderId = reminderId,
    taskId = taskId,
    occurrenceDate = occurrenceDate.toEpochDay(),
    triggerAt = triggerAt.toEpochMilli(),
    status = status.name,
    updatedAt = updatedAt.toEpochMilli(),
)

internal const val KIND_AT_TIME = "AT_TIME"
internal const val KIND_BEFORE_START = "BEFORE_START"
