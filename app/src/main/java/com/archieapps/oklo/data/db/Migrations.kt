package com.archieapps.oklo.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val ONE_TO_TWO = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE cards ADD COLUMN easeFactor INTEGER")
            db.execSQL("ALTER TABLE cards ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 0")
            listOf(
                "scheduler TEXT NOT NULL DEFAULT 'sm2'",
                "graduatingIntervalDays INTEGER NOT NULL DEFAULT 1",
                "easyIntervalDays INTEGER NOT NULL DEFAULT 4",
                "startingEase REAL NOT NULL DEFAULT 2.5",
                "easyBonus REAL NOT NULL DEFAULT 1.3",
                "hardIntervalFactor REAL NOT NULL DEFAULT 1.2",
                "intervalModifier REAL NOT NULL DEFAULT 1.0",
                "newIntervalFactor REAL NOT NULL DEFAULT 0.0",
                "minimumIntervalDays INTEGER NOT NULL DEFAULT 1",
            ).forEach { db.execSQL("ALTER TABLE settings ADD COLUMN $it") }
            db.execSQL(
                "UPDATE cards SET intervalDays = MAX(1, CAST(ROUND((dueAt - lastReviewAt) / 86400000.0) AS INTEGER)) " +
                    "WHERE state IN ('review', 'relearning') AND dueAt IS NOT NULL AND lastReviewAt IS NOT NULL",
            )
            db.execSQL("UPDATE settings SET scheduler = 'sm2'")
        }
    }
}
