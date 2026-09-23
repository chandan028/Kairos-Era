package com.kairosera.data.sample

import android.content.Context
import com.kairosera.R
import com.kairosera.core.database.CategoryEntity
import com.kairosera.core.database.KairosDatabase
import com.kairosera.domain.model.Frequency
import com.kairosera.domain.model.Priority
import com.kairosera.domain.model.RepeatRule
import com.kairosera.domain.model.Subtask
import com.kairosera.domain.model.Task
import com.kairosera.domain.repository.TaskRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Default categories and optional example tasks. Examples are flagged isSample and can be removed in one tap. */
object SampleData {
    const val CATEGORY_LEARNING = 1L
    const val CATEGORY_HEALTH = 2L
    const val CATEGORY_READING = 3L
    const val CATEGORY_WORK = 4L
    const val CATEGORY_PERSONAL = 5L

    suspend fun ensureCategories(context: Context, db: KairosDatabase) {
        val dao = db.taskDao()
        if (dao.categoryCount() > 0) return
        listOf(
            CategoryEntity(CATEGORY_LEARNING, context.getString(R.string.focus_learning), 0xFF3F5E96, "school", 0),
            CategoryEntity(CATEGORY_HEALTH, context.getString(R.string.focus_health), 0xFF4E8A64, "fitness", 1),
            CategoryEntity(CATEGORY_READING, context.getString(R.string.focus_reading), 0xFFB5652B, "book", 2),
            CategoryEntity(CATEGORY_WORK, context.getString(R.string.focus_work), 0xFF6B5B95, "work", 3),
            CategoryEntity(CATEGORY_PERSONAL, context.getString(R.string.focus_personal), 0xFF9A6B4F, "person", 4),
        ).forEach { dao.upsertCategory(it) }
    }

    suspend fun insertExamples(context: Context, repo: TaskRepository, today: LocalDate, clock: Clock) {
        if (repo.hasSampleData()) return
        val now = Instant.now(clock)
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val daily = RepeatRule(Frequency.DAILY)
        fun task(title: Int, start: LocalTime?, end: LocalTime?, category: Long, rule: RepeatRule?, priority: Priority = Priority.NONE, subtasks: List<Int> = emptyList()) = Task(
            title = context.getString(title),
            date = today,
            startTime = start,
            endTime = end,
            categoryId = category,
            repeatRule = rule,
            priority = priority,
            subtasks = subtasks.mapIndexed { i, s -> Subtask(title = context.getString(s), position = i) },
            createdAt = now,
            updatedAt = now,
            isSample = true,
        )
        listOf(
            task(R.string.sample_java, LocalTime.of(8, 0), LocalTime.of(9, 0), CATEGORY_LEARNING, RepeatRule(Frequency.WEEKLY, weekdays = weekdays), Priority.HIGH,
                listOf(R.string.sample_java_1, R.string.sample_java_2, R.string.sample_java_3)),
            task(R.string.sample_walk, LocalTime.of(6, 30), LocalTime.of(7, 0), CATEGORY_HEALTH, daily),
            task(R.string.sample_project, LocalTime.of(10, 30), LocalTime.of(12, 30), CATEGORY_WORK, null, Priority.MEDIUM),
            task(R.string.sample_workout, LocalTime.of(18, 30), LocalTime.of(19, 15), CATEGORY_HEALTH, daily),
            task(R.string.sample_read, LocalTime.of(21, 30), LocalTime.of(22, 0), CATEGORY_READING, daily),
            task(R.string.sample_reflection, null, null, CATEGORY_PERSONAL, daily),
        ).forEach { repo.saveTask(it) }
    }
}
