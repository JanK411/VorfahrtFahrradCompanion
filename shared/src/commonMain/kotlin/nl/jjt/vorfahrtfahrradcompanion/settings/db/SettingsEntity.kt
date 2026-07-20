package nl.jjt.vorfahrtfahrradcompanion.settings.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row table: [id] is always [SINGLETON_ID]. */
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
