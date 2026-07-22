package nl.jjt.vorfahrtfahrradcompanion.patchnotes.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PatchNotesStateDao {
    /** Emits `null` until a version has been marked seen. */
    @Query("SELECT lastSeenVersion FROM patch_notes_state WHERE id = :id")
    fun observeLastSeenVersion(id: Int = PatchNotesStateEntity.SINGLETON_ID): Flow<String?>

    @Upsert
    suspend fun upsert(entity: PatchNotesStateEntity)
}
