package nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment

/** What ending a segment did with it. */
enum class SegmentOutcome {
    SAVED,

    /** Ended with nothing approved, so there was nothing to describe the stretch with. */
    NOTHING_TO_STORE,

    /** Thrown away by the rider. */
    DISCARDED,

    /** Ended so long after the boundary that there was no saying where the stretch ended. */
    TOO_LATE,
}
