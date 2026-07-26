package nl.jjt.vorfahrtfahrradcompanion.settings.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 → v2: adds the single-row `patch_notes_state` table backing the What's New "already seen" tracking.
 * Additive only — existing settings are untouched.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `patch_notes_state` " +
                "(`id` INTEGER NOT NULL, `lastSeenVersion` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v2 → v3: adds the single-row `catalogue_cache` table backing offline use of the criterion catalogue.
 * Additive only — existing tables are untouched.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `catalogue_cache` " +
                "(`id` INTEGER NOT NULL, `baseUrl` TEXT NOT NULL, `catalogueJson` TEXT NOT NULL, " +
                "`fetchedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v3 → v4: adds the `observations` table where submitted observations are stored locally (timestamp +
 * selected values) instead of being sent to the server. Additive only — existing tables are untouched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `observations` " +
                "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recordedAtEpochMs` INTEGER NOT NULL, " +
                "`valuesJson` TEXT NOT NULL)",
        )
    }
}
