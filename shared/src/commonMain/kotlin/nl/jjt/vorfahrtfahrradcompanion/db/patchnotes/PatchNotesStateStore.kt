package nl.jjt.vorfahrtfahrradcompanion.db.patchnotes

import kotlinx.coroutines.flow.Flow

class PatchNotesStateStore(private val dao: PatchNotesStateDao) {

    /** The newest patch-note version the user has seen, or `null` if none yet. */
    val lastSeenVersion: Flow<String?> = dao.observeLastSeenVersion()

    suspend fun markSeen(version: String) =
        dao.upsert(PatchNotesStateEntity(lastSeenVersion = version))
}
