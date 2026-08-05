package nl.jjt.vorfahrtfahrradcompanion.service.ride

import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.first
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsStore
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.StoredObservation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.service.http.normalizeBaseUrl

/**
 * Posts a ride to the companion app's own endpoint, `POST {base}/companion-app/rides`. That group
 * exists for this client rather than for a resource: what it sends is a ride and its observations
 * denormalized into one envelope, which nothing in the admin API has a counterpart for.
 *
 * Anything but a 2xx is a failure and says so — what the server does with a ride it has already seen
 * is its own business, and needs no opinion here.
 */
class KtorRideApi(
    private val client: HttpClient,
    private val settings: SettingsStore,
) : RideApi {

    override suspend fun upload(ride: RecordedRide, observations: List<StoredObservation>) {
        val s = settings.settings.first()
        val response = client.post {
            url {
                takeFrom(normalizeBaseUrl(s.baseUrl) ?: s.baseUrl)
                appendPathSegments("companion-app", "rides")
            }
            basicAuth(s.username, s.password)
            contentType(ContentType.Application.Json)
            setBody(ride.toDto(observations))
        }

        if (!response.status.isSuccess()) error("Server answered ${response.status}")
    }
}
