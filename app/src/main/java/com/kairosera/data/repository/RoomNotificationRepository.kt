package com.kairosera.data.repository

import com.kairosera.core.database.NotificationDao
import com.kairosera.domain.model.NotificationStatus
import com.kairosera.domain.model.ScheduledNotification
import com.kairosera.domain.repository.NotificationRepository
import java.time.Instant
import java.time.LocalDate

class RoomNotificationRepository(private val dao: NotificationDao) : NotificationRepository {
    override suspend fun getSince(date: LocalDate): List<ScheduledNotification> = dao.since(date.toEpochDay()).map { it.toDomain() }

    override suspend fun get(id: Long): ScheduledNotification? = dao.get(id)?.toDomain()

    override suspend fun upsert(row: ScheduledNotification): Long {
        val newId = dao.upsert(row.toEntity())
        return if (row.id != 0L) row.id else newId
    }

    override suspend fun setStatus(id: Long, status: NotificationStatus, at: Instant, triggerAt: Instant?) {
        if (triggerAt == null) {
            dao.setStatus(id, status.name, at.toEpochMilli())
        } else {
            dao.setStatusAndTrigger(id, status.name, at.toEpochMilli(), triggerAt.toEpochMilli())
        }
    }

    override suspend fun activeForTask(taskId: Long): List<ScheduledNotification> = dao.activeForTask(taskId).map { it.toDomain() }
}
