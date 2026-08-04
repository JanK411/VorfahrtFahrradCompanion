package nl.jjt.vorfahrtfahrradcompanion.domain.criteria

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The values chosen per criterion id. A criterion the rider has not touched is simply absent.
 *
 * ```
 * val width = Criterion("WIDTH", CriterionKind.SINGLE, listOf("W_1", "W_2"))
 * val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI, listOf("CARS", "CYCLISTS"))
 *
 * Selections()
 *     .select(width, "W_1")      // WIDTH  -> [W_1]
 *     .select(width, "W_2")      // WIDTH  -> [W_2]        pick-one replaces
 *     .select(users, "CARS")     // USERS  -> [CARS]
 *     .select(users, "CYCLISTS") // USERS  -> [CARS, CYCLISTS]  pick-any adds
 *     .select(users, "CARS")     // USERS  -> [CYCLISTS]        tapping again removes
 * ```
 */
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
     * Takes a pick from the menu a folded card opens: the value picked becomes the one the
     * criterion holds.
     *
     * Only a pick-one criterion offers that menu — a pick-any one has no single answer to slide
     * onto — so unlike [select] this never leaves a criterion empty, which matters because the
     * pick starts the next segment.
     */
    fun pick(criterion: Criterion, value: String): Selections =
        Selections(byCriterion + (criterion.id to setOf(value)))

    /** The criteria actually holding a value — everything a segment ending now could be described by. */
    val filled: Set<String> get() = byCriterion.filterValues(Set<String>::isNotEmpty).keys

    /** Drops criteria the rider deselected back to nothing, so they never reach storage. */
    fun compact(): Selections = Selections(byCriterion.filterValues(Set<String>::isNotEmpty))

    /** Narrows to [criterionIds] — used to store only what the rider approved for this segment. */
    fun retain(criterionIds: Set<String>): Selections =
        Selections(byCriterion.filterKeys { it in criterionIds })

    fun isEmpty(): Boolean = byCriterion.values.all(Set<String>::isEmpty)
}
