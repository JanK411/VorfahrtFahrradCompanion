package nl.jjt.vorfahrtfahrradcompanion.patchnotes

/** Newest first. Prepend a new entry on every user-visible change (see CLAUDE.md). */
val patchNotes: List<PatchNote> = listOf(
    PatchNote(
        version = "1.0",
        date = "2026-07-21",
        changes = listOf(
            "Added a What's New page so you can follow app updates.",
        ),
    ),
)

/**
 * Splits [all] (newest first) into `(new, older)` around [lastSeen]: notes above the last-seen
 * version are new, the rest are older. A `null` or unknown [lastSeen] treats everything as new —
 * so a fresh install shows the full history and can't silently swallow notes.
 */
fun splitPatchNotes(
    all: List<PatchNote>,
    lastSeen: String?,
): Pair<List<PatchNote>, List<PatchNote>> {
    val seenIndex = lastSeen?.let { v -> all.indexOfFirst { it.version == v } }?.takeIf { it >= 0 }
        ?: return all to emptyList()
    return all.subList(0, seenIndex) to all.subList(seenIndex, all.size)
}
