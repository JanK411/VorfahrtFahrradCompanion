package nl.jjt.vorfahrtfahrradcompanion.patchnotes

/** Newest first. Prepend a new entry on every user-visible change (see CLAUDE.md). */
val patchNotes: List<PatchNote> = listOf(
    PatchNote(
        version = "1.7",
        date = "2026-08-04",
        changes = listOf(
            "The criteria screen is built for riding now: big buttons, bigger type, and a tap you " +
                "can feel. One criterion is open at a time; answer it and it folds to a single line " +
                "and brings the next one up to your thumb, so the list gets shorter as you fill it in " +
                "and there is nothing to scroll.",
            "The next one arrives second from the top rather than first, so what you just answered " +
                "stays in sight — a value hit wrongly on a bumpy road is put right where it still is.",
            "Skip a criterion and nothing drags you back to it. Once you reach the bottom of the list " +
                "the flow comes round for whatever you passed over. Every new segment starts at the top.",
            "Pick-any criteria wait a moment and then move on themselves, or take \"Done — next\".",
            "Only what you approve for the stretch you are on is saved. Values still carry over from " +
                "one segment to the next — consecutive stretches usually differ in one thing — but " +
                "they cross the boundary as suggestions, in an amber-outlined card with a big check " +
                "to approve it. Tapping a value that is already there approves it rather than " +
                "clearing it, so the common case is still one tap.",
            "A segment that inherited answers opens with everything folded, for reading rather than " +
                "filling in. \"✕ Clear n carried over\" leads that list and \"✓ Approve all n\" closes " +
                "it; both can be taken back from the message that follows.",
            "A segment where you approved nothing describes nothing, so it is discarded rather than " +
                "stored empty — and says so.",
            "Values now carry over only through \"Start next\", which continues along the same path. " +
                "A plain \"End\" finishes the survey and leaves the next segment empty.",
            "Starting one from scratch offers \"Copy the previous segment\": one tap fills it in from " +
                "whatever you last stored, ready to be approved line by line. It goes as soon as you " +
                "enter anything yourself.",
            "The two buttons under a running segment are \"End\" and \"Start next\", and pressing " +
                "either asks how well you caught the moment: ✓ Precisely, ← ~10 s late, or 🗑 Too " +
                "late. Precisely stores the end where you pressed, ~10 s late stores it ten seconds " +
                "back, and too late throws the segment away — a stretch that ended somewhere unknown " +
                "is worse than none.",
            "You can answer that without the dialog. Hold either button and the same three answers " +
                "fill the screen above your thumb, a third of it each; slide onto one and let go, in " +
                "one movement, without looking. Let go where you started and nothing happens.",
            "The boundary is stamped the moment you press, not when you finish answering, so taking " +
                "your time over it costs the recording nothing.",
            "The commonest boundary of all — the path carries on but for one thing — is now a single " +
                "gesture. Hold the green knob on the right-hand edge of a folded card, slide onto the " +
                "value that is true from here on, and let go. The segment ends there and the next one " +
                "opens described exactly as this one was, but for that value, with nothing to confirm.",
            "Ending with anything still carried over asks first, and every way out of that question " +
                "ends the segment: approve all, approve the ones you ticked, or drop all. A row that " +
                "is nearly right can be opened and changed there, which approves it in the same tap.",
            "A stretch you would rather not keep can be thrown away with the bin next to the " +
                "recording time. It asks first, then stores nothing and starts the next from scratch.",
            "Starting is \"Start precise\" for a segment that begins where you press, and \"Start " +
                "earlier\" for one you have already ridden onto.",
            "Criterion names are readable now: ALLOWED_USERS reads as \"Allowed users\".",
            "The app is dark throughout, and in daylight the one thing wanting an answer is lit " +
                "instead — a bright card against a dark screen is quicker to find than the right one " +
                "among a pageful of equally bright ones. The same goes for anything that asks you " +
                "something mid-ride. After dark nothing lights up: your eyes are set for the road.",
            "It no longer follows your phone's dark setting, which said nothing about whether it is " +
                "actually dark out. Night colours come at real sunset, worked out for the date and for " +
                "where you are — half past ten in June, half past four in December. No fix is " +
                "requested for it, and it stays light while there is none.",
            "The screen no longer sleeps while a segment is running, and the bottom tab bar is hidden " +
                "so there is more room for the criteria and no tab to hit by accident.",
            "It does dim itself after twenty seconds without a touch, and comes straight back on the " +
                "next one. It is never switched off, so there is no power button and no unlocking to " +
                "do at speed — and that first touch only wakes the screen, it does not press whatever " +
                "was under your thumb.",
        ),
    ),
    PatchNote(
        version = "1.6",
        date = "2026-08-03",
        changes = listOf(
            "Segments are now recorded into a ride: press \"Start ride\" before the first one, and every segment you mark belongs to that outing.",
            "\"End ride\" appears between segments and shows what the ride came to — when it began and ended, and how many segments it holds. Give it a name there if you want one, or save it without.",
            "Ending a ride with no segments in it throws it away rather than storing an empty one.",
        ),
    ),
    PatchNote(
        version = "1.5",
        date = "2026-07-31",
        changes = listOf(
            "Clearing the app's cache in the Android settings now also drops the stored criterion catalogue, so the next start fetches it fresh. Your observations and settings are untouched.",
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
