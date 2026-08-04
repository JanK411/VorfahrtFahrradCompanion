package nl.jjt.vorfahrtfahrradcompanion.service.criteria

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.platform.SystemCacheMarker
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CachedCatalogue
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheStore
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsStore
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue
import nl.jjt.vorfahrtfahrradcompanion.service.http.normalizeBaseUrl
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Caches the criterion catalogue in Room so the app works offline and does not re-fetch on every screen
 * open. The catalogue changes rarely, so a fetch happens at most once per [CACHE_INVALIDATION_TIME]; a fresh copy
 * is served straight from the cache, and a stale copy is still served when the server is unreachable.
 * The cache is keyed by the current base URL so a previous server's catalogue is never shown after the
 * server is changed. Wraps [delegate].
 *
 * The cached copy also counts as cache to the operating system: clearing the app's cache from the
 * Android settings drops it, which [systemCache] reports.
 */
class CachingCriteriaApi(
    private val delegate: CriteriaApi,
    private val cache: CatalogueCacheStore,
    private val settings: SettingsStore,
    private val systemCache: SystemCacheMarker,
    private val clock: Clock = Clock.System,
) : CriteriaApi {
    private companion object {
        val CACHE_INVALIDATION_TIME = 24.hours
    }

    override suspend fun catalogue(): Catalogue {
        if (systemCache.consumeCacheCleared()) cache.clear()

        val baseUrl = currentBaseUrl()
        val cached = cache.cached()?.takeIf { it.baseUrl == baseUrl }

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

    private suspend fun store(baseUrl: String, catalogue: Catalogue) = cache.put(
        baseUrl = baseUrl,
        json = Json.encodeToString(CatalogueDto.serializer(), catalogue.toDto()),
        fetchedAt = clock.now(),
    )

    private fun CachedCatalogue.isStale() = clock.now() - fetchedAt > CACHE_INVALIDATION_TIME

    private fun CachedCatalogue.decode() =
        Json.decodeFromString(CatalogueDto.serializer(), json).toDomain()
}
