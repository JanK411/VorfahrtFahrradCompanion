package nl.jjt.vorfahrtfahrradcompanion.criteria

data class Criterion(val id: String, val kind: CriterionKind, val values: List<String>)

enum class CriterionKind { SINGLE, MULTI }

data class Catalogue(val criteria: List<Criterion>)
