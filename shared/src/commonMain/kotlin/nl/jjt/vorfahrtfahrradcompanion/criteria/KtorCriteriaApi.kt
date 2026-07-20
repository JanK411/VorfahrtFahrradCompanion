package nl.jjt.vorfahrtfahrradcompanion.criteria

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.first
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsRepository
import nl.jjt.vorfahrtfahrradcompanion.settings.normalizeBaseUrl

class KtorCriteriaApi(
    private val client: HttpClient,
    private val settings: SettingsRepository
) : CriteriaApi {

    override suspend fun catalogue(): Catalogue {
        val s = settings.settings.first()
        return client.get {
            url {
                takeFrom(normalizeBaseUrl(s.baseUrl) ?: s.baseUrl)
                appendPathSegments("admin", "evaluation-model", "criterion-catalogue")
            }
            basicAuth(s.username, s.password)
        }.body<CatalogueDto>().toDomain()
    }

    override suspend fun submit(o: Observation) {
        val s = settings.settings.first()
        client.post {
            url {
                takeFrom(normalizeBaseUrl(s.baseUrl) ?: s.baseUrl)
                appendPathSegments("admin", "evaluation-model", "observations")
            }
            basicAuth(s.username, s.password)
            contentType(ContentType.Application.Json)
            setBody(o.toDto())
        }
    }
}
