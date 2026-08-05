package nl.jjt.vorfahrtfahrradcompanion.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideStore
import kotlin.time.Instant

/** Keeps rides in a list, minting the ids the way the real store does — readable ones, so a failure names them. */
class FakeRideStore : RideStore {
    data class Row(
        val id: String,
        val startedAt: Instant,
        val endedAt: Instant? = null,
        val name: String? = null,
        val uploadedAt: Instant? = null,
    )

    private val state = MutableStateFlow<List<Row>>(emptyList())
    private var minted = 0

    /** How many segments each ride is to report — the real store counts them, this one is told. */
    var segmentsPerRide: Map<String, Int> = emptyMap()

    val rows: List<Row> get() = state.value

    override suspend fun open(startedAt: Instant): String {
        val id = "ride-${++minted}"
        state.value += Row(id, startedAt)
        return id
    }

    override suspend fun close(id: String, endedAt: Instant, name: String?) =
        update(id) { it.copy(endedAt = endedAt, name = name) }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override fun recorded(): Flow<List<RecordedRide>> = state.map { rows ->
        rows.sortedByDescending(Row::startedAt).map {
            RecordedRide(
                id = it.id,
                startedAt = it.startedAt,
                endedAt = it.endedAt,
                name = it.name,
                segments = segmentsPerRide[it.id] ?: 0,
                uploadedAt = it.uploadedAt,
            )
        }
    }

    override suspend fun markUploaded(id: String, at: Instant) = update(id) { it.copy(uploadedAt = at) }

    private fun update(id: String, change: (Row) -> Row) {
        state.value = state.value.map { if (it.id == id) change(it) else it }
    }
}
