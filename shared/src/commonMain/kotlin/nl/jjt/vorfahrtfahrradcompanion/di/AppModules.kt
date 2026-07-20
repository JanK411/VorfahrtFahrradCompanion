package nl.jjt.vorfahrtfahrradcompanion.di

import nl.jjt.vorfahrtfahrradcompanion.location.LocationViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val locationModule = module {
    viewModel { LocationViewModel(get(), get()) }
}

val appModules: List<Module> = listOf(locationModule)
