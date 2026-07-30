package nl.jjt.vorfahrtfahrradcompanion.patchnotes

/** Newest first. Prepend a new entry on every user-visible change (see CLAUDE.md). */
val patchNotes: List<PatchNote> = listOf(
    PatchNote(
        version = "1.5",
        date = "2026-07-30",
        changes = listOf(
            "The criteria screen is built for riding now: big buttons, bigger type, and a tap you can feel.",
            "Answer the criterion at the top and the next one scrolls to you — no more reaching for the screen to scroll. Skip one and it stays skipped: the flow only ever moves down the list, and every new segment starts back at the top.",
            "Answered criteria collapse to a single line, so the list gets shorter as you fill it in. Tap one to change it again.",
            "A segment that inherits the last one's answers opens with everything folded: each criterion shows the value it carried over, with a big check to approve it — which brings the next one up to your thumb. Tapping the criterion anywhere else opens it up to pick something else, which moves you on just the same.",
            "Ending a segment while anything is still unapproved asks first: it lists those entries and lets you keep or drop each one — or all of them at once — before the segment is stored.",
            "The boundary is stamped the moment you press End, so answering that question costs the recording nothing.",
            "Values only carry over through \"End, start next\", which continues along the same path. Plain \"End\" finishes the survey and leaves the next segment empty.",
            "Only what you confirmed is saved. A segment where you confirmed nothing is discarded instead of stored empty, and says so.",
            "A stretch you would rather not keep at all can be thrown away with the bin next to the recording time — it asks first, then stores nothing and starts the next segment from scratch.",
            "Multiple-choice criteria wait a moment before moving on, or move on right away with \"Next\".",
            "The screen no longer sleeps while a segment is running.",
            "Missed the moment? Hold the Start or End button instead of tapping it, and the boundary is marked as already passed. The two \"earlier\" buttons are gone, which leaves Start a full-width target and End half the screen — and a hold feels different from a tap, so you know which one you got without looking.",
            "A (?) next to the buttons explains that gesture in full.",
            "Holding End asks how long ago the boundary was: under 10 seconds puts the end 10 seconds back, longer than that throws the segment away — a stretch that ended somewhere unknown is worse than none. Holding Start needs no such question: it is stamped now and already marked as an imprecise start.",
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
