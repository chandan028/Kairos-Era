package com.kairosera.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change, in order. SQL is copied from the exported schema JSON (app/schemas) so the
 * migrated database is byte-for-byte what Room expects; MigrationTest verifies it with real data.
 */
object Migrations {
    /** v2 adds trackers, study plans and reading. Only new tables, so no existing row is touched. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `trackers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `description` TEXT NOT NULL, `why` TEXT NOT NULL, `gain` TEXT NOT NULL, `template` TEXT NOT NULL, `frequency` TEXT NOT NULL, `frequencyDays` INTEGER NOT NULL, `frequencyTimes` INTEGER NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `archivedAt` INTEGER, `deletedAt` INTEGER, `isSample` INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trackers_deletedAt` ON `trackers` (`deletedAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `tracker_fields` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackerId` INTEGER NOT NULL, `label` TEXT NOT NULL, `type` TEXT NOT NULL, `unit` TEXT NOT NULL, `target` REAL, `options` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`trackerId`) REFERENCES `trackers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracker_fields_trackerId` ON `tracker_fields` (`trackerId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `tracker_values` (`fieldId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `trackerId` INTEGER NOT NULL, `number` REAL, `text` TEXT, `checked` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`fieldId`, `date`), FOREIGN KEY(`fieldId`) REFERENCES `tracker_fields`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`trackerId`) REFERENCES `trackers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracker_values_trackerId_date` ON `tracker_values` (`trackerId`, `date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracker_values_date` ON `tracker_values` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `study_topics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackerId` INTEGER NOT NULL, `parentId` INTEGER, `title` TEXT NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `resources` TEXT NOT NULL, `questions` TEXT NOT NULL, `practiceDone` INTEGER NOT NULL, `confidence` INTEGER NOT NULL, `position` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`trackerId`) REFERENCES `trackers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_topics_trackerId` ON `study_topics` (`trackerId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_topics_parentId` ON `study_topics` (`parentId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `study_targets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackerId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `topicId` INTEGER, `title` TEXT NOT NULL, `done` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`trackerId`) REFERENCES `trackers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`topicId`) REFERENCES `study_topics`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_targets_trackerId_date` ON `study_targets` (`trackerId`, `date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_targets_topicId` ON `study_targets` (`topicId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_targets_date` ON `study_targets` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `totalPages` INTEGER NOT NULL, `currentPage` INTEGER NOT NULL, `status` TEXT NOT NULL, `startDate` INTEGER, `targetDate` INTEGER, `finishedDate` INTEGER, `coverColor` INTEGER NOT NULL, `whyStarted` TEXT NOT NULL, `expected` TEXT NOT NULL, `learned` TEXT NOT NULL, `applied` TEXT NOT NULL, `changed` TEXT NOT NULL, `skills` TEXT NOT NULL, `keyIdeas` TEXT NOT NULL, `finalTakeaway` TEXT NOT NULL, `recommendAgain` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `isSample` INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_deletedAt` ON `books` (`deletedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_status` ON `books` (`status`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `reading_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `pages` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookId` ON `reading_sessions` (`bookId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_date` ON `reading_sessions` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `book_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, `page` INTEGER, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_notes_bookId` ON `book_notes` (`bookId`)")
        }
    }
}
