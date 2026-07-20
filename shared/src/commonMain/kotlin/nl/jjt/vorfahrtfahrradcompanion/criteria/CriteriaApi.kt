package nl.jjt.vorfahrtfahrradcompanion.criteria

interface CriteriaApi {
    suspend fun catalogue(): Catalogue
    suspend fun submit(o: Observation)
}
