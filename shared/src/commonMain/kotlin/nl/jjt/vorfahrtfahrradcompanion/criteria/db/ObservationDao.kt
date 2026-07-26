package nl.jjt.vorfahrtfahrradcompanion.criteria.db

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface ObservationDao {
    @Insert
    suspend fun insert(entity: ObservationEntity)
}
