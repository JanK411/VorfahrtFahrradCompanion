package nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment

import kotlin.time.Instant

/** The segment being recorded right now, if any. */
sealed interface Segment {
    data object Idle : Segment
    data class Open(val startedAt: Instant, val startKind: BoundaryKind) : Segment
}
