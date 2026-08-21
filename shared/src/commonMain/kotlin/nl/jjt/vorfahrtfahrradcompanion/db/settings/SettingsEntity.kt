package nl.jjt.vorfahrtfahrradcompanion.db.settings

import androidx.room.Entity
import androidx.room.PrimaryKey
import nl.jjt.vorfahrtfahrradcompanion.domain.settings.CategorizationVariant

/**
 * Single-row table: [id] is always [SINGLETON_ID].
 *
 * Multiple named profiles are planned. That needs a `name` column and an active-profile marker
 * alongside an `autoGenerate` key — one Room migration covering all three, so the singleton costs
 * nothing to keep until then.
 *
 * [categorization] is not part of the server connection the other three columns describe; it rides
 * along because this is where the one row the rider configures already lives.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val baseUrl: String,
    val username: String,
    val password: String,
    val categorization: CategorizationVariant = CategorizationVariant.JAN,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
