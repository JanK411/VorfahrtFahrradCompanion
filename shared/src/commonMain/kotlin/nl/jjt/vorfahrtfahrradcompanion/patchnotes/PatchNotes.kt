package nl.jjt.vorfahrtfahrradcompanion.patchnotes

/** Newest first. Prepend a new entry on every user-visible change (see CLAUDE.md). */
val patchNotes: List<PatchNote> = listOf(
    PatchNote(
        version = "1.6",
        date = "2026-07-31",
        changes = listOf(
            "Clearing the app's cache in the Android settings now also drops the stored criterion catalogue, so the next start fetches it fresh. Your observations and settings are untouched.",
        ),
    ),
    PatchNote(
        version = "1.5",
        date = "2026-07-30",
        changes = listOf(
            "The criteria screen is built for riding now: big buttons, bigger type, and a tap you can feel.",
            "Answer the criterion at the top and the next one scrolls to you — no more reaching for the screen to scroll. Every new segment starts back at the top.",
            "Answered criteria collapse to a single line, so the list gets shorter as you fill it in. Tap one to change it again.",
            "A segment that inherits the last one's answers opens with everything folded: each criterion shows the value it carried over, with a big check to approve it — which brings the next one up to your thumb. Tapping the criterion anywhere else opens it up to pick something else, which moves you on just the same.",
            "\"Approve all\" takes the whole lot in one tap. \"Discard\" drops everything still unapproved so you can fill those in fresh — with an \"Undo\" in case you hit it by accident.",
            "Values only carry over through \"End now, start next\", which continues along the same path. Plain \"End now\" finishes the survey and leaves the next segment empty.",
            "Only what you confirmed is saved. A segment where you confirmed nothing is discarded instead of stored empty, and says so.",
            "Multiple-choice criteria wait a moment before moving on, or move on right away with \"Next\".",
            "The screen no longer sleeps while a segment is running.",
            "The \"earlier\" buttons moved behind \"Missed the moment?\", leaving the buttons you actually press big.",
        ),
    ),
    PatchNote(
        version = "1.4",
        date = "2026-07-28",
        changes = listOf(
            "The app now only talks to servers over https, and a base URL typed without a scheme defaults to https.",
        ),
    ),
    PatchNote(
        version = "1.3",
        date = "2026-07-28",
        changes = listOf(
            "An observation now covers a whole segment: mark where it starts, mark where it ends.",
            "Missed the exact moment? Use the amber \"Started earlier\" / \"Ended earlier\" buttons and the segment is stored as having begun or ended before you pressed.",
            "\"End now, start next\" closes one segment and opens the next on the same spot, for surveying a path in one go.",
            "Your selections stay put when a segment ends, so you only change what actually differs.",
            "The criteria are only shown while a segment is running — before you start, the screen stays out of the way.",
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
