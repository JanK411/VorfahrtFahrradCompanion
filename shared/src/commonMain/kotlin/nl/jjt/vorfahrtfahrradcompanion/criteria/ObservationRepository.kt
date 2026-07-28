package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationEntity
import kotlin.time.Clock
import kotlin.time.Instant

/** The segment being recorded right now, if any. */
sealed interface Segment {
    data object Idle : Segment
    data class Open(val startedAt: Instant, val startKind: BoundaryKind) : Segment
}

/** What the rider has entered but not stored yet. */
data class Draft(val segment: Segment = Segment.Idle, val selections: Selections = Selections())

/**
 * Persists observations locally instead of sending them to the server, and owns the segment currently
 * being recorded. The draft lives here rather than in the ViewModel because a ViewModel does not survive
 * a bottom-bar tab switch; it is still memory only and does not outlive the process.
 */
class ObservationRepository(
    private val dao: ObservationDao,
    private val clock: Clock = Clock.System,
) {
    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    fun select(criterion: Criterion, value: String) =
        _draft.update { it.copy(selections = it.selections.select(criterion, value)) }

    /** Marks the start of a segment. Ignored while one is already open. */
    fun start(kind: BoundaryKind) = _draft.update {
        if (it.segment is Segment.Open) it else it.copy(segment = Segment.Open(clock.now(), kind))
    }

    /**
     * Stores the open segment. With [startNext] the next segment opens on the same instant and inherits
     * [kind] — it is the same boundary, so a late end means a late start too. Selections are kept either
     * way: consecutive stretches of path usually differ in only one criterion. Ignored while idle.
     */
    suspend fun end(kind: BoundaryKind, startNext: Boolean = false) {
        val open = _draft.value.segment as? Segment.Open ?: return
        val endedAt = clock.now()

        dao.insert(
            ObservationEntity(
                startedAtEpochMs = open.startedAt.toEpochMilliseconds(),
                startKind = open.startKind,
                endedAtEpochMs = endedAt.toEpochMilliseconds(),
                endKind = kind,
                valuesJson = Json.encodeToString(_draft.value.selections.compact()),
            ),
        )

        _draft.update {
            it.copy(segment = if (startNext) Segment.Open(endedAt, kind) else Segment.Idle)
        }
    }
}
