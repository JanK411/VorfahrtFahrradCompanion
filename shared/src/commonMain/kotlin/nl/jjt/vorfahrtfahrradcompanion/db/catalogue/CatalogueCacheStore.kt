package nl.jjt.vorfahrtfahrradcompanion.db.catalogue

import kotlin.time.Instant

/** A cached catalogue: the server it came from, what was cached, and when. */
data class CachedCatalogue(val baseUrl: String, val json: String, val fetchedAt: Instant)

/**
 * The catalogue cache. What the JSON means is the caller's business — this table holds it as an opaque
 * blob, because its shape belongs to the layer that encodes it, not to the database.
 */
class CatalogueCacheStore(private val dao: CatalogueCacheDao) {

    suspend fun cached(): CachedCatalogue? = dao.get()?.let {
        CachedCatalogue(
            baseUrl = it.baseUrl,
            json = it.catalogueJson,
            fetchedAt = Instant.fromEpochMilliseconds(it.fetchedAtEpochMs),
        )
    }

    suspend fun put(baseUrl: String, json: String, fetchedAt: Instant) = dao.upsert(
        CatalogueCacheEntity(
            baseUrl = baseUrl,
            catalogueJson = json,
            fetchedAtEpochMs = fetchedAt.toEpochMilliseconds(),
        ),
    )

    suspend fun clear() = dao.clear()
}
