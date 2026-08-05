package nl.jjt.vorfahrtfahrradcompanion.domain.criteria

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val width = Criterion("WIDTH", CriterionKind.SINGLE)
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI)

private val w1 = CriterionValue("W_1")
private val w2 = CriterionValue("W_2")
private val cars = CriterionValue("CARS")
private val cyclists = CriterionValue("CYCLISTS")

class AnswersTest {

    @Test
    fun aCriterionNobodyTouchedHoldsNothing() {
        assertEquals(emptySet(), Answers()[width])
        assertTrue(Answers().isEmpty())
    }

    @Test
    fun singleAnswerReplacesAndClears() {
        var answers = Answers()

        answers = answers.select(width, w1)
        assertEquals(setOf(w1), answers[width])

        // A different chip replaces
        answers = answers.select(width, w2)
        assertEquals(setOf(w2), answers[width])

        // The selected chip clears
        answers = answers.select(width, w2)
        assertEquals(emptySet(), answers[width])
    }

    @Test
    fun multiAnswerToggles() {
        var answers = Answers()

        answers = answers.select(users, cars)
        answers = answers.select(users, cyclists)
        assertEquals(setOf(cars, cyclists), answers[users])

        answers = answers.select(users, cars)
        assertEquals(setOf(cyclists), answers[users])
    }

    @Test
    fun carryingOnSetsTheValueTheCriterionHolds() {
        // Carrying on with what is already there must not leave the next segment with nothing to
        // describe it.
        assertEquals(setOf(w1), Answers(mapOf(width to setOf(w1))).carryOnWith(width, w1)[width])
        assertEquals(setOf(w2), Answers(mapOf(width to setOf(w1))).carryOnWith(width, w2)[width])
    }

    @Test
    fun carryingOnFillsACriterionHoldingNothing() {
        assertEquals(setOf(w1), Answers().carryOnWith(width, w1)[width])
        assertEquals(setOf(w1), Answers(mapOf(width to emptySet())).carryOnWith(width, w1)[width])
    }

    @Test
    fun carryingOnAnswersOneCriterionAndLeavesTheRestAsTheyWere() {
        val answers = Answers(mapOf(width to setOf(w1), users to setOf(cars, cyclists)))

        val carried = answers.carryOnWith(width, w2)

        assertEquals(setOf(w2), carried[width])
        assertEquals(setOf(cars, cyclists), carried[users])
    }

    @Test
    fun onlyCriteriaHoldingValuesCountAsFilled() {
        val answers = Answers(mapOf(width to setOf(w1), users to emptySet()))

        assertEquals(setOf(width), answers.filled)
    }

    /**
     * Deselecting leaves the criterion behind holding nothing rather than dropping it, which is the
     * whole reason [Answers.compact] exists: a segment must not be stored describing itself by a
     * criterion the rider cleared.
     */
    @Test
    fun clearingACriterionLeavesAnEmptyEntryForCompactToDrop() {
        val cleared = Answers().select(width, w1).select(width, w1)

        assertEquals(emptySet(), cleared.filled)
        assertTrue(cleared.isEmpty())
        assertEquals(Answers(), cleared.compact())
    }

    @Test
    fun retainNarrowsToTheCriteriaNamed() {
        val answers = Answers(mapOf(width to setOf(w1), users to setOf(cars)))

        assertEquals(Answers(mapOf(width to setOf(w1))), answers.retain(setOf(width)))
        assertEquals(Answers(), answers.retain(emptySet()))
    }

    @Test
    fun holdingNothingButEmptySetsCountsAsEmpty() {
        assertFalse(Answers(mapOf(width to setOf(w1))).isEmpty())
        assertTrue(Answers(mapOf(width to emptySet())).isEmpty())
    }
}
