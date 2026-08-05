package nl.jjt.vorfahrtfahrradcompanion.service.ride

import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionValue
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredAnswers
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.StoredObservation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.BoundaryKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

private val json = Json { prettyPrint = true }

private fun answers(vararg pairs: Pair<String, List<String>>) =
    StoredAnswers(pairs.associate { (id, values) -> id to values.map(::CriterionValue).toSet() })

private val ride = RecordedRide(
    id = "3f2b1c8e-9a44-4c31-8b2d-77e5c0a1d9f6",
    startedAt = Instant.parse("2026-08-05T14:02:11Z"),
    endedAt = Instant.parse("2026-08-05T14:40:53Z"),
    name = "Kanaalweg noord",
    segments = 2,
    uploadedAt = null,
)

private val observations = listOf(
    StoredObservation(
        startedAt = Instant.parse("2026-08-05T14:02:11Z"),
        startKind = BoundaryKind.EXACT,
        endedAt = Instant.parse("2026-08-05T14:09:30Z"),
        endKind = BoundaryKind.EARLIER,
        answers = answers(
            "WIDTH" to listOf("W_2"),
            "SURFACE_KIND" to listOf("ASPHALT"),
            "ALLOWED_USERS" to listOf("CYCLISTS", "PEDESTRIANS"),
        ),
    ),
    StoredObservation(
        startedAt = Instant.parse("2026-08-05T14:09:30Z"),
        startKind = BoundaryKind.EARLIER,
        endedAt = Instant.parse("2026-08-05T14:40:53Z"),
        endKind = BoundaryKind.EXACT,
        answers = answers("WIDTH" to listOf("W_3"), "BARRIER_KIND" to listOf("PAINTED_LINE")),
    ),
)

class RideUploadDtoTest {

    /**
     * The payload, spelled out. This is the contract the backend will be written against, so it is
     * pinned whole rather than field by field: a change anywhere in it should fail here and be argued
     * about, not slip through because each part still looked reasonable on its own.
     */
    @Test
    fun aRideGoesOverTheWireAsOneEnvelope() {
        val encoded = json.encodeToString(RideUploadDto.serializer(), ride.toDto(observations))

        assertEquals(
            """
            {
                "rideId": "3f2b1c8e-9a44-4c31-8b2d-77e5c0a1d9f6",
                "startedAt": "2026-08-05T14:02:11Z",
                "endedAt": "2026-08-05T14:40:53Z",
                "name": "Kanaalweg noord",
                "observations": [
                    {
                        "startedAt": "2026-08-05T14:02:11Z",
                        "startKind": "EXACT",
                        "endedAt": "2026-08-05T14:09:30Z",
                        "endKind": "EARLIER",
                        "answers": {
                            "WIDTH": [
                                "W_2"
                            ],
                            "SURFACE_KIND": [
                                "ASPHALT"
                            ],
                            "ALLOWED_USERS": [
                                "CYCLISTS",
                                "PEDESTRIANS"
                            ]
                        }
                    },
                    {
                        "startedAt": "2026-08-05T14:09:30Z",
                        "startKind": "EARLIER",
                        "endedAt": "2026-08-05T14:40:53Z",
                        "endKind": "EXACT",
                        "answers": {
                            "WIDTH": [
                                "W_3"
                            ],
                            "BARRIER_KIND": [
                                "PAINTED_LINE"
                            ]
                        }
                    }
                ]
            }
            """.trimIndent(),
            encoded,
        )
    }

    /** A rider who did not name the ride sends no name, rather than an empty one. */
    @Test
    fun anUnnamedRideOmitsTheName() {
        val encoded = json.encodeToString(
            RideUploadDto.serializer(),
            ride.copy(name = null).toDto(emptyList()),
        )

        assertEquals(
            """
            {
                "rideId": "3f2b1c8e-9a44-4c31-8b2d-77e5c0a1d9f6",
                "startedAt": "2026-08-05T14:02:11Z",
                "endedAt": "2026-08-05T14:40:53Z",
                "observations": []
            }
            """.trimIndent(),
            encoded,
        )
    }

    /**
     * The ids are the server's own — criterion names from `CriterionId`, values from the criterion's
     * enum — so they cross unchanged. Nothing translates them on the way out.
     */
    @Test
    fun answersKeepTheIdsTheCatalogueServed() {
        val dto = ride.toDto(observations)

        assertEquals(
            mapOf(
                "WIDTH" to listOf("W_2"),
                "SURFACE_KIND" to listOf("ASPHALT"),
                "ALLOWED_USERS" to listOf("CYCLISTS", "PEDESTRIANS"),
            ),
            dto.observations.first().answers,
        )
    }

    @Test
    fun aRideThatHasNotEndedCannotBeSent() {
        assertFailsWith<IllegalArgumentException> { ride.copy(endedAt = null).toDto(observations) }
    }
}
