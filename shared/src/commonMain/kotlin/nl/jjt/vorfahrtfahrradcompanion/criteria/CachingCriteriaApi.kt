package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.CatalogueCacheDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.CatalogueCacheEntity
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsRepository
import nl.jjt.vorfahrtfahrradcompanion.settings.normalizeBaseUrl
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

/**
 * Caches the criterion catalogue in Room so the app works offline and does not re-fetch on every screen
 * open. The catalogue changes rarely, so a fetch happens at most once per [CATALOGUE_TTL]; a fresh copy
 * is served straight from the cache, and a stale copy is still served when the server is unreachable.
 * The cache is keyed by the current base URL so a previous server's catalogue is never shown after the
 * server is changed. Wraps [delegate] and passes [submit] straight through.
 */
@OptIn(ExperimentalTime::class)
class CachingCriteriaApi(
    private val delegate: CriteriaApi,
    private val dao: CatalogueCacheDao,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.System,
) : CriteriaApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun catalogue(): Catalogue {
        val baseUrl = currentBaseUrl()
        val cached = dao.get()?.takeIf { it.baseUrl == baseUrl }

        if (cached != null && !cached.isStale()) return cached.decode()

        return try {
            delegate.catalogue().also { store(baseUrl, it) }
        } catch (e: Exception) {
            cached?.decode() ?: throw e
        }
    }

    override suspend fun submit(o: Observation) = delegate.submit(o)

    private suspend fun currentBaseUrl(): String {
        val raw = settings.settings.first().baseUrl
        return normalizeBaseUrl(raw) ?: raw
    }

    private suspend fun store(baseUrl: String, catalogue: Catalogue) = dao.upsert(
        CatalogueCacheEntity(
            baseUrl = baseUrl,
            catalogueJson = json.encodeToString(CatalogueDto.serializer(), catalogue.toDto()),
            fetchedAtEpochMs = clock.now().toEpochMilliseconds(),
        ),
    )

    private fun CatalogueCacheEntity.isStale() =
        clock.now().toEpochMilliseconds() - fetchedAtEpochMs > CATALOGUE_TTL.inWholeMilliseconds

    private fun CatalogueCacheEntity.decode() =
        json.decodeFromString(CatalogueDto.serializer(), catalogueJson).toDomain()

    private companion object {
        val CATALOGUE_TTL = 24.hours
    }
}
