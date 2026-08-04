package nl.jjt.vorfahrtfahrradcompanion.ui.criteria

import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionKind
import kotlin.test.assertEquals
import kotlin.test.Test

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
