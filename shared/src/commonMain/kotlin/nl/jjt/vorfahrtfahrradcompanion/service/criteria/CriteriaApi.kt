package nl.jjt.vorfahrtfahrradcompanion.service.criteria

import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue

interface CriteriaApi {
    suspend fun catalogue(): Catalogue
}
