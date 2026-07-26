package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogueDto(val criteria: List<CriterionDto>)

@Serializable
internal data class CriterionDto(val id: String, val kind: String, val values: List<String>)

internal fun CatalogueDto.toDomain() = Catalogue(criteria.map { it.toDomain() })

internal fun CriterionDto.toDomain() = Criterion(
    id = id,
    kind = if (kind == "SINGLE") CriterionKind.SINGLE else CriterionKind.MULTI,
    values = values
)

internal fun Catalogue.toDto() = CatalogueDto(criteria.map { it.toDto() })

internal fun Criterion.toDto() = CriterionDto(
    id = id,
    kind = when (kind) {
        CriterionKind.SINGLE -> "SINGLE"
        CriterionKind.MULTI -> "MULTI"
    },
    values = values
)
