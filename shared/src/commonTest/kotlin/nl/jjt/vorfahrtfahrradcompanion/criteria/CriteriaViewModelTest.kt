package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.jjt.vorfahrtfahrradcompanion.location.Location
import nl.jjt.vorfahrtfahrradcompanion.location.LocationProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val width = Criterion("WIDTH", CriterionKind.SINGLE, listOf("W_1", "W_2"))
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI, listOf("CARS", "CYCLISTS"))
private val catalogue = Catalogue(listOf(width, users))

private val fix = Location(52.1, 4.3, 8.0f, null, null, Instant.parse("2026-07-20T12:43:37Z"))

private class FakeApi : CriteriaApi {
    var submitted: Observation? = null
    override suspend fun catalogue() = catalogue
    override suspend fun submit(o: Observation) {
        submitted = o
    }
}

private class FakeLocationProvider : LocationProvider {
    override fun locations(intervalMillis: Long): Flow<Location> = flowOf(fix)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CriteriaViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

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
    fun emptySelectionsAreOmittedFromTheSubmittedBody() = runTest {
        val api = FakeApi()
        val vm = CriteriaViewModel(api, FakeLocationProvider())
        testScheduler.advanceUntilIdle()

        // WIDTH ends up selected-then-cleared, so it must not reach the body at all.
        vm.onSelect(width, "W_1")
        vm.onSelect(width, "W_1")
        vm.onSelect(users, "CARS")

        vm.submit()
        testScheduler.advanceUntilIdle()

        assertEquals(mapOf("ALLOWED_USERS" to setOf("CARS")), api.submitted?.values)
        assertEquals(fix, api.submitted?.location)
    }

    @Test
    fun successfulSubmitClearsSelections() = runTest {
        val vm = CriteriaViewModel(FakeApi(), FakeLocationProvider())
        testScheduler.advanceUntilIdle()

        vm.onSelect(users, "CARS")
        vm.submit()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(emptyMap(), ready.selections)
        assertEquals(SubmitState.Idle, ready.submitState)
    }
}
