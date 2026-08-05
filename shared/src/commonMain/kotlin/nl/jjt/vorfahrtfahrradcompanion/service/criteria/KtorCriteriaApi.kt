package nl.jjt.vorfahrtfahrradcompanion.service.criteria

import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.first
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue
import nl.jjt.vorfahrtfahrradcompanion.service.http.requireConfigured
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsStore

/**
 * Fetches the criterion catalogue. A server that was never configured fails before the request, so
 * the empty Settings screen is what the failure names rather than an unreachable host.
 */
class KtorCriteriaApi(
    private val client: HttpClient,
    private val settings: SettingsStore
) : CriteriaApi {

    override suspend fun catalogue(): Catalogue {
        val s = settings.settings.first().requireConfigured()
        return client.get {
            url {
                takeFrom(s.baseUrl)
                appendPathSegments("admin", "evaluation-model", "criterion-catalogue")
            }
            basicAuth(s.username, s.password)
        }.body<CatalogueDto>().toDomain()
    }
}
