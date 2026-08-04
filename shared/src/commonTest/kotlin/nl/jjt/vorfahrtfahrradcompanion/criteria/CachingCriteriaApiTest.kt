package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.cache.SystemCacheMarker
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheDao
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheEntity
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsDao
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsEntity
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionKind
import nl.jjt.vorfahrtfahrradcompanion.FakeClock
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val catalogue = Catalogue(listOf(Criterion("WIDTH", CriterionKind.SINGLE, listOf("W_1", "W_2"))))

private const val BASE_URL = "http://example.test"
private val json = Json

private class FakeCacheDao(var row: CatalogueCacheEntity? = null) : CatalogueCacheDao {
    override suspend fun get(id: Int) = row
    override suspend fun upsert(entity: CatalogueCacheEntity) {
        row = entity
    }

    override suspend fun clear() {
        row = null
    }
}

private class FakeSystemCacheMarker(private var cleared: Boolean = false) : SystemCacheMarker {
    override suspend fun consumeCacheCleared() = cleared.also { cleared = false }
}

private class FakeDelegate(
    private val result: Catalogue = catalogue,
    private val error: Exception? = null,
) : CriteriaApi {
    var catalogueCalls = 0
    override suspend fun catalogue(): Catalogue {
        catalogueCalls++
        error?.let { throw it }
        return result
    }
}

private class FakeSettingsDao : SettingsDao {
    override fun observe(id: Int): Flow<SettingsEntity?> =
        flowOf(SettingsEntity(baseUrl = BASE_URL, username = "u", password = "p"))

    override suspend fun upsert(entity: SettingsEntity) = Unit
}

class CachingCriteriaApiTest {

    private val now = Instant.parse("2026-07-25T12:00:00Z")

    private fun api(
        delegate: CriteriaApi,
        dao: CatalogueCacheDao,
        systemCache: SystemCacheMarker = FakeSystemCacheMarker(),
    ) = CachingCriteriaApi(delegate, dao, SettingsRepository(FakeSettingsDao()), systemCache, FakeClock(now))

    private fun cachedRow(baseUrl: String = BASE_URL, fetchedAt: Instant = now) = CatalogueCacheEntity(
        baseUrl = baseUrl,
        catalogueJson = json.encodeToString(CatalogueDto.serializer(), catalogue.toDto()),
        fetchedAtEpochMs = fetchedAt.toEpochMilliseconds(),
    )

    @Test
    fun emptyCacheFetchesAndPersists() = runTest {
        val delegate = FakeDelegate()
        val dao = FakeCacheDao()

        assertEquals(catalogue, api(delegate, dao).catalogue())
        assertEquals(1, delegate.catalogueCalls)
        assertNotNull(dao.row)
        assertEquals(BASE_URL, dao.row?.baseUrl)
    }

    @Test
    fun freshCacheIsServedWithoutHittingTheServer() = runTest {
        val delegate = FakeDelegate()
        val dao = FakeCacheDao(cachedRow())

        assertEquals(catalogue, api(delegate, dao).catalogue())
        assertEquals(0, delegate.catalogueCalls)
    }

    @Test
    fun staleCacheIsServedWhenTheServerIsUnreachable() = runTest {
        val delegate = FakeDelegate(error = RuntimeException("offline"))
        val dao = FakeCacheDao(cachedRow(fetchedAt = now - 25.hours))

        assertEquals(catalogue, api(delegate, dao).catalogue())
        assertEquals(1, delegate.catalogueCalls)
    }

    @Test
    fun noCacheAndServerUnreachablePropagatesTheError() = runTest {
        val delegate = FakeDelegate(error = RuntimeException("offline"))

        assertFailsWith<RuntimeException> { api(delegate, FakeCacheDao()).catalogue() }
    }

    @Test
    fun cacheForAnotherServerIsIgnored() = runTest {
        val delegate = FakeDelegate()
        val dao = FakeCacheDao(cachedRow(baseUrl = "http://other.test"))

        assertEquals(catalogue, api(delegate, dao).catalogue())
        assertEquals(1, delegate.catalogueCalls)
        assertEquals(BASE_URL, dao.row?.baseUrl)
    }

    @Test
    fun cacheForAnotherServerIsNotUsedAsOfflineFallback() = runTest {
        val delegate = FakeDelegate(error = RuntimeException("offline"))
        val dao = FakeCacheDao(cachedRow(baseUrl = "http://other.test"))

        assertFailsWith<RuntimeException> { api(delegate, dao).catalogue() }
    }

    @Test
    fun clearingTheSystemCacheDropsTheCachedCatalogue() = runTest {
        val delegate = FakeDelegate()
        val dao = FakeCacheDao(cachedRow())

        api(delegate, dao, FakeSystemCacheMarker(cleared = true)).catalogue()

        assertEquals(1, delegate.catalogueCalls)
    }

    @Test
    fun clearingTheSystemCacheLeavesNoStaleFallback() = runTest {
        val delegate = FakeDelegate(error = RuntimeException("offline"))
        val dao = FakeCacheDao(cachedRow())

        assertFailsWith<RuntimeException> {
            api(delegate, dao, FakeSystemCacheMarker(cleared = true)).catalogue()
        }
        assertNull(dao.row)
    }
}
