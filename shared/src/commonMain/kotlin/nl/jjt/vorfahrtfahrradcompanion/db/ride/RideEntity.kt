package nl.jjt.vorfahrtfahrradcompanion.db.ride

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One outing, bracketing the segments recorded during it. The row is written when the ride opens rather
 * than when it closes, so the ride — and the segments hanging off it — survive the app being killed
 * mid-ride; [endedAtEpochMs] is null for exactly as long as it is still running.
 *
 * [name] is whatever the rider called it on their way out, and stays null when they did not bother.
 *
 * [id] is a UUID minted on the device rather than a row number: a ride is eventually handed to a server
 * that sees rides from every device, and an autoincrement means the same id there twice.
 */
@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val name: String? = null,
)
