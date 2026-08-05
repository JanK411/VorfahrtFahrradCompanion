package nl.jjt.vorfahrtfahrradcompanion.service.ride

import kotlinx.serialization.Serializable
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionValue
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.StoredObservation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide

/**
 * One ride and everything recorded during it, as it goes over the wire.
 *
 * The types are deliberately dumb — strings for timestamps and enums, a plain map for the answers —
 * so that renaming something in the domain cannot silently change the protocol. Same reasoning as
 * `service/criteria/CatalogueDto`, which keeps `kind` a String rather than the enum.
 *
 * [rideId] is the ride's own id, minted on the device when the ride opened. Nothing is generated for
 * the upload, so sending the same ride twice sends the same id twice, and the server can tell.
 */
@Serializable
internal data class RideUploadDto(
    val rideId: String,
    val startedAt: String,
    val endedAt: String,
    val name: String? = null,
    val observations: List<ObservationDto>,
)

/**
 * [answers] is keyed by criterion id, with the value ids the rider chose — exactly the names
 * `GET /admin/evaluation-model/criterion-catalogue` serves, which is where the app got them.
 *
 * A list rather than a set because JSON has no sets; the criterion decides whether more than one value
 * is meaningful, and the server knows that from its own catalogue.
 */
@Serializable
internal data class ObservationDto(
    val startedAt: String,
    val startKind: String,
    val endedAt: String,
    val endKind: String,
    val answers: Map<String, List<String>>,
)

/**
 * The ride as a payload. Only a finished ride can be sent — an open one has no end to report — which
 * the screen already enforces by refusing to send one.
 */
internal fun RecordedRide.toDto(observations: List<StoredObservation>): RideUploadDto {
    val ended = requireNotNull(endedAt) { "a ride that has not ended cannot be sent" }
    return RideUploadDto(
        rideId = id,
        startedAt = startedAt.toString(),
        endedAt = ended.toString(),
        name = name,
        observations = observations.map(StoredObservation::toDto),
    )
}

internal fun StoredObservation.toDto() = ObservationDto(
    startedAt = startedAt.toString(),
    startKind = startKind.name,
    endedAt = endedAt.toString(),
    endKind = endKind.name,
    answers = answers.byId.mapValues { (_, values) -> values.map(CriterionValue::id) },
)
