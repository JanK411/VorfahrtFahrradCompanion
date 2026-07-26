package nl.jjt.vorfahrtfahrradcompanion.criteria

interface CriteriaApi {
    suspend fun catalogue(): Catalogue
}
