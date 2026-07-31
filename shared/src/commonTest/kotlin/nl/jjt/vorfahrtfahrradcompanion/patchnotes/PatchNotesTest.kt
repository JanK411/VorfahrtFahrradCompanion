package nl.jjt.vorfahrtfahrradcompanion.patchnotes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatchNotesTest {

    private val notes = listOf(
        PatchNote("3.0", "2026-09-01", listOf("c")),
        PatchNote("2.0", "2026-08-01", listOf("b")),
        PatchNote("1.0", "2026-07-01", listOf("a")),
    )

    @Test
    fun nullLastSeen_allNew() {
        val (new, older) = splitPatchNotes(notes, null)
        assertEquals(notes, new)
        assertEquals(emptyList(), older)
    }

    @Test
    fun seenNewest_noneNew() {
        val (new, older) = splitPatchNotes(notes, "3.0")
        assertEquals(emptyList(), new)
        assertEquals(notes, older)
    }

    @Test
    fun seenMiddle_splitsAroundIt() {
        val (new, older) = splitPatchNotes(notes, "2.0")
        assertEquals(listOf(notes[0]), new)
        assertEquals(listOf(notes[1], notes[2]), older)
    }

    @Test
    fun unknownVersion_allNew() {
        val (new, older) = splitPatchNotes(notes, "9.9")
        assertEquals(notes, new)
        assertEquals(emptyList(), older)
    }

    @Test
    fun patchNotes_versionsAreStrictlyDescending() {
        patchNotes.map { it.version }.zipWithNext { newer, older ->
            assertTrue(
                compareVersions(newer, older) > 0,
                "patch notes must be newest first, but $newer is not newer than $older",
            )
        }
    }

    @Test
    fun patchNotes_versionsAreUnique() {
        val duplicates = patchNotes.groupingBy { it.version }.eachCount().filterValues { it > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate patch note versions: $duplicates")
    }
}

/** Compares dotted numeric versions ("1.10" > "1.9"); missing components count as 0. */
private fun compareVersions(a: String, b: String): Int {
    val left = a.split('.').map { it.toInt() }
    val right = b.split('.').map { it.toInt() }
    for (i in 0 until maxOf(left.size, right.size)) {
        val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}
