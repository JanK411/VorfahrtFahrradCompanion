package nl.jjt.vorfahrtfahrradcompanion.criteria

import nl.jjt.vorfahrtfahrradcompanion.location.Location

data class Criterion(val id: String, val kind: CriterionKind, val values: List<String>)

enum class CriterionKind { SINGLE, MULTI }

data class Catalogue(val criteria: List<Criterion>)

data class Observation(val location: Location, val values: Map<String, Set<String>>)
