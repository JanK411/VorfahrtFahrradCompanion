package nl.jjt.vorfahrtfahrradcompanion.settings.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import nl.jjt.vorfahrtfahrradcompanion.patchnotes.db.PatchNotesStateDao
import nl.jjt.vorfahrtfahrradcompanion.patchnotes.db.PatchNotesStateEntity

@Database(entities = [SettingsEntity::class, PatchNotesStateEntity::class], version = 2)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun patchNotesStateDao(): PatchNotesStateDao
}
