package nl.jjt.vorfahrtfahrradcompanion.patchnotes

/** Newest first. Prepend a new entry on every user-visible change (see CLAUDE.md). */
val patchNotes: List<PatchNote> = listOf(
    PatchNote(
        version = "1.9",
        date = "2026-07-31",
        changes = listOf(
            "The question asked when you end a segment with unapproved entries now gives each one its own two buttons: a tick to keep it and a cross to drop it. Whichever one is filled in is what happens when you end.",
            "An entry that is nearly right no longer costs you the whole criterion: tap it and it opens up with all its values, exactly as on the list. Picking one changes the entry and keeps it in one go — and crossing it out afterwards still drops it.",
            "Entries you can change carry the same pencil as the carried-over cards on the criteria list.",
        ),
    ),
    PatchNote(
        version = "1.8",
        date = "2026-07-31",
        changes = listOf(
            "The two buttons under a running segment are now \"End\" and \"Start next\".",
            "Pressing either of them asks how well you caught the moment, with three answers: you hit it precisely, you were about 10 seconds late, or you were later than that. Precisely stores the end where you pressed, ~10 seconds stores it 10 seconds back, and later than that throws the segment away — a stretch that ended somewhere unknown is worse than none. \"Keep recording\" takes the press back.",
            "The boundary is still stamped the moment you press, so answering that question costs the recording nothing.",
            "Holding a button instead of tapping it now raises all three answers above your thumb — up-left for precisely, straight up for ~10 seconds, up-right for later than that — and answering there skips the dialog entirely. Let go without having slid anywhere and nothing happens.",
            "The answers no longer hang off the edge of the screen: they sit across the middle of it, above whichever button you are holding.",
            "The \"Missed the moment?\" line and its (?) are gone from the screen, which gives the list of criteria more room. The same explanation now lives behind a (?) in the question itself.",
            "While a segment is running the bottom tab bar is hidden: more room for the criteria, and no tab to hit by accident. It comes back as soon as you end the segment.",
        ),
    ),
    PatchNote(
        version = "1.7",
        date = "2026-07-31",
        changes = listOf(
            "The question asked when you end a segment with unapproved entries now has four ways out, and each one is a full-width button: end keeping all of them, end keeping the ones still ticked, end dropping all of them, or go back to the segment. \"Keep all\" and \"Drop all\" used to only tick or untick the list and left you to press End afterwards — now they end the segment themselves.",
        ),
    ),
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
            "Answer the criterion at the top and the next one scrolls to you — no more reaching for the screen to scroll. Skip one and the flow carries on down the list without dragging you back; once you answer the last one it comes back round for whatever you skipped. Every new segment starts back at the top.",
            "Answered criteria collapse to a single line, so the list gets shorter as you fill it in. Tap one to change it again.",
            "A segment that inherits the last one's answers opens with everything folded: each criterion shows the value it carried over, with a big check to approve it — which brings the next one up to your thumb. Tapping the criterion anywhere else opens it up to pick something else, which moves you on just the same.",
            "A segment that starts out with preselected answers opens on \"Clear preselected\": one tap empties the lot so you can fill the stretch in from scratch, with an \"Undo\" in the snackbar. What you have already approved for this segment stays. It is the first entry of the list, so once you answer anything below it, it scrolls out of the way.",
            "Ending a segment while anything is still unapproved asks first: it lists those entries and lets you keep or drop each one — or all of them at once — before the segment is stored.",
            "The boundary is stamped the moment you press End, so answering that question costs the recording nothing.",
            "Values only carry over through \"End, start next\", which continues along the same path. Plain \"End\" finishes the survey and leaves the next segment empty.",
            "Only what you confirmed is saved. A segment where you confirmed nothing is discarded instead of stored empty, and says so.",
            "A stretch you would rather not keep at all can be thrown away with the bin next to the recording time — it asks first, then stores nothing and starts the next segment from scratch.",
            "Multiple-choice criteria wait a moment before moving on, or move on right away with \"Next\".",
            "The screen no longer sleeps while a segment is running.",
            "Missed the moment? Hold the Start or End button instead of tapping it, and the boundary is marked as already passed. The two \"earlier\" buttons are gone, which leaves Start a full-width target and End half the screen — and a hold feels different from a tap, so you know which one you got without looking.",
            "A (?) next to the buttons explains that gesture in full.",
            "Holding End raises two answers above your thumb: slide up-left for \"under 10 seconds\" and the end is stored 10 seconds earlier, up-right for \"longer\" and the segment is thrown away — a stretch that ended somewhere unknown is worse than none. Slide and let go in one movement, without looking; let go where you started and nothing happens.",
            "Holding Start needs no such answer: it is stamped now and already marked as an imprecise start.",
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
