package nl.jjt.vorfahrtfahrradcompanion.net

import io.ktor.client.engine.HttpClientEngine

expect fun platformHttpClientEngine(): HttpClientEngine
