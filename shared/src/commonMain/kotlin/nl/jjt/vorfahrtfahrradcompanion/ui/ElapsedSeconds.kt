package nl.jjt.vorfahrtfahrradcompanion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Whole seconds since [timestamp], re-read once a second for as long as it is composed. */
@Composable
fun secondsSince(timestamp: Instant): Long {
    val seconds by produceState(0L, timestamp) {
        while (true) {
            value = (Clock.System.now() - timestamp).inWholeSeconds
            delay(1.seconds)
        }
    }
    return seconds
}
