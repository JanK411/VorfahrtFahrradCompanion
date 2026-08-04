package nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment

import kotlinx.coroutines.flow.*
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionValue
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Selections
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredSelections
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ObservationStore
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideRecorder
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Owns the segment currently being recorded and hands the finished ones to [store].
 * The draft lives here rather than in the ViewModel because a ViewModel does not survive
 * a bottom-bar tab switch; it is still memory only and does not outlive the process.
 */
class SegmentRecorder(
    private val store: ObservationStore,
    private val rides: RideRecorder,
    private val clock: Clock = Clock.System,
) {
    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    /**
     * What the last change made to the whole carried-over list replaced, held for as long as the
     * rider might take it back — see [clearCarriedOver] and [approveAll].
     */
    private var replaced: Draft? = null

    /** What the last segment stored was described with — what [preselect] fills a fresh one in from. */
    val lastSubmitted: Flow<StoredSelections?> = store.lastValues()

    /** The clock this recorder stamps boundaries with, for a caller that has to mark one early. */
    val now: Instant get() = clock.now()

    /**
     * Applies a tap on [value]. The first tap on a value still carried over from the previous segment only
     * approves it: the rider is standing by what is already there, not toggling it off. Every other tap
     * selects, and either way the criterion counts as approved from then on — so a second tap does toggle.
     */
    fun tap(criterion: Criterion, value: CriterionValue) = _draft.update {
        val standingBy = criterion !in it.approved && value in it.selections[criterion]
        it.copy(
            selections = if (standingBy) it.selections else it.selections.select(criterion, value),
            approved = it.approved + criterion,
        )
    }

    /**
     * Fills the open segment in with [values], unapproved — where the values carried over from the
     * previous segment land, so the rider approves or changes them one line at a time. Meant for a
     * segment with nothing filled in yet: it replaces whatever is there, and drops any undo left
     * behind, which describes a draft that no longer exists.
     */
    fun preselect(values: Selections) {
        replaced = null
        _draft.update {
            if (it.segment !is Segment.Open) it else it.copy(selections = values, approved = emptySet())
        }
    }

    /** Stands by [criterion] as it is, without touching its values. */
    fun approve(criterion: Criterion) = _draft.update { it.copy(approved = it.approved + criterion) }

    /**
     * Settles every criterion still carried over in one go: those in [approve] are stood by, the rest
     * lose their values. This is what the rider answers on their way out of a segment.
     *
     * [drop] is turned down explicitly, which takes back a criterion the rider settled while answering —
     * changing a value in the question approves it there and then, and rejecting it afterwards has to
     * undo that.
     */
    fun resolveCarriedOver(approve: Set<Criterion>, drop: Set<Criterion> = emptySet()) = _draft.update {
        val approved = it.approved + approve - drop
        it.copy(selections = it.selections.retain(approved), approved = approved)
    }

    /**
     * Drops every value the rider has not approved, leaving those criteria open to be answered from
     * scratch — for the stretch that has nothing in common with the last one. What they have already
     * approved for this segment is their own work and stays. Reversible via [undo].
     */
    fun clearCarriedOver() {
        replaced = _draft.value
        _draft.update { it.copy(selections = it.selections.retain(it.approved)) }
    }

    /**
     * Stands by every value in the draft at once — the other way out of a carried-over list, for the
     * stretch that is exactly like the one before it and needs no line-by-line nod. Reversible via
     * [undo].
     */
    fun approveAll() {
        replaced = _draft.value
        _draft.update { it.copy(approved = it.approved + it.selections.filled) }
    }

    /**
     * Describes the open segment as the last one with [criterion] changed to [value], and stands by
     * all of it — which is precisely what a rider says by picking that value off a folded card: this
     * one thing is different now, the rest still holds.
     */
    fun carryOnWith(criterion: Criterion, value: CriterionValue) = _draft.update {
        val selections = it.selections.pick(criterion, value)
        it.copy(selections = selections, approved = it.approved + selections.filled)
    }

    /** Puts back what the last of those two replaced. Does nothing if there is nothing to take back. */
    fun undo() {
        val previous = replaced ?: return
        replaced = null
        _draft.update { it.copy(selections = previous.selections, approved = previous.approved) }
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
     *
     * [endedAt] defaults to now, but is passed explicitly when the rider was asked something on the way
     * out: the boundary is where they pressed the button, not where they finished answering.
     */
    suspend fun end(
        kind: BoundaryKind,
        action: SegmentAction,
        endedAt: Instant = clock.now(),
    ): SegmentOutcome? {
        val draft = _draft.value
        val open = draft.segment as? Segment.Open ?: return null
        val rideId = rides.openId ?: return null
        val values = draft.selections.retain(draft.approved).compact()

        // A boundary moved back for a missed moment must not land before the segment began.
        val boundary = maxOf(endedAt, open.startedAt)

        if (!values.isEmpty()) {
            store.insert(
                rideId = rideId,
                startedAt = open.startedAt,
                startKind = open.startKind,
                endedAt = boundary,
                endKind = kind,
                values = values,
            )
        }

        replaced = null
        _draft.update {
            if (action == SegmentAction.START_NEXT) {
                it.copy(segment = Segment.Open(boundary, kind), approved = emptySet())
            } else {
                Draft()
            }
        }

        return if (values.isEmpty()) SegmentOutcome.NOTHING_TO_STORE else SegmentOutcome.SAVED
    }
}
