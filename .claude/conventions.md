# Conventions

Everything is under `nl.jjt.vorfahrtfahrradcompanion`, in `shared/src/{commonMain,androidMain,iosMain}`
unless noted. `commonTest` mirrors the package of whatever it tests, plus `testing`.

The tree below is a manifest, not a snapshot: **a package that is not on the list is a violation, not
a new convention.** A rule that has to change is changed here first, then in the code.

```shell
.claude/conventions-check.sh
```

Prints one line per violation as `path:line  rule  detail`. Run it after moving files and before
committing a refactoring.

## Folder structure

```
(root)                    App.kt, the composition root — and in androidApp, MainActivity
db                        AppDatabase, AppDatabaseConstructor, Migrations, AndroidAppDatabase
db/catalogue              CatalogueCacheEntity, CatalogueCacheDao, CatalogueCacheStore
db/observation            ObservationEntity, ObservationDao, RoomObservationStore
db/patchnotes             PatchNotesStateEntity, PatchNotesStateDao, PatchNotesStateStore
db/ride                   RideEntity, RideDao, RoomRideStore
db/settings               SettingsEntity, SettingsDao, SettingsStore
domain/criteria           Criterion, CriterionKind, CriterionValue, Catalogue, Answers, StoredAnswers
domain/patchnotes         PatchNote, the patch note list, splitPatchNotes
domain/recording          Observation, StoredObservation and ObservationStore — what both halves store,
                          and the port they store it through, and nothing else
domain/recording/ride     Ride, RecordedRide, RideState, RideSummary, RideRecorder, RideStore
domain/recording/segment  Segment, Draft, SegmentOutcome, SegmentAction, SegmentRecorder,
                          BoundaryKind, EndTiming
domain/settings           Settings
service/http              HttpClient, HttpClientEngine (expect/actual), TransportSecurity, BaseUrl
service/criteria          CriteriaApi, KtorCriteriaApi, CachingCriteriaApi, CatalogueDto
service/connection        ConnectionTester
service/ride              RideApi, KtorRideApi, RideUploader, RideUploadDto
ui/navigation             Routes, NavigationGate/LeaveGuard, ConfirmPrompt
ui/theme                  AppTheme, Spotlight, LocalNight, BicycleIcon
ui/common                 HoldMenu, ElapsedSeconds, TimeOfDay, DimWhenIdle, KeepScreenAwake,
                          SystemBarIcons
ui/criteria               CriteriaScreen and its cards, buttons, dialogs, CriteriaViewModel
ui/location               LocationScreen, LocationViewModel
ui/patchnotes             PatchNotesScreen, PatchNotesViewModel
ui/rides                  RidesScreen, RidesViewModel
ui/settings               SettingsScreen, ServerConnectionScreen, ServerConnectionViewModel
util/location             Location, LocationProvider, LocationPermissions, LocationSettings
                          + Android*/Ios*
util/platform             ScreenAwake, ScreenBrightness, SystemBars, SystemCacheMarker + Android*/Ios*
util/daylight             Sun, Daylight
util/di                   AppModules, AndroidModule — one module per layer, named <layer>Module
testing                   (commonTest only) the fakes, one Fake<Interface> per file
```

**`util` holds packages, never files.** A file that would sit directly in it belongs in a sub-package
that answers a question, or in the layer it actually serves.

**`domain/recording` is split by what owns the state.** A ride is one thing the rider starts and ends;
a segment is one of the many stretches recorded inside it, with its own boundaries, draft and outcome.
Separate lifetimes, separate recorders, separate packages. What neither half owns alone stays in
`domain/recording` — a new file there has to earn that spot the same way.

## What may depend on what

- **`domain` depends on nothing else in the app**, and on no framework: no `db`, `service`, `ui`,
  `di`, and no Room, Ktor, Compose or Lifecycle. It declares the interfaces it needs and lets `db`
  implement them.
- Everything else may depend on `db`. Only `domain` is held to purity.
- `androidx.room` appears only under `db`; a `<X>Dao` is used only inside its own `db/<feature>`
  package, and everything outside talks to a Store.
- `@Composable` only under `ui`, `util/location` (the permission state) and `App.kt`.
- No `java.*`, `android.*` or `javax.*` in `commonMain`.

## Local vocabulary

- **`Store`** persists (only in `db`), **`Recorder`** owns in-memory recording state that outlives a
  tab switch (only under `domain/recording`), **`Api`** talks HTTP and **`Tester`** performs a
  one-shot check (only in `service`), **`ViewModel`/`Screen`/`Dialog`** only in `ui`.
- The `Room` prefix means one thing: *an interface of this name is declared outside `db`*.
  `SettingsStore` and `PatchNotesStateStore` have no consumer in `domain`, so they get no port and no
  prefix.
- **`Repository` is not used.** It once named both a DAO wrapper and an in-memory engine, which is
  the ambiguity this vocabulary exists to remove.
- Exactly one `<Screen>UiState` per ViewModel — what its `state` flow emits. Auxiliary state types
  keep plain names (`SaveState`, `ConnectionTestState`) and nothing else ends in `UiState`. Sealed
  state hierarchies are named after the concept with the states nested inside: `Ride { Idle, Open }`.
- Table names are `snake_case` of the concept, pluralised when the table holds many rows: `rides`,
  `observations`, `settings`, `catalogue_cache`, `patch_notes_state`.
- A `commonMain` interface with an Android implementation must have an iOS one too, even if its body
  is `TODO("iOS not implemented")` — that stub is the whole iOS investment.
