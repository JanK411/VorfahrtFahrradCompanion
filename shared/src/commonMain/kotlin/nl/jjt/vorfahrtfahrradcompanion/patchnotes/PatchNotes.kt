package nl.jjt.vorfahrtfahrradcompanion.patchnotes

/** Newest first. Prepend a new entry on every user-visible change (see CLAUDE.md). */
val patchNotes: List<PatchNote> = listOf(
    PatchNote(
        version = "1.3",
        date = "2026-07-28",
        changes = listOf(
            "The app now only talks to servers over https — plain http is accepted for servers on your own network only.",
            "A base URL typed without http:// or https:// now defaults to https, unless it points at your own network.",
        ),
    ),
    PatchNote(
        version = "1.2",
        date = "2026-07-26",
        changes = listOf(
            "Observations are now saved on your device and submitting no longer waits for a GPS fix.",
        ),
    ),
    PatchNote(
        version = "1.1",
        date = "2026-07-25",
        changes = listOf(
            "The criterion catalogue now works offline: it is stored on your device and shown even when the server can't be reached.",
        ),
    ),
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
