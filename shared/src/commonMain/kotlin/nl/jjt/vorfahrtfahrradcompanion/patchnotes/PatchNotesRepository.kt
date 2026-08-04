package nl.jjt.vorfahrtfahrradcompanion.patchnotes

import kotlinx.coroutines.flow.Flow
import nl.jjt.vorfahrtfahrradcompanion.db.patchnotes.PatchNotesStateDao
import nl.jjt.vorfahrtfahrradcompanion.db.patchnotes.PatchNotesStateEntity

class PatchNotesRepository(private val dao: PatchNotesStateDao) {

    /** The newest patch-note version the user has seen, or `null` if none yet. */
    val lastSeenVersion: Flow<String?> = dao.observeLastSeenVersion()

    suspend fun markSeen(version: String) =
        dao.upsert(PatchNotesStateEntity(lastSeenVersion = version))
}
