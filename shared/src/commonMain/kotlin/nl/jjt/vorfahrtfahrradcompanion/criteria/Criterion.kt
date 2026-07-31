package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

data class Criterion(val id: String, val kind: CriterionKind, val values: List<String>)

enum class CriterionKind { SINGLE, MULTI }

data class Catalogue(val criteria: List<Criterion>)

/** The values chosen per criterion id. A criterion the rider has not touched is simply absent. */
@Serializable
@JvmInline
value class Selections(private val byCriterion: Map<String, Set<String>> = emptyMap()) {

    operator fun get(criterion: Criterion): Set<String> = byCriterion[criterion.id].orEmpty()

    /**
     * Applies a chip tap. [CriterionKind] is the only thing that differs between criteria, which is what
     * lets one screen render a catalogue it has never seen.
     */
    fun select(criterion: Criterion, value: String): Selections {
        val current = get(criterion)
        val next = when (criterion.kind) {
            CriterionKind.SINGLE -> if (value in current) emptySet() else setOf(value)
            CriterionKind.MULTI -> if (value in current) current - value else current + value
        }
        return Selections(byCriterion + (criterion.id to next))
    }

    /**
     * Takes a pick from the menu a folded card opens: for a single-choice criterion the value picked
     * becomes the one it holds, for a multi-choice one it goes on or comes off the set.
     *
     * Unlike [select], picking what is already there is no way to end up with nothing: that menu
     * starts the next segment, and a criterion left empty is one that segment cannot be described by.
     */
    fun pick(criterion: Criterion, value: String): Selections = when (criterion.kind) {
        CriterionKind.SINGLE -> Selections(byCriterion + (criterion.id to setOf(value)))
        CriterionKind.MULTI -> select(criterion, value)
    }

    /** The criteria actually holding a value — everything a segment ending now could be described by. */
    val filled: Set<String> get() = byCriterion.filterValues(Set<String>::isNotEmpty).keys

    /** Drops criteria the rider deselected back to nothing, so they never reach storage. */
    fun compact(): Selections = Selections(byCriterion.filterValues(Set<String>::isNotEmpty))

    /** Narrows to [criterionIds] — used to store only what the rider confirmed for this segment. */
    fun retain(criterionIds: Set<String>): Selections =
        Selections(byCriterion.filterKeys { it in criterionIds })

    fun isEmpty(): Boolean = byCriterion.values.all(Set<String>::isEmpty)
}
