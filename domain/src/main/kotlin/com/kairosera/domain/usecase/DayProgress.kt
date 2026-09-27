package com.kairosera.domain.usecase

import com.kairosera.domain.model.TaskOccurrence
import java.time.LocalTime

data class DayProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val remaining: Int get() = total - done
}

object DaySummary {
    /** Skipped occurrences are neither done nor counted, so skipping never reads as failing. */
    fun progress(occurrences: List<TaskOccurrence>): DayProgress {
        val counted = occurrences.filterNot { it.isSkipped }
        return DayProgress(done = counted.count { it.isDone }, total = counted.size)
    }

    /** Open timed occurrences that have not ended yet, then open all-day ones, then overdue timed ones. */
    fun nextUp(occurrences: List<TaskOccurrence>, now: LocalTime, limit: Int = 4): List<TaskOccurrence> {
        val open = occurrences.filter { !it.isDone && !it.isSkipped }
        val (timed, allDay) = open.partition { it.task.startTime != null }
        val (upcoming, overdue) = timed.partition { o -> (o.task.endTime ?: o.task.startTime ?: LocalTime.MIN) >= now }
        return (upcoming.sortedBy { it.task.startTime } + allDay + overdue).take(limit)
    }
}
