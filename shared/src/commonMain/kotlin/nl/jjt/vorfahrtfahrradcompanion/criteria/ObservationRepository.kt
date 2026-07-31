package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    /** Thrown away by the rider. */
    DISCARDED,

    /** Ended so long after the boundary that there was no saying where the stretch ended. */
    TOO_LATE,
}

/**
 * What the rider has entered but not stored yet.
 *
 * [reviewed] holds the criteria the rider has stood by *for the current segment*. Selections outlive a
 * segment, this set does not: after an end every carried-over value is a suggestion again, and only what
 * the rider confirms is stored. See [ObservationRepository.tap].
 */
data class Draft(
    val segment: Segment = Segment.Idle,
    val selections: Selections = Selections(),
    val reviewed: Set<String> = emptySet(),
)

/**
 * Persists observations locally and owns the segment currently being recorded.
 * The draft lives here rather than in the ViewModel because a ViewModel does not survive
 * a bottom-bar tab switch; it is still memory only and does not outlive the process.
 */
class ObservationRepository(
    private val dao: ObservationDao,
    private val clock: Clock = Clock.System,
) {
    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    /**
     * What the last change made to the whole carried-over list replaced, held for as long as the
     * rider might take it back — see [clearCarriedOver] and [approveCarriedOver].
     */
    private var replaced: Draft? = null

    /** What the last segment stored was described with — what [preselect] fills a fresh one in from. */
    val lastSubmitted: Flow<Selections?> =
        dao.lastValuesJson().map { json -> json?.let { Json.decodeFromString<Selections>(it) } }

    /** The clock this repository stamps boundaries with, for a caller that has to mark one early. */
    val now: Instant get() = clock.now()

    /**
     * Applies a tap on [value]. The first tap on a value still carried over from the previous segment only
     * confirms it: the rider is approving what is already there, not toggling it off. Every other tap
     * selects, and either way the criterion counts as reviewed from then on — so a second tap does toggle.
     */
    fun tap(criterion: Criterion, value: String) = _draft.update {
        val approving = criterion.id !in it.reviewed && value in it.selections[criterion]
        it.copy(
            selections = if (approving) it.selections else it.selections.select(criterion, value),
            reviewed = it.reviewed + criterion.id,
        )
    }

    /**
     * Fills the open segment in with [values], unreviewed — the standing values carried over from the
     * previous segment have, so the rider approves or changes them one line at a time. Meant for a
     * segment with nothing filled in yet: it replaces whatever is there, and drops any undo left
     * behind, which describes a draft that no longer exists.
     */
    fun preselect(values: Selections) {
        replaced = null
        _draft.update {
            if (it.segment !is Segment.Open) it else it.copy(selections = values, reviewed = emptySet())
        }
    }

    /** Stands by [criterion] as it is, without touching its values. */
    fun confirm(criterion: Criterion) = _draft.update { it.copy(reviewed = it.reviewed + criterion.id) }

    /**
     * Settles every criterion still carried over in one go: those in [approve] are stood by, the rest
     * lose their values. This is what the rider answers on their way out of a segment.
     *
     * [drop] is turned down explicitly, which takes back a criterion the rider settled while answering —
     * changing a value in the question approves it there and then, and rejecting it afterwards has to
     * undo that.
     */
    fun resolveCarriedOver(approve: Set<String>, drop: Set<String> = emptySet()) = _draft.update {
        val reviewed = it.reviewed + approve - drop
        it.copy(selections = it.selections.retain(reviewed), reviewed = reviewed)
    }

    /**
     * Drops every value the rider has not approved, leaving those criteria open to be answered from
     * scratch — for the stretch that has nothing in common with the last one. What they have already
     * approved for this segment is their own work and stays. Reversible via [undo].
     */
    fun clearCarriedOver() {
        replaced = _draft.value
        _draft.update { it.copy(selections = it.selections.retain(it.reviewed)) }
    }

    /**
     * Stands by every criterion in [criterionIds] at once — the other way out of a carried-over
     * list, for the stretch that is exactly like the one before it and needs no line-by-line nod.
     * Reversible via [undo].
     */
    fun approveCarriedOver(criterionIds: Set<String>) {
        replaced = _draft.value
        _draft.update { it.copy(reviewed = it.reviewed + criterionIds) }
    }

    /** Puts back what the last of those two replaced. Does nothing if there is nothing to take back. */
    fun undo() {
        val previous = replaced ?: return
        replaced = null
        _draft.update { it.copy(selections = previous.selections, reviewed = previous.reviewed) }
    }

    /**
     * Throws the open segment away: nothing is stored, and nothing of it is carried into what comes
     * next. Reports whether there was anything to throw away.
     */
    fun discardSegment(): Boolean {
        if (_draft.value.segment !is Segment.Open) return false
        replaced = null
        _draft.value = Draft()
        return true
    }

    /** Marks the start of a segment. Ignored while one is already open. */
    fun start(kind: BoundaryKind) = _draft.update {
        if (it.segment is Segment.Open) it else it.copy(segment = Segment.Open(clock.now(), kind))
    }

    /**
     * Stores the open segment and reports what happened, or null while idle.
     *
     * Only confirmed criteria are stored — a value the rider rode past without approving describes the
     * previous stretch, not this one. A segment that ends with nothing confirmed would say nothing at all,
     * so it is discarded instead of stored empty; the boundary still holds, so [SegmentAction.START_NEXT]
     * opens the next segment either way.
     *
     * With [action] = [SegmentAction.START_NEXT] the next segment opens on the same instant and inherits
     * [kind] — it is the same boundary, so a late end means a late start too — and keeps the selections,
     * unreviewed again, because consecutive stretches of path usually differ in only one criterion.
     * [SegmentAction.STOP] instead ends the survey: it leaves nothing behind for whatever the rider
     * describes next.
     *
     * [endedAt] defaults to now, but is passed explicitly when the rider was asked something on the way
     * out: the boundary is where they pressed the button, not where they finished answering.
     */
    suspend fun end(kind: BoundaryKind, action: SegmentAction, endedAt: Instant = clock.now()): SegmentOutcome? {
        val draft = _draft.value
        val open = draft.segment as? Segment.Open ?: return null
        val values = draft.selections.retain(draft.reviewed).compact()

        // A boundary moved back for a missed moment must not land before the segment began.
        val boundary = maxOf(endedAt, open.startedAt)

        if (!values.isEmpty()) {
            dao.insert(
                ObservationEntity(
                    startedAtEpochMs = open.startedAt.toEpochMilliseconds(),
                    startKind = open.startKind,
                    endedAtEpochMs = boundary.toEpochMilliseconds(),
                    endKind = kind,
                    valuesJson = Json.encodeToString(values),
                ),
            )
        }

        replaced = null
        _draft.update {
            if (action == SegmentAction.START_NEXT) {
                it.copy(segment = Segment.Open(boundary, kind), reviewed = emptySet())
            } else {
                Draft()
            }
        }

        return if (values.isEmpty()) SegmentOutcome.NOTHING_TO_STORE else SegmentOutcome.SAVED
    }
}
