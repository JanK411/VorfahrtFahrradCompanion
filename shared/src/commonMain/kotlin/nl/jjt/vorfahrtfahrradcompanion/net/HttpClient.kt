package nl.jjt.vorfahrtfahrradcompanion.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The engine is bound per platform in DI; there is deliberately no `defaultRequest` base URL —
 * it is read from settings per call, so editing it takes effect without rebuilding the client.
 */
fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
