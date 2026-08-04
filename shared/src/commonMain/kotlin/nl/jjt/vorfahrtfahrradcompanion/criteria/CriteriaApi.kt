package nl.jjt.vorfahrtfahrradcompanion.criteria

import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue

interface CriteriaApi {
    suspend fun catalogue(): Catalogue
}
