package nl.jjt.vorfahrtfahrradcompanion.service.http

import io.ktor.client.engine.HttpClientEngine

expect fun platformHttpClientEngine(): HttpClientEngine
