package nl.jjt.vorfahrtfahrradcompanion.testing

import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue
import nl.jjt.vorfahrtfahrradcompanion.service.criteria.CriteriaApi

/**
 * A catalogue source a test drives. Counts its calls, so a caller that is supposed to have served
 * from a cache can be held to it, and throws [error] instead of answering when one is given.
 */
class FakeCriteriaApi(
    private val result: Catalogue,
    private val error: Exception? = null,
) : CriteriaApi {
    var catalogueCalls = 0
        private set

    override suspend fun catalogue(): Catalogue {
        catalogueCalls++
        error?.let { throw it }
        return result
    }
}
