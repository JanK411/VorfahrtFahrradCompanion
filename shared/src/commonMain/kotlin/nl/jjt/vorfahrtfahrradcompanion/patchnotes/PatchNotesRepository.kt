package nl.jjt.vorfahrtfahrradcompanion.patchnotes

import kotlinx.coroutines.flow.Flow
import nl.jjt.vorfahrtfahrradcompanion.patchnotes.db.PatchNotesStateDao
import nl.jjt.vorfahrtfahrradcompanion.patchnotes.db.PatchNotesStateEntity

class PatchNotesRepository(private val dao: PatchNotesStateDao) {

    /** The newest patch-note version the user has seen, or `null` if none yet. */
    val lastSeenVersion: Flow<String?> = dao.observeLastSeenVersion()

    suspend fun markSeen(version: String) =
        dao.upsert(PatchNotesStateEntity(lastSeenVersion = version))
}
