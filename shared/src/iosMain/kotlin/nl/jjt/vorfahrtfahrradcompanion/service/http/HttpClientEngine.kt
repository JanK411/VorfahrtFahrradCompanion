package nl.jjt.vorfahrtfahrradcompanion.service.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun platformHttpClientEngine(): HttpClientEngine = Darwin.create()
