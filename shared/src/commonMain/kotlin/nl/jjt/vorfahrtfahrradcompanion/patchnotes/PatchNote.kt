package nl.jjt.vorfahrtfahrradcompanion.patchnotes

/**
 * One entry in the app's user-facing changelog.
 *
 * [version] is the app release the note belongs to and doubles as the key we track "already seen"
 * against. [date] is a plain display string (no `kotlinx-datetime` dependency).
 */
data class PatchNote(
    val version: String,
    val date: String,
    val changes: List<String>,
)
