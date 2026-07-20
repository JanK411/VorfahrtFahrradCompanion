package nl.jjt.vorfahrtfahrradcompanion.settings

import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlin.coroutines.cancellation.CancellationException

sealed interface ConnectionTestResult {
    data object Ok : ConnectionTestResult
    data class Failed(val message: String) : ConnectionTestResult
}

/**
 * Probes the criterion-catalogue endpoint with the given credentials. Only the status matters —
 * the body is never read, so this stays independent of the catalogue's shape.
 */
class ConnectionTester(private val client: HttpClient) {

    suspend fun test(settings: Settings): ConnectionTestResult {
        val baseUrl = normalizeBaseUrl(settings.baseUrl)
            ?: return ConnectionTestResult.Failed("Invalid base URL")

        return try {
            val response = client.get {
                url {
                    takeFrom(baseUrl)
                    appendPathSegments("admin", "evaluation-model", "criterion-catalogue")
                }
                basicAuth(settings.username, settings.password)
            }
            when (response.status) {
                HttpStatusCode.OK -> ConnectionTestResult.Ok
                HttpStatusCode.Unauthorized -> ConnectionTestResult.Failed("Wrong username or password")
                else -> ConnectionTestResult.Failed("Server answered ${response.status}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ConnectionTestResult.Failed(e.message ?: "Endpoint not reachable")
        }
    }
}
