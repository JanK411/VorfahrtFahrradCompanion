package nl.jjt.vorfahrtfahrradcompanion.di

import nl.jjt.vorfahrtfahrradcompanion.location.LocationViewModel
import nl.jjt.vorfahrtfahrradcompanion.net.createHttpClient
import nl.jjt.vorfahrtfahrradcompanion.net.platformHttpClientEngine
import nl.jjt.vorfahrtfahrradcompanion.settings.ConnectionTester
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsRepository
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsViewModel
import nl.jjt.vorfahrtfahrradcompanion.settings.db.AppDatabase
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val locationModule = module {
    viewModel { LocationViewModel(get(), get()) }
}

/** [AppDatabase] itself is bound per platform — its builder needs a platform context. */
val settingsModule = module {
    single { createHttpClient(platformHttpClientEngine()) }
    single { get<AppDatabase>().settingsDao() }
    single { SettingsRepository(get()) }
    single { ConnectionTester(get()) }
    viewModel { SettingsViewModel(get(), get()) }
}

val appModules: List<Module> = listOf(locationModule, settingsModule)
