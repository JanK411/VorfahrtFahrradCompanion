package nl.jjt.vorfahrtfahrradcompanion.domain.criteria

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * One question the catalogue asks about a stretch of path: what it is called, and whether it takes
 * one answer or several. Which answers it offers is the [Catalogue]'s business, not this one's —
 * a criterion is what a selection is *about*, and stays the same however the values move.
 */
data class Criterion(val id: String, val kind: CriterionKind)

enum class CriterionKind { SINGLE, MULTI }

/**
 * One of the answers a criterion offers. [id] is the name the server knows it by — `W_0_5`, `CARS` —
 * which is what gets stored; it is not something to put in front of a rider as it stands.
 */
@Serializable
@JvmInline
value class CriterionValue(val id: String)
