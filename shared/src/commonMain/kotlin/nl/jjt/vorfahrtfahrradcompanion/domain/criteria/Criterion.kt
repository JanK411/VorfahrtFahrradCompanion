package nl.jjt.vorfahrtfahrradcompanion.domain.criteria

data class Criterion(val id: String, val kind: CriterionKind, val values: List<String>)

enum class CriterionKind { SINGLE, MULTI }
