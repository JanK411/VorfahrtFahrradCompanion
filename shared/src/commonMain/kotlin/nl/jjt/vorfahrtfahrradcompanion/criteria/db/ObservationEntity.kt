package nl.jjt.vorfahrtfahrradcompanion.criteria.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A recorded observation: the [values] the user selected, serialized to JSON, stamped with
 * [recordedAtEpochMs] (the moment of sampling). No location is stored — the sampling position is
 * recovered later by matching this timestamp against the GPS track.
 */
@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAtEpochMs: Long,
    val valuesJson: String,
)
