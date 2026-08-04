package nl.jjt.vorfahrtfahrradcompanion.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheDao
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheEntity
import nl.jjt.vorfahrtfahrradcompanion.db.observation.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.db.observation.ObservationEntity
import nl.jjt.vorfahrtfahrradcompanion.db.patchnotes.PatchNotesStateDao
import nl.jjt.vorfahrtfahrradcompanion.db.patchnotes.PatchNotesStateEntity
import nl.jjt.vorfahrtfahrradcompanion.db.ride.RideDao
import nl.jjt.vorfahrtfahrradcompanion.db.ride.RideEntity
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsDao
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsEntity

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
