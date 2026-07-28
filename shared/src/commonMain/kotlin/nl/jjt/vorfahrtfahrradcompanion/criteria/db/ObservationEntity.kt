package nl.jjt.vorfahrtfahrradcompanion.criteria.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import nl.jjt.vorfahrtfahrradcompanion.criteria.BoundaryKind

/**
 * A recorded segment: the values the user selected, serialized to JSON, bounded by the two moments the
 * rider marked. Each boundary carries its [BoundaryKind], because a button pressed late means the real
 * boundary lies before the stored timestamp. No location is stored — the segment's position is recovered
 * later by matching these timestamps against the GPS track.
 */
@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtEpochMs: Long,
    val startKind: BoundaryKind,
    val endedAtEpochMs: Long,
    val endKind: BoundaryKind,
    val valuesJson: String,
)
