package nl.jjt.vorfahrtfahrradcompanion.settings.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table: [id] is always [SINGLETON_ID].
 *
 * Multiple named profiles are planned. That needs a `name` column and an active-profile marker
 * alongside an `autoGenerate` key — one Room migration covering all three, so the singleton costs
 * nothing to keep until then.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val baseUrl: String,
    val username: String,
    val password: String
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
