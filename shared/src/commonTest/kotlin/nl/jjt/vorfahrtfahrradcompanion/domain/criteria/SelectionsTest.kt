package nl.jjt.vorfahrtfahrradcompanion.domain.criteria

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

private val width = Criterion("WIDTH", CriterionKind.SINGLE, listOf("W_1", "W_2"))
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI, listOf("CARS", "CYCLISTS"))

class SelectionsTest {

    @Test
    fun aCriterionNobodyTouchedHoldsNothing() {
        assertEquals(emptySet(), Selections()[width])
        assertTrue(Selections().isEmpty())
    }

    @Test
    fun singleSelectionReplacesAndClears() {
        var selections = Selections()

        selections = selections.select(width, "W_1")
        assertEquals(setOf("W_1"), selections[width])

        // A different chip replaces
        selections = selections.select(width, "W_2")
        assertEquals(setOf("W_2"), selections[width])

        // The selected chip clears
        selections = selections.select(width, "W_2")
        assertEquals(emptySet(), selections[width])
    }

    @Test
    fun multiSelectionToggles() {
        var selections = Selections()

        selections = selections.select(users, "CARS")
        selections = selections.select(users, "CYCLISTS")
        assertEquals(setOf("CARS", "CYCLISTS"), selections[users])

        selections = selections.select(users, "CARS")
        assertEquals(setOf("CYCLISTS"), selections[users])
    }

    @Test
    fun pickingOffACardSetsTheValueTheCriterionHolds() {
        // Picking what is already there must not leave the next segment with nothing to describe it.
        assertEquals(setOf("W_1"), Selections(mapOf("WIDTH" to setOf("W_1"))).pick(width, "W_1")[width])
        assertEquals(setOf("W_2"), Selections(mapOf("WIDTH" to setOf("W_1"))).pick(width, "W_2")[width])
    }

    @Test
    fun onlyCriteriaHoldingValuesCountAsFilled() {
        val selections = Selections(mapOf("WIDTH" to setOf("W_1"), "ALLOWED_USERS" to emptySet()))

        assertEquals(setOf("WIDTH"), selections.filled)
    }

    /**
     * Deselecting leaves the criterion behind holding nothing rather than dropping it, which is the
     * whole reason [Selections.compact] exists: a segment must not be stored describing itself by a
     * criterion the rider cleared.
     */
    @Test
    fun clearingACriterionLeavesAnEmptyEntryForCompactToDrop() {
        val cleared = Selections().select(width, "W_1").select(width, "W_1")

        assertEquals(emptySet(), cleared.filled)
        assertTrue(cleared.isEmpty())
        assertEquals(Selections(), cleared.compact())
    }

    @Test
    fun retainNarrowsToTheCriteriaNamed() {
        val selections = Selections(mapOf("WIDTH" to setOf("W_1"), "ALLOWED_USERS" to setOf("CARS")))

        assertEquals(Selections(mapOf("WIDTH" to setOf("W_1"))), selections.retain(setOf("WIDTH")))
        assertEquals(Selections(), selections.retain(emptySet()))
    }

    @Test
    fun holdingNothingButEmptySetsCountsAsEmpty() {
        assertFalse(Selections(mapOf("WIDTH" to setOf("W_1"))).isEmpty())
        assertTrue(Selections(mapOf("WIDTH" to emptySet())).isEmpty())
    }
}
