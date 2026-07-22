package nl.jjt.vorfahrtfahrradcompanion.patchnotes.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table ([id] is always [SINGLETON_ID]) holding the newest patch-note version the user has
 * already seen. Survives app updates, so the What's New page can show only newer notes.
 */
@Entity(tableName = "patch_notes_state")
data class PatchNotesStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastSeenVersion: String,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
