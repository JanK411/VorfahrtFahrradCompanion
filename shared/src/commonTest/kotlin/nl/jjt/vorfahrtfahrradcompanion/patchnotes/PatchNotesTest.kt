package nl.jjt.vorfahrtfahrradcompanion.patchnotes

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
