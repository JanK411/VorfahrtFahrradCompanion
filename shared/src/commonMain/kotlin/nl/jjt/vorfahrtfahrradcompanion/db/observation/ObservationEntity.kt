package nl.jjt.vorfahrtfahrradcompanion.db.observation

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import nl.jjt.vorfahrtfahrradcompanion.db.ride.RideEntity
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.BoundaryKind

/**
 * A recorded segment: the values the user selected, serialized to JSON, bounded by the two moments the
 * rider marked. Each boundary carries its [BoundaryKind], because a button pressed late means the real
 * boundary lies before the stored timestamp. No location is stored — the segment's position is recovered
 * later by matching these timestamps against the GPS track.
 *
 * Every segment belongs to the [RideEntity] it was recorded during — there is no describing a stretch of
 * path outside a ride — and goes with it when that ride is deleted.
 */
@Entity(
    tableName = "observations",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("rideId")],
)
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val startedAtEpochMs: Long,
    val startKind: BoundaryKind,
    val endedAtEpochMs: Long,
    val endKind: BoundaryKind,
    val valuesJson: String,
)
