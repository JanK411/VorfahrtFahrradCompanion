package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.CatalogueCacheDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.CatalogueCacheEntity
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsRepository
import nl.jjt.vorfahrtfahrradcompanion.settings.normalizeBaseUrl
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Caches the criterion catalogue in Room so the app works offline and does not re-fetch on every screen
 * open. The catalogue changes rarely, so a fetch happens at most once per [CACHE_INVALIDATION_TIME]; a fresh copy
 * is served straight from the cache, and a stale copy is still served when the server is unreachable.
 * The cache is keyed by the current base URL so a previous server's catalogue is never shown after the
 * server is changed. Wraps [delegate].
 */
class CachingCriteriaApi(
    private val delegate: CriteriaApi,
    private val dao: CatalogueCacheDao,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.System,
) : CriteriaApi {
    private companion object {
        val CACHE_INVALIDATION_TIME = 24.hours
    }

    override suspend fun catalogue(): Catalogue {
        val baseUrl = currentBaseUrl()
        val cached = dao.get()?.takeIf { it.baseUrl == baseUrl }

        if (cached != null && !cached.isStale()) return cached.decode()

        return try {
            delegate.catalogue().also { store(baseUrl, it) }
        } catch (e: Exception) {
            // failed to fetch from the delegate. Fallback to the cached one.
            cached?.decode() ?: throw e
        }
    }

    private suspend fun currentBaseUrl(): String {
        val raw = settings.settings.first().baseUrl
        return normalizeBaseUrl(raw) ?: raw
    }

    private suspend fun store(baseUrl: String, catalogue: Catalogue) = dao.upsert(
        CatalogueCacheEntity(
            baseUrl = baseUrl,
            catalogueJson = Json.encodeToString(CatalogueDto.serializer(), catalogue.toDto()),
            fetchedAtEpochMs = clock.now().toEpochMilliseconds(),
        ),
    )

    private fun CatalogueCacheEntity.isStale() =
        clock.now().toEpochMilliseconds() - fetchedAtEpochMs > CACHE_INVALIDATION_TIME.inWholeMilliseconds

    private fun CatalogueCacheEntity.decode() =
        Json.decodeFromString(CatalogueDto.serializer(), catalogueJson).toDomain()
}
