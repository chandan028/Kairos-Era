package com.kairosera.domain.repository

import com.kairosera.domain.model.NotificationStatus
import com.kairosera.domain.model.ScheduledNotification
import java.time.Instant
import java.time.LocalDate

/** Persistence of the Reminder -> notification id -> trigger time -> status mapping. */
interface NotificationRepository {
    suspend fun getSince(date: LocalDate): List<ScheduledNotification>

    suspend fun get(id: Long): ScheduledNotification?

    /** Inserts (id == 0) or updates a row; returns its id, which is also the notification id. */
    suspend fun upsert(row: ScheduledNotification): Long

    suspend fun setStatus(id: Long, status: NotificationStatus, at: Instant, triggerAt: Instant? = null)

    suspend fun activeForTask(taskId: Long): List<ScheduledNotification>
}
