package nl.jjt.vorfahrtfahrradcompanion.settings.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = :id")
    fun observe(id: Int = SettingsEntity.SINGLETON_ID): Flow<SettingsEntity?>

    @Upsert
    suspend fun upsert(entity: SettingsEntity)
}
