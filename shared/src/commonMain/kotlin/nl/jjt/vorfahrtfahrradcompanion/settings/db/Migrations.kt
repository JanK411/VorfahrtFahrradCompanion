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
