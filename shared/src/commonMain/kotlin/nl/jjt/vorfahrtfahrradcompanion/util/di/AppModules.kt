package nl.jjt.vorfahrtfahrradcompanion.util.di

import nl.jjt.vorfahrtfahrradcompanion.service.criteria.CachingCriteriaApi
import nl.jjt.vorfahrtfahrradcompanion.service.criteria.CriteriaApi
import nl.jjt.vorfahrtfahrradcompanion.ui.criteria.CriteriaViewModel
import nl.jjt.vorfahrtfahrradcompanion.service.criteria.KtorCriteriaApi
import nl.jjt.vorfahrtfahrradcompanion.util.daylight.Daylight
import nl.jjt.vorfahrtfahrradcompanion.db.AppDatabase
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheStore
import nl.jjt.vorfahrtfahrradcompanion.db.observation.RoomObservationStore
import nl.jjt.vorfahrtfahrradcompanion.db.patchnotes.PatchNotesStateStore
import nl.jjt.vorfahrtfahrradcompanion.db.ride.RoomRideStore
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsStore
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.SegmentRecorder
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ObservationStore
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.RideRecorder
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.RideStore
import nl.jjt.vorfahrtfahrradcompanion.ui.location.LocationViewModel
import nl.jjt.vorfahrtfahrradcompanion.service.http.createHttpClient
import nl.jjt.vorfahrtfahrradcompanion.service.http.platformHttpClientEngine
import nl.jjt.vorfahrtfahrradcompanion.ui.patchnotes.PatchNotesViewModel
import nl.jjt.vorfahrtfahrradcompanion.service.connection.ConnectionTester
import nl.jjt.vorfahrtfahrradcompanion.ui.settings.ServerConnectionViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The modules follow the top-level packages, so where a binding lives says which module declares it.
 * [AppDatabase] itself is bound per platform — its builder needs a platform context.
 */
val dbModule = module {
    single { get<AppDatabase>().catalogueCacheDao() }
    single { get<AppDatabase>().observationDao() }
    single { get<AppDatabase>().rideDao() }
    single { get<AppDatabase>().settingsDao() }
    single { get<AppDatabase>().patchNotesStateDao() }
    single<ObservationStore> { RoomObservationStore(get()) }
    single<RideStore> { RoomRideStore(get()) }
    single { CatalogueCacheStore(get()) }
    single { SettingsStore(get()) }
    single { PatchNotesStateStore(get()) }
}

/** Daylight sits in its own top-level package but is domain logic, so it is bound here. */
val domainModule = module {
    single { RideRecorder(get(), get()) }
    single { SegmentRecorder(get(), get()) }
    single { Daylight(get()) }
}

val serviceModule = module {
    single { createHttpClient(platformHttpClientEngine()) }
    single<CriteriaApi> { CachingCriteriaApi(KtorCriteriaApi(get(), get()), get(), get(), get()) }
    single { ConnectionTester(get()) }
}

val uiModule = module {
    viewModel { CriteriaViewModel(get(), get(), get()) }
    viewModel { LocationViewModel(get(), get()) }
    viewModel { ServerConnectionViewModel(get(), get()) }
    viewModel { PatchNotesViewModel(get()) }
}

val appModules: List<Module> = listOf(
    dbModule,
    domainModule,
    serviceModule,
    uiModule,
)
