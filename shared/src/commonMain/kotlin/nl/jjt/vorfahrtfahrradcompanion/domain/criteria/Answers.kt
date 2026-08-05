package nl.jjt.vorfahrtfahrradcompanion.domain.criteria

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The values chosen per criterion. A criterion the rider has not touched is simply absent.
 *
 * ```
 * val width = Criterion("WIDTH", CriterionKind.SINGLE)
 * val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI)
 * val (w1, w2) = CriterionValue("W_1") to CriterionValue("W_2")
 * val (cars, cyclists) = CriterionValue("CARS") to CriterionValue("CYCLISTS")
 *
 * Answers()
 *     .select(width, w1)       // WIDTH -> [W_1]
 *     .select(width, w2)       // WIDTH -> [W_2]             pick-one replaces
 *     .select(users, cars)     // USERS -> [CARS]
 *     .select(users, cyclists) // USERS -> [CARS, CYCLISTS]  pick-any adds
 *     .select(users, cars)     // USERS -> [CYCLISTS]        tapping again removes
 * ```
 */
@JvmInline
value class Answers(private val byCriterion: Map<Criterion, Set<CriterionValue>> = emptyMap()) {

    operator fun get(criterion: Criterion): Set<CriterionValue> = byCriterion[criterion].orEmpty()

    /**
     * Applies a chip tap. [CriterionKind] is the only thing that differs between criteria, which is what
     * lets one screen render a catalogue it has never seen.
     */
    fun select(criterion: Criterion, value: CriterionValue): Answers {
        val current = get(criterion)
        val next = when (criterion.kind) {
            CriterionKind.SINGLE -> if (value in current) emptySet() else setOf(value)
            CriterionKind.MULTI -> if (value in current) current - value else current + value
        }
        return Answers(byCriterion + (criterion to next))
    }

    /**
     * Creates a new [Answers] object that is the same as the current one but with one criterion changed to the new value.
     */
    fun carryOnWith(criterion: Criterion, value: CriterionValue): Answers =
        Answers(byCriterion + (criterion to setOf(value)))

    /** The criteria actually holding a value — everything a segment ending now could be described by. */
    val filled: Set<Criterion> get() = byCriterion.filterValues(Set<CriterionValue>::isNotEmpty).keys

    /** Drops criteria the rider deselected back to nothing, so they never reach storage. */
    fun compact(): Answers = Answers(byCriterion.filterValues(Set<CriterionValue>::isNotEmpty))

    /** Narrows to [criteria] — used to store only what the rider approved for this segment. */
    fun retain(criteria: Set<Criterion>): Answers =
        Answers(byCriterion.filterKeys { it in criteria })

    fun isEmpty(): Boolean = byCriterion.values.all(Set<CriterionValue>::isEmpty)

    /** How storage holds these, having no use for the kinds. Empty criteria are dropped on the way. */
    fun stored(): StoredAnswers = StoredAnswers(
        byCriterion.filterValues(Set<CriterionValue>::isNotEmpty).mapKeys { it.key.id },
    )
}

/**
 * Answers as the observations table holds them: criterion ids and values, with nothing to say what
 * kind of question each id was. A stored segment outlives the catalogue that described it, so reading
 * one back means asking the catalogue what those ids are now — see [Catalogue.resolve].
 */
@Serializable
@JvmInline
value class StoredAnswers(private val byCriterionId: Map<String, Set<CriterionValue>> = emptyMap()) {

    operator fun get(criterionId: String): Set<CriterionValue>? = byCriterionId[criterionId]

    /**
     * All of them, for whatever has to walk the answers rather than ask after one — the wire format
     * being the reason this exists. Still id-keyed: nothing here knows what kind of question each was.
     */
    val byId: Map<String, Set<CriterionValue>> get() = byCriterionId

    fun isEmpty(): Boolean = byCriterionId.isEmpty()
}
