package nl.jjt.vorfahrtfahrradcompanion.settings.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.CatalogueCacheDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.CatalogueCacheEntity
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationEntity
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.RideDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.RideEntity
import nl.jjt.vorfahrtfahrradcompanion.patchnotes.db.PatchNotesStateDao
import nl.jjt.vorfahrtfahrradcompanion.patchnotes.db.PatchNotesStateEntity

@Database(
    entities = [
        SettingsEntity::class,
        PatchNotesStateEntity::class,
        CatalogueCacheEntity::class,
        ObservationEntity::class,
        RideEntity::class,
    ],
    version = 6,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun patchNotesStateDao(): PatchNotesStateDao
    abstract fun catalogueCacheDao(): CatalogueCacheDao
    abstract fun observationDao(): ObservationDao
    abstract fun rideDao(): RideDao
}
