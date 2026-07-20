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

@Serializable
internal data class ObservationDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val recordedAt: String,
    val values: Map<String, List<String>>
)

internal fun Observation.toDto() = ObservationDto(
    latitude = location.latitude,
    longitude = location.longitude,
    accuracyMeters = location.accuracyMeters,
    recordedAt = location.timestamp.toString(),
    values = values.mapValues { it.value.toList() }
)
