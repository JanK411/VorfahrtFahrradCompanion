package nl.jjt.vorfahrtfahrradcompanion.testing

import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideStore
import kotlin.time.Instant

/** Keeps rides in a list, minting the ids the way the real store does — readable ones, so a failure names them. */
class FakeRideStore : RideStore {
    data class Row(
        val id: String,
        val startedAt: Instant,
        val endedAt: Instant? = null,
        val name: String? = null,
    )

    val rows = mutableListOf<Row>()
    private var minted = 0

    override suspend fun open(startedAt: Instant): String {
        val id = "ride-${++minted}"
        rows += Row(id, startedAt)
        return id
    }

    override suspend fun close(id: String, endedAt: Instant, name: String?) {
        val at = rows.indexOfFirst { it.id == id }
        if (at >= 0) rows[at] = rows[at].copy(endedAt = endedAt, name = name)
    }

    override suspend fun delete(id: String) {
        rows.removeAll { it.id == id }
    }
}
