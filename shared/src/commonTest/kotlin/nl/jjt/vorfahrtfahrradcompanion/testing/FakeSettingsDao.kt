package nl.jjt.vorfahrtfahrradcompanion.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsDao
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsEntity

/** A settings row that never changes — for a test that only needs a server to point at. */
class FakeSettingsDao(
    private val baseUrl: String,
    private val username: String = "u",
    private val password: String = "p",
) : SettingsDao {
    override fun observe(id: Int): Flow<SettingsEntity?> =
        flowOf(SettingsEntity(baseUrl = baseUrl, username = username, password = password))

    override suspend fun upsert(entity: SettingsEntity) = Unit
}
