package nl.jjt.vorfahrtfahrradcompanion.testing

import nl.jjt.vorfahrtfahrradcompanion.domain.recording.RideStore
import kotlin.time.Instant

/** Keeps rides in a list, handing out ids the way the real table's autoincrement does. */
class FakeRideStore : RideStore {
    data class Row(
        val id: Long,
        val startedAt: Instant,
        val endedAt: Instant? = null,
        val name: String? = null,
    )

    val rows = mutableListOf<Row>()
    private var nextId = 1L

    override suspend fun open(startedAt: Instant): Long {
        val id = nextId++
        rows += Row(id, startedAt)
        return id
    }

    override suspend fun close(id: Long, endedAt: Instant, name: String?) {
        val at = rows.indexOfFirst { it.id == id }
        if (at >= 0) rows[at] = rows[at].copy(endedAt = endedAt, name = name)
    }

    override suspend fun delete(id: Long) {
        rows.removeAll { it.id == id }
    }
}
