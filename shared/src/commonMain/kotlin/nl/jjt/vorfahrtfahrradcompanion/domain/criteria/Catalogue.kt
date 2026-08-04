package nl.jjt.vorfahrtfahrradcompanion.domain.criteria

/**
 * Every criterion a segment can be described by, each with the values it offers.
 *
 * The map is what makes a [Criterion] worth passing around on its own: the question travels through
 * the recording as a key, and only the screen that draws the buttons has to ask what may be answered.
 */
data class Catalogue(private val byCriterion: Map<Criterion, List<CriterionValue>> = emptyMap()) {

    /** In the order the server sent them, which is the order the rider is asked them in. */
    val criteria: List<Criterion> = byCriterion.keys.toList()

    /** What [criterion] may be answered with, or nothing at all if this catalogue never heard of it. */
    operator fun get(criterion: Criterion): List<CriterionValue> = byCriterion[criterion].orEmpty()
}
