package nl.jjt.vorfahrtfahrradcompanion.ui.rides

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeRideStore
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeRideUploader
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val startedAt = Instant.parse("2026-08-05T14:02:11Z")
private val endedAt = Instant.parse("2026-08-05T14:40:53Z")
private val sentAt = Instant.parse("2026-08-05T15:10:00Z")

@OptIn(ExperimentalCoroutinesApi::class)
class RidesViewModelTest {

    private val rides = FakeRideStore()
    private val uploader = FakeRideUploader()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The state is a `WhileSubscribed` flow, so it stays at its initial value until something collects
     * it — the screen does, and here a background collector stands in for it.
     */
    private fun TestScope.viewModel(): RidesViewModel {
        val vm = RidesViewModel(rides, uploader)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        testScheduler.advanceUntilIdle()
        return vm
    }

    private suspend fun openRide() = rides.open(startedAt)

    private suspend fun finishedRide(): String =
        rides.open(startedAt).also { rides.close(it, endedAt, "Kanaalweg noord") }

    private suspend fun sentRide(): String = finishedRide().also { rides.markUploaded(it, sentAt) }

    private fun RidesViewModel.tap(id: String) =
        rideTapped(state.value.rides.first { it.id == id })

    @Test
    fun tappingARideStillBeingRiddenOnlySaysSo() = runTest {
        val id = openRide()
        val vm = viewModel()

        vm.tap(id)

        assertTrue(vm.state.value.prompt is RidePrompt.StillOpen)
    }

    /** Confirming the open-ride question has nothing to confirm — it is an explanation, not an offer. */
    @Test
    fun confirmingAnOpenRideSendsNothing() = runTest {
        val id = openRide()
        val vm = viewModel()

        vm.tap(id)
        vm.promptConfirmed()

        assertTrue(uploader.uploaded.isEmpty())
        assertNull(vm.state.value.prompt)
    }

    @Test
    fun tappingAFinishedRideAsksBeforeSending() = runTest {
        val id = finishedRide()
        val vm = viewModel()

        vm.tap(id)

        assertTrue(vm.state.value.prompt is RidePrompt.Send)
        assertTrue(uploader.uploaded.isEmpty())
    }

    @Test
    fun tappingAnAlreadySentRideAsksWhetherToSendItAgain() = runTest {
        val id = sentRide()
        val vm = viewModel()

        vm.tap(id)

        assertTrue(vm.state.value.prompt is RidePrompt.SendAgain)
    }

    @Test
    fun confirmingSendsTheRide() = runTest {
        val id = finishedRide()
        val vm = viewModel()

        vm.tap(id)
        vm.promptConfirmed()

        assertEquals(listOf(id), uploader.uploaded.map(RecordedRide::id))
        assertNull(vm.state.value.prompt)
    }

    @Test
    fun confirmingSendsAnAlreadySentRideAgain() = runTest {
        val id = sentRide()
        val vm = viewModel()

        vm.tap(id)
        vm.promptConfirmed()

        assertEquals(listOf(id), uploader.uploaded.map(RecordedRide::id))
    }

    @Test
    fun cancellingSendsNothing() = runTest {
        val id = finishedRide()
        val vm = viewModel()

        vm.tap(id)
        vm.promptDismissed()

        assertTrue(uploader.uploaded.isEmpty())
        assertNull(vm.state.value.prompt)
    }

    @Test
    fun aFailedSendSaysSoAndLeavesTheRideAlone() = runTest {
        val id = finishedRide()
        uploader.outcome = Result.failure(IllegalStateException("no route to host"))
        val vm = viewModel()

        vm.tap(id)
        vm.promptConfirmed()

        assertEquals("Could not send: no route to host", vm.state.value.message)
        assertNull(rides.rows.single().uploadedAt)
    }

    @Test
    fun aSentRideSaysSo() = runTest {
        val id = finishedRide()
        val vm = viewModel()

        vm.tap(id)
        vm.promptConfirmed()

        assertEquals("Ride sent", vm.state.value.message)
    }

    @Test
    fun aMessageIsShownOnlyOnce() = runTest {
        val id = finishedRide()
        val vm = viewModel()

        vm.tap(id)
        vm.promptConfirmed()
        vm.messageShown()

        assertNull(vm.state.value.message)
    }

    /** Nothing is in flight by the time the send has answered, so the row is tappable again. */
    @Test
    fun aFinishedSendLeavesNothingInFlight() = runTest {
        val id = finishedRide()
        val vm = viewModel()

        vm.tap(id)
        vm.promptConfirmed()

        assertTrue(vm.state.value.sending.isEmpty())
    }
}
