package nl.jjt.vorfahrtfahrradcompanion.settings.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Lives in `androidMain` because the builder needs a [Context]; iOS would supply its own factory
 * over an `NSDocumentDirectory` path.
 */
fun createAppDatabase(context: Context): AppDatabase =
    Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath("vorfahrt.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_1_2)
        .build()
