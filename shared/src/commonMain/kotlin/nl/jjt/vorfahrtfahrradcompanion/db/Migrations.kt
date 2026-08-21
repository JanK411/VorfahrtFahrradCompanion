package nl.jjt.vorfahrtfahrradcompanion.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.BoundaryKind

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

/**
 * v4 → v5: an observation covers a segment (start and end, each with its [BoundaryKind]) instead of a
 * single instant. The only destructive migration in this file: no observation has been recorded in the
 * field yet and nothing reads the table, so the point-shaped rows are dropped rather than reshaped into
 * zero-length segments.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `observations`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `observations` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, " +
                    "`startKind` TEXT NOT NULL, `endedAtEpochMs` INTEGER NOT NULL, `endKind` TEXT NOT NULL, " +
                    "`valuesJson` TEXT NOT NULL)",
        )
    }
}

/**
 * v5 → v6: adds the `rides` table and makes every observation belong to one. Destructive, like the
 * v4 → v5 reshape before it: an observation recorded before rides existed has no ride to be attached to,
 * and the app is still in testing, so those rows are dropped rather than folded into an invented ride.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `observations`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `rides` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, " +
                    "`endedAtEpochMs` INTEGER, `name` TEXT)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `observations` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rideId` INTEGER NOT NULL, " +
                    "`startedAtEpochMs` INTEGER NOT NULL, `startKind` TEXT NOT NULL, " +
                    "`endedAtEpochMs` INTEGER NOT NULL, `endKind` TEXT NOT NULL, `valuesJson` TEXT NOT NULL, " +
                    "FOREIGN KEY(`rideId`) REFERENCES `rides`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_observations_rideId` ON `observations` (`rideId`)",
        )
    }
}

/**
 * v6 → v7: a ride is identified by a UUID minted on the device instead of an autoincrement row number,
 * so a server collecting rides from several devices never sees the same id twice. Destructive, like the
 * two reshapes before it: a row number cannot be turned into a UUID, and no ride recorded so far is
 * worth carrying over.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `observations`")
        connection.execSQL("DROP TABLE IF EXISTS `rides`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `rides` " +
                    "(`id` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, " +
                    "`endedAtEpochMs` INTEGER, `name` TEXT, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `observations` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rideId` TEXT NOT NULL, " +
                    "`startedAtEpochMs` INTEGER NOT NULL, `startKind` TEXT NOT NULL, " +
                    "`endedAtEpochMs` INTEGER NOT NULL, `endKind` TEXT NOT NULL, `valuesJson` TEXT NOT NULL, " +
                    "FOREIGN KEY(`rideId`) REFERENCES `rides`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_observations_rideId` ON `observations` (`rideId`)",
        )
    }
}

/**
 * v7 → v8: records when a ride was accepted by the server, which is what tells a finished ride apart
 * from one already sent. Additive — a ride recorded before this column existed simply has not been sent.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `rides` ADD COLUMN `uploadedAtEpochMs` INTEGER")
    }
}

/**
 * v8 → v9: records which design of the criteria screen the rider is being shown. Additive — a rider
 * upgrading keeps the one they have been riding with all along, which is what the default says.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `settings` ADD COLUMN `categorization` TEXT NOT NULL DEFAULT 'JAN'")
    }
}
