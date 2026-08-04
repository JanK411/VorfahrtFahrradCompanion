package nl.jjt.vorfahrtfahrradcompanion.db.catalogue

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CatalogueCacheDao {
    @Query("SELECT * FROM catalogue_cache WHERE id = :id")
    suspend fun get(id: Int = CatalogueCacheEntity.SINGLETON_ID): CatalogueCacheEntity?

    @Upsert
    suspend fun upsert(entity: CatalogueCacheEntity)

    @Query("DELETE FROM catalogue_cache")
    suspend fun clear()
}
