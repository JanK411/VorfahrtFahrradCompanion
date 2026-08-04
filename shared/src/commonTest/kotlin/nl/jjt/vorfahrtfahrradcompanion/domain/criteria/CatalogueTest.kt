package nl.jjt.vorfahrtfahrradcompanion.domain.criteria

import kotlin.test.assertEquals
import kotlin.test.Test

private val width = Criterion("WIDTH", CriterionKind.SINGLE)
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI)

private val w1 = CriterionValue("W_1")
private val cars = CriterionValue("CARS")

class CatalogueTest {

    private val catalogue = Catalogue(mapOf(width to listOf(w1), users to listOf(cars)))

    @Test
    fun theCriteriaKeepTheOrderTheyArrivedIn() {
        assertEquals(listOf(width, users), catalogue.criteria)
    }

    @Test
    fun aCriterionItNeverHeardOfOffersNothing() {
        assertEquals(emptyList(), catalogue[Criterion("SURFACE_KIND", CriterionKind.SINGLE)])
    }

    @Test
    fun storedSelectionsComeBackAsTheCriteriaTheyWereStoredUnder() {
        val selections = Selections(mapOf(width to setOf(w1), users to setOf(cars)))

        assertEquals(selections, catalogue.resolve(selections.stored()))
    }

    /** The catalogue is fetched fresh; a question no longer put cannot describe the next segment. */
    @Test
    fun aStoredCriterionTheCatalogueNoLongerOffersIsDropped() {
        val retired = Criterion("RETIRED", CriterionKind.SINGLE)
        val stored = Selections(
            mapOf(width to setOf(w1), retired to setOf(CriterionValue("GONE"))),
        ).stored()

        assertEquals(Selections(mapOf(width to setOf(w1))), catalogue.resolve(stored))
    }

    /**
     * Resolution goes by id, since that is all the table kept. A criterion the server has since
     * changed the kind of comes back as the question it is now — the one the rider is about to be
     * asked — rather than the one it was when the segment was stored.
     */
    @Test
    fun aStoredCriterionWhoseKindChangedComesBackAsTheCatalogueHasItNow() {
        val whenItWasPickOne = Criterion("ALLOWED_USERS", CriterionKind.SINGLE)
        val stored = Selections(mapOf(whenItWasPickOne to setOf(cars))).stored()

        assertEquals(Selections(mapOf(users to setOf(cars))), catalogue.resolve(stored))
    }
}
