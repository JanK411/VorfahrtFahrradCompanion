package nl.jjt.vorfahrtfahrradcompanion.db.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import nl.jjt.vorfahrtfahrradcompanion.domain.settings.CategorizationVariant
import nl.jjt.vorfahrtfahrradcompanion.domain.settings.Settings

class SettingsStore(private val dao: SettingsDao) {

    /** Emits [EMPTY] while no row has been saved yet. */
    val settings: Flow<Settings> = dao.observe().map { entity ->
        entity?.let { Settings(it.baseUrl, it.username, it.password) } ?: EMPTY
    }

    /** Which design of the criteria screen the rider picked; Jan until they pick another. */
    val categorization: Flow<CategorizationVariant> = dao.observe()
        .map { it?.categorization ?: CategorizationVariant.JAN }
        .distinctUntilChanged()

    suspend fun save(settings: Settings) = dao.upsert(
        row().copy(
            baseUrl = settings.baseUrl,
            username = settings.username,
            password = settings.password,
        ),
    )

    suspend fun saveCategorization(variant: CategorizationVariant) =
        dao.upsert(row().copy(categorization = variant))

    /**
     * The row as it stands, so writing one part of it leaves the rest alone — [Settings] no longer
     * covers every column, and an editor that only knows about its own must not blank the others.
     */
    private suspend fun row(): SettingsEntity =
        dao.observe().first() ?: SettingsEntity(
            baseUrl = EMPTY.baseUrl,
            username = EMPTY.username,
            password = EMPTY.password,
        )

    companion object {
        val EMPTY = Settings(baseUrl = "", username = "", password = "")
    }
}
