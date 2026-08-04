package nl.jjt.vorfahrtfahrradcompanion.service.criteria

import kotlinx.serialization.Serializable
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionKind
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionValue

@Serializable
internal data class CatalogueDto(val criteria: List<CriterionDto>)

@Serializable
internal data class CriterionDto(val id: String, val kind: String, val values: List<String>)

internal fun CatalogueDto.toDomain() = Catalogue(criteria.associate { it.toDomain() })

/** The criterion and the values it offers — one thing on the wire, two in the domain. */
internal fun CriterionDto.toDomain(): Pair<Criterion, List<CriterionValue>> =
    Criterion(
        id = id,
        kind = if (kind == "SINGLE") CriterionKind.SINGLE else CriterionKind.MULTI,
    ) to values.map(::CriterionValue)

internal fun Catalogue.toDto() = CatalogueDto(criteria.map { it.toDto(this[it]) })

internal fun Criterion.toDto(values: List<CriterionValue>) = CriterionDto(
    id = id,
    kind = when (kind) {
        CriterionKind.SINGLE -> "SINGLE"
        CriterionKind.MULTI -> "MULTI"
    },
    values = values.map(CriterionValue::id),
)
