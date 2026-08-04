package nl.jjt.vorfahrtfahrradcompanion.service.criteria

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheDao
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheEntity
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheStore
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsDao
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsEntity
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsStore
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionKind
import nl.jjt.vorfahrtfahrradcompanion.platform.SystemCacheMarker
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeCatalogueCacheDao
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeClock
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeCriteriaApi
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeSettingsDao
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeSystemCacheMarker
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





class CachingCriteriaApiTest {

    private val now = Instant.parse("2026-07-25T12:00:00Z")

    private fun api(
        delegate: CriteriaApi,
        dao: CatalogueCacheDao,
        systemCache: SystemCacheMarker = FakeSystemCacheMarker(),
    ) = CachingCriteriaApi(delegate, CatalogueCacheStore(dao), SettingsStore(FakeSettingsDao(BASE_URL)), systemCache, FakeClock(now))

    private fun cachedRow(baseUrl: String = BASE_URL, fetchedAt: Instant = now) = CatalogueCacheEntity(
        baseUrl = baseUrl,
        catalogueJson = json.encodeToString(CatalogueDto.serializer(), catalogue.toDto()),
        fetchedAtEpochMs = fetchedAt.toEpochMilliseconds(),
    )

    @Test
    fun emptyCacheFetchesAndPersists() = runTest {
        val delegate = FakeCriteriaApi(catalogue)
        val dao = FakeCatalogueCacheDao()

        assertEquals(catalogue, api(delegate, dao).catalogue())
        assertEquals(1, delegate.catalogueCalls)
        assertNotNull(dao.row)
        assertEquals(BASE_URL, dao.row?.baseUrl)
    }

    @Test
    fun freshCacheIsServedWithoutHittingTheServer() = runTest {
        val delegate = FakeCriteriaApi(catalogue)
        val dao = FakeCatalogueCacheDao(cachedRow())

        assertEquals(catalogue, api(delegate, dao).catalogue())
        assertEquals(0, delegate.catalogueCalls)
    }

    @Test
    fun staleCacheIsServedWhenTheServerIsUnreachable() = runTest {
        val delegate = FakeCriteriaApi(catalogue, error = RuntimeException("offline"))
        val dao = FakeCatalogueCacheDao(cachedRow(fetchedAt = now - 25.hours))

        assertEquals(catalogue, api(delegate, dao).catalogue())
        assertEquals(1, delegate.catalogueCalls)
    }

    @Test
    fun noCacheAndServerUnreachablePropagatesTheError() = runTest {
        val delegate = FakeCriteriaApi(catalogue, error = RuntimeException("offline"))

        assertFailsWith<RuntimeException> { api(delegate, FakeCatalogueCacheDao()).catalogue() }
    }

    @Test
    fun cacheForAnotherServerIsIgnored() = runTest {
        val delegate = FakeCriteriaApi(catalogue)
        val dao = FakeCatalogueCacheDao(cachedRow(baseUrl = "http://other.test"))

        assertEquals(catalogue, api(delegate, dao).catalogue())
        assertEquals(1, delegate.catalogueCalls)
        assertEquals(BASE_URL, dao.row?.baseUrl)
    }

    @Test
    fun cacheForAnotherServerIsNotUsedAsOfflineFallback() = runTest {
        val delegate = FakeCriteriaApi(catalogue, error = RuntimeException("offline"))
        val dao = FakeCatalogueCacheDao(cachedRow(baseUrl = "http://other.test"))

        assertFailsWith<RuntimeException> { api(delegate, dao).catalogue() }
    }

    @Test
    fun clearingTheSystemCacheDropsTheCachedCatalogue() = runTest {
        val delegate = FakeCriteriaApi(catalogue)
        val dao = FakeCatalogueCacheDao(cachedRow())

        api(delegate, dao, FakeSystemCacheMarker(cleared = true)).catalogue()

        assertEquals(1, delegate.catalogueCalls)
    }

    @Test
    fun clearingTheSystemCacheLeavesNoStaleFallback() = runTest {
        val delegate = FakeCriteriaApi(catalogue, error = RuntimeException("offline"))
        val dao = FakeCatalogueCacheDao(cachedRow())

        assertFailsWith<RuntimeException> {
            api(delegate, dao, FakeSystemCacheMarker(cleared = true)).catalogue()
        }
        assertNull(dao.row)
    }
}
