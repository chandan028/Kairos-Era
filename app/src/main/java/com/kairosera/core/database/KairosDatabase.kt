package com.kairosera.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema history lives in app/schemas (exported JSON), and every version bump must ship a
 * Migration plus a test in test/.../MigrationTest. Destructive migration is never enabled:
 * an unknown upgrade path makes Room throw rather than silently wipe the user's data.
 */
@Database(
    entities = [
        CategoryEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        SubtaskEntity::class,
        TaskTagEntity::class,
        OccurrenceStateEntity::class,
        ScheduledNotificationEntity::class,
        TrackerEntity::class,
        TrackerFieldEntity::class,
        TrackerValueEntity::class,
        StudyTopicEntity::class,
        StudyTargetEntity::class,
        BookEntity::class,
        ReadingSessionEntity::class,
        BookNoteEntity::class,
        JournalEntryEntity::class,
    ],
    version = KairosDatabase.VERSION,
    exportSchema = true,
)
abstract class KairosDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun notificationDao(): NotificationDao
    abstract fun trackerDao(): TrackerDao
    abstract fun studyDao(): StudyDao
    abstract fun bookDao(): BookDao
    abstract fun journalDao(): JournalDao

    companion object {
        const val VERSION = 3
        const val NAME = "kairos.db"

        /** Ordered list of every migration. Append; never edit a shipped one. */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)

        fun build(context: Context): KairosDatabase =
            Room.databaseBuilder(context, KairosDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
