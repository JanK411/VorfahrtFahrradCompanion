package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlin.test.Test
import kotlin.test.assertEquals

class CriterionLabelTest {

    private fun label(id: String) = Criterion(id, CriterionKind.SINGLE, emptyList()).label()

    @Test
    fun readsIdsAsWords() {
        assertEquals("Allowed users", label("ALLOWED_USERS"))
        assertEquals("Width", label("WIDTH"))
        assertEquals("Surface type", label("surfaceType"))
        assertEquals("Boundary kind", label("boundary-kind"))
        assertEquals("W 1", label("W_1"))
        assertEquals("", label(""))
    }
}
