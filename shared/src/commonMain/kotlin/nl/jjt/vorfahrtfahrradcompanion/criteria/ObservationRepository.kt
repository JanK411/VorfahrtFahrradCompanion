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

/** What ending a segment did with it. */
enum class SegmentOutcome {
    SAVED,

    /** Ended with nothing approved, so there was nothing to describe the stretch with. */
    NOTHING_TO_STORE,
}

/**
 * What the rider has entered but not stored yet.
 *
 * [approved] holds the criteria the rider has stood by *for the current segment*. Selections outlive a
 * segment, this set does not: after an end every carried-over value is a suggestion again, and only what
 * the rider approves is stored. See [ObservationRepository.tap].
 */
data class Draft(
    val segment: Segment = Segment.Idle,
    val selections: Selections = Selections(),
    val approved: Set<String> = emptySet(),
)

/**
 * Persists observations locally and owns the segment currently being recorded.
 * The draft lives here rather than in the ViewModel because a ViewModel does not survive
 * a bottom-bar tab switch; it is still memory only and does not outlive the process.
 */
class ObservationRepository(
    private val dao: ObservationDao,
    private val rides: RideRepository,
    private val clock: Clock = Clock.System,
) {
    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    /**
     * Applies a tap on [value]. The first tap on a value still carried over from the previous segment only
     * approves it: the rider is standing by what is already there, not toggling it off. Every other tap
     * selects, and either way the criterion counts as approved from then on — so a second tap does toggle.
     */
    fun tap(criterion: Criterion, value: String) = _draft.update {
        val standingBy = criterion.id !in it.approved && value in it.selections[criterion]
        it.copy(
            selections = if (standingBy) it.selections else it.selections.select(criterion, value),
            approved = it.approved + criterion.id,
        )
    }

    /** Stands by [criterion] as it is, without touching its values. */
    fun approve(criterion: Criterion) = _draft.update { it.copy(approved = it.approved + criterion.id) }

    /** Marks the start of a segment. Ignored while one is already open. */
    fun start(kind: BoundaryKind) = _draft.update {
        if (it.segment is Segment.Open) it else it.copy(segment = Segment.Open(clock.now(), kind))
    }

    /**
     * Stores the open segment against the ride it was recorded during and reports what happened, or
     * null while idle — and likewise outside a ride: a stretch of path with no outing around it has
     * nothing to belong to.
     *
     * Only approved criteria are stored — a value the rider rode past without standing by describes the
     * previous stretch, not this one. A segment that ends with nothing approved would say nothing at all,
     * so it is discarded instead of stored empty; the boundary still holds, so [SegmentAction.START_NEXT]
     * opens the next segment either way.
     *
     * With [action] = [SegmentAction.START_NEXT] the next segment opens on the same instant and inherits
     * [kind] — it is the same boundary, so a late end means a late start too — and keeps the selections,
     * unapproved again, because consecutive stretches of path usually differ in only one criterion.
     * [SegmentAction.STOP] instead ends the survey: it leaves nothing behind for whatever the rider
     * describes next.
     */
    suspend fun end(kind: BoundaryKind, action: SegmentAction): SegmentOutcome? {
        val draft = _draft.value
        val open = draft.segment as? Segment.Open ?: return null
        val rideId = rides.openId ?: return null
        val endedAt = clock.now()
        val values = draft.selections.retain(draft.approved).compact()

        if (!values.isEmpty()) {
            dao.insert(
                ObservationEntity(
                    rideId = rideId,
                    startedAtEpochMs = open.startedAt.toEpochMilliseconds(),
                    startKind = open.startKind,
                    endedAtEpochMs = endedAt.toEpochMilliseconds(),
                    endKind = kind,
                    valuesJson = Json.encodeToString(values),
                ),
            )
        }

        _draft.update {
            if (action == SegmentAction.START_NEXT) {
                it.copy(segment = Segment.Open(endedAt, kind), approved = emptySet())
            } else {
                Draft()
            }
        }

        return if (values.isEmpty()) SegmentOutcome.NOTHING_TO_STORE else SegmentOutcome.SAVED
    }
}
