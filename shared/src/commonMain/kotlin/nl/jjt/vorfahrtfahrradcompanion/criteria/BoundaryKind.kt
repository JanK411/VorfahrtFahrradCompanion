package nl.jjt.vorfahrtfahrradcompanion.criteria

/**
 * Where the boundary of a segment really lies. A rider cannot always press the button at the exact
 * moment the path changes, so [EARLIER] records that the segment already started (or ended) some time
 * before the timestamp that was captured.
 */
enum class BoundaryKind { EXACT, EARLIER }
