package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val width = Criterion("WIDTH", CriterionKind.SINGLE, listOf("W_1", "W_2"))
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI, listOf("CARS", "CYCLISTS"))
private val catalogue = Catalogue(listOf(width, users))

private val recordedAt = Instant.parse("2026-07-20T12:43:37Z")
private val valuesSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

private class FakeApi : CriteriaApi {
    override suspend fun catalogue() = catalogue
}

private class FakeObservationDao : ObservationDao {
    var inserted: ObservationEntity? = null
    override suspend fun insert(entity: ObservationEntity) {
        inserted = entity
    }
}

@OptIn(ExperimentalTime::class)
private class FakeClock(private val instant: Instant) : Clock {
    override fun now() = instant
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CriteriaViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vmWith(dao: FakeObservationDao) =
        CriteriaViewModel(FakeApi(), ObservationRepository(dao, FakeClock(recordedAt)))

    @Test
    fun singleSelectionReplacesAndClears() {
        var selections: Map<String, Set<String>> = emptyMap()

        selections = selections.select(width, "W_1")
        assertEquals(setOf("W_1"), selections["WIDTH"])

        // A different chip replaces
        selections = selections.select(width, "W_2")
        assertEquals(setOf("W_2"), selections["WIDTH"])

        // The selected chip clears
        selections = selections.select(width, "W_2")
        assertEquals(emptySet(), selections["WIDTH"])
    }

    @Test
    fun multiSelectionToggles() {
        var selections: Map<String, Set<String>> = emptyMap()

        selections = selections.select(users, "CARS")
        selections = selections.select(users, "CYCLISTS")
        assertEquals(setOf("CARS", "CYCLISTS"), selections["ALLOWED_USERS"])

        selections = selections.select(users, "CARS")
        assertEquals(setOf("CYCLISTS"), selections["ALLOWED_USERS"])
    }

    @Test
    fun emptySelectionsAreOmittedFromTheStoredObservation() = runTest {
        val dao = FakeObservationDao()
        val vm = vmWith(dao)
        testScheduler.advanceUntilIdle()

        // WIDTH ends up selected-then-cleared, so it must not reach the stored values at all.
        vm.onSelect(width, "W_1")
        vm.onSelect(width, "W_1")
        vm.onSelect(users, "CARS")

        vm.submit()
        testScheduler.advanceUntilIdle()

        val stored = dao.inserted
        assertEquals(recordedAt.toEpochMilliseconds(), stored?.recordedAtEpochMs)
        assertEquals(
            mapOf("ALLOWED_USERS" to listOf("CARS")),
            stored?.valuesJson?.let { Json.decodeFromString(valuesSerializer, it) },
        )
    }

    @Test
    fun successfulSubmitClearsSelections() = runTest {
        val vm = vmWith(FakeObservationDao())
        testScheduler.advanceUntilIdle()

        vm.onSelect(users, "CARS")
        vm.submit()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(emptyMap(), ready.selections)
        assertEquals(SubmitState.Idle, ready.submitState)
    }
}
