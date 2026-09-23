package com.kairosera.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema history lives in app/schemas (exported JSON), and every version bump must ship a
 * Migration plus a test in androidTest/MigrationTest. Destructive migration is never enabled:
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
    ],
    version = KairosDatabase.VERSION,
    exportSchema = true,
)
abstract class KairosDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        const val VERSION = 1
        const val NAME = "kairos.db"

        /** Ordered list of every migration. Append; never edit a shipped one. */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun build(context: Context): KairosDatabase =
            Room.databaseBuilder(context, KairosDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
