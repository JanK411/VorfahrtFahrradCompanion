package nl.jjt.vorfahrtfahrradcompanion.domain.criteria

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

private val width = Criterion("WIDTH", CriterionKind.SINGLE)
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI)

private val w1 = CriterionValue("W_1")
private val w2 = CriterionValue("W_2")
private val cars = CriterionValue("CARS")
private val cyclists = CriterionValue("CYCLISTS")

class SelectionsTest {

    @Test
    fun aCriterionNobodyTouchedHoldsNothing() {
        assertEquals(emptySet(), Selections()[width])
        assertTrue(Selections().isEmpty())
    }

    @Test
    fun singleSelectionReplacesAndClears() {
        var selections = Selections()

        selections = selections.select(width, w1)
        assertEquals(setOf(w1), selections[width])

        // A different chip replaces
        selections = selections.select(width, w2)
        assertEquals(setOf(w2), selections[width])

        // The selected chip clears
        selections = selections.select(width, w2)
        assertEquals(emptySet(), selections[width])
    }

    @Test
    fun multiSelectionToggles() {
        var selections = Selections()

        selections = selections.select(users, cars)
        selections = selections.select(users, cyclists)
        assertEquals(setOf(cars, cyclists), selections[users])

        selections = selections.select(users, cars)
        assertEquals(setOf(cyclists), selections[users])
    }

    @Test
    fun pickingOffACardSetsTheValueTheCriterionHolds() {
        // Picking what is already there must not leave the next segment with nothing to describe it.
        assertEquals(setOf(w1), Selections(mapOf("WIDTH" to setOf(w1))).pick(width, w1)[width])
        assertEquals(setOf(w2), Selections(mapOf("WIDTH" to setOf(w1))).pick(width, w2)[width])
    }

    @Test
    fun onlyCriteriaHoldingValuesCountAsFilled() {
        val selections = Selections(mapOf("WIDTH" to setOf(w1), "ALLOWED_USERS" to emptySet()))

        assertEquals(setOf("WIDTH"), selections.filled)
    }

    /**
     * Deselecting leaves the criterion behind holding nothing rather than dropping it, which is the
     * whole reason [Selections.compact] exists: a segment must not be stored describing itself by a
     * criterion the rider cleared.
     */
    @Test
    fun clearingACriterionLeavesAnEmptyEntryForCompactToDrop() {
        val cleared = Selections().select(width, w1).select(width, w1)

        assertEquals(emptySet(), cleared.filled)
        assertTrue(cleared.isEmpty())
        assertEquals(Selections(), cleared.compact())
    }

    @Test
    fun retainNarrowsToTheCriteriaNamed() {
        val selections = Selections(mapOf("WIDTH" to setOf(w1), "ALLOWED_USERS" to setOf(cars)))

        assertEquals(Selections(mapOf("WIDTH" to setOf(w1))), selections.retain(setOf("WIDTH")))
        assertEquals(Selections(), selections.retain(emptySet()))
    }

    @Test
    fun holdingNothingButEmptySetsCountsAsEmpty() {
        assertFalse(Selections(mapOf("WIDTH" to setOf(w1))).isEmpty())
        assertTrue(Selections(mapOf("WIDTH" to emptySet())).isEmpty())
    }
}
