package nl.jjt.vorfahrtfahrradcompanion.criteria.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row cache of the criterion catalogue so the app works offline and does not re-fetch on every
 * screen open. [baseUrl] records which server the cached [catalogueJson] came from, so a stale copy is
 * never served after the server is changed in Settings; [fetchedAtEpochMs] backs the freshness window.
 */
@Entity(tableName = "catalogue_cache")
data class CatalogueCacheEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val baseUrl: String,
    val catalogueJson: String,
    val fetchedAtEpochMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
