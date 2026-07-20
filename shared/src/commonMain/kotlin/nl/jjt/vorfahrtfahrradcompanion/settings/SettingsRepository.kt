package nl.jjt.vorfahrtfahrradcompanion.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.jjt.vorfahrtfahrradcompanion.settings.db.SettingsDao
import nl.jjt.vorfahrtfahrradcompanion.settings.db.SettingsEntity

class SettingsRepository(private val dao: SettingsDao) {

    /** Emits [EMPTY] while no row has been saved yet. */
    val settings: Flow<Settings> = dao.observe().map { entity ->
        entity?.let { Settings(it.baseUrl, it.username, it.password) } ?: EMPTY
    }

    suspend fun save(settings: Settings) =
        dao.upsert(SettingsEntity(baseUrl = settings.baseUrl, username = settings.username, password = settings.password))

    companion object {
        val EMPTY = Settings(baseUrl = "", username = "", password = "")
    }
}
