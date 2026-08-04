---
name: conventions
description: The package structure and naming rules for this codebase, and a checker that enforces them. Use when adding or moving a file and wondering where it goes, when naming a class, an interface, a constant or a test, and whenever asked to "check the conventions", "check the structure", or after any refactoring that moves code between packages.
---

# Conventions

Every package answers one question, and the name of a thing says which kind of thing it is. The rules
below describe the codebase as it stands; `check.sh` enforces the mechanical ones.

```shell
.claude/skills/conventions/check.sh
```

It prints one line per violation as `path:line  rule  detail` and exits non-zero if any fired. Run it
after moving files, and before committing a refactoring.

**A rule that has to change is changed here first.** The folder tree below is the checker's manifest:
a package that is not on the list is a violation, not a new convention. That is the point — growing the
structure should be a deliberate act with a diff attached.

## Folder structure

All under `nl.jjt.vorfahrtfahrradcompanion`, in `shared/src/{commonMain,androidMain,iosMain}` unless
noted.

```
(root)              App.kt, the composition root — and in androidApp, MainActivity
db                  AppDatabase, AppDatabaseConstructor, Migrations, AndroidAppDatabase
db/catalogue        CatalogueCacheEntity, CatalogueCacheDao, CatalogueCacheStore
db/observation      ObservationEntity, ObservationDao, RoomObservationStore
db/patchnotes       PatchNotesStateEntity, PatchNotesStateDao, PatchNotesStateStore
db/ride             RideEntity, RideDao, RoomRideStore
db/settings         SettingsEntity, SettingsDao, SettingsStore
domain/criteria     Criterion, CriterionKind, Catalogue, Selections
domain/patchnotes   PatchNote, the patch note list, splitPatchNotes
domain/recording    Segment, Draft, SegmentOutcome, SegmentRecorder, ObservationStore,
                    Ride, RideSummary, RideRecorder, RideStore, BoundaryKind, EndTiming, SegmentAction
domain/settings     Settings
service/http        HttpClient, HttpClientEngine (expect/actual), TransportSecurity, BaseUrl
service/criteria    CriteriaApi, KtorCriteriaApi, CachingCriteriaApi, CatalogueDto
service/connection  ConnectionTester
ui/navigation       Routes, NavigationGate/LeaveGuard, ConfirmPrompt
ui/theme            AppTheme, Spotlight, LocalNight, BicycleIcon
ui/common           HoldMenu, ElapsedSeconds, TimeOfDay, DimWhenIdle, KeepScreenAwake, SystemBarIcons
ui/criteria         CriteriaScreen and its cards, buttons, dialogs, CriteriaViewModel
ui/location         LocationScreen, LocationViewModel
ui/patchnotes       PatchNotesScreen, PatchNotesViewModel
ui/settings         SettingsScreen, ServerConnectionScreen, ServerConnectionViewModel
location            Location, LocationProvider, LocationPermissions, LocationSettings + Android*/Ios*
platform            ScreenAwake, ScreenBrightness, SystemBars, SystemCacheMarker + Android*/Ios*
daylight            Sun, Daylight
di                  AppModules, AndroidModule
testing             (commonTest only) the fakes
```

`commonTest` mirrors the package of whatever it tests, plus `testing`.

### What may depend on what

- **`domain` depends on nothing else in the app**, and on no framework: no `db`, `service`, `ui`,
  `di`, and no Room, Ktor, Compose or Lifecycle. It declares the interfaces it needs and
  lets `db` implement them.
- Everything else may depend on `db`. Only `domain` is held to purity.
- `androidx.room` appears only under `db`.
- A `<X>Dao` is used only inside its own `db/<feature>` package. Everything outside talks to a Store.
- `@Composable` only under `ui`, `location` (the permission state) and `App.kt`.
- No `java.*`, `android.*` or `javax.*` in `commonMain`.

## Naming

### Persistence

| Role | Name | Where |
|---|---|---|
| Room entity | `<Concept>Entity` | `db/<feature>` |
| Room DAO | `<Concept>Dao` | `db/<feature>`, used only from there |
| Gateway with a domain port | `Room<X>Store` implementing `<X>Store` | impl in `db`, port in `domain` |
| Gateway without a port | `<Concept>Store` | `db/<feature>` |

The `Room` prefix means one thing: *there is an interface of this name declared outside `db`*.
`SettingsStore` and `PatchNotesStateStore` have no consumer in `domain`, so they get no port and no
prefix. An interface with one implementation and one caller is bloat.

Table names are `snake_case` of the concept, pluralised when the table holds many rows: `rides`,
`observations`, `settings`, `catalogue_cache`, `patch_notes_state`.

### Implementations

`<Technology><Interface>`: `AndroidLocationProvider`, `IosScreenAwake`, `KtorCriteriaApi`,
`CachingCriteriaApi`, `RoomRideStore`. A `commonMain` interface with an Android implementation must
have an iOS one too, even if its body is `TODO("iOS not implemented")` — that stub is the whole iOS
investment. `expect`/`actual` files share a file name across source sets.

### Vocabulary

Each suffix means one thing, everywhere:

- **`Store`** — persistence gateway. Speaks domain types; hides entities, DAOs and column formats.
  Only in `db`.
- **`Recorder`** — owns in-memory recording state that has to outlive a tab switch. Only in
  `domain/recording`.
- **`Api`** — talks HTTP. Only in `service`. **`Tester`** — performs a one-shot check.
- **`ViewModel`**, **`Screen`**, **`Dialog`** — only in `ui`.
- **`Repository`** — not used. It once named both a DAO wrapper and an in-memory engine, which is the
  ambiguity this vocabulary exists to remove.

### Files and types

- A file is named after one public type and may also hold what exists only to serve it — its enum, its
  constants, its mappers. The file name must match a top-level public declaration.
- Extension-only files are `<Receiver><WhatItAdds>.kt`: `CriterionLabel.kt`, `EndTimingAppearance.kt`.
- Private Composable helpers live in the file of the Composable they serve.

### UI

- Routed destinations end in `Screen` and are reachable from `ui/navigation/Routes.kt`.
- Anything shown in a dialog or sheet ends in `Dialog`.
- Everything else is named after what it draws: `CriterionCard`, `RecorderButton`, `HoldMenuOption`.
- Exactly one `<Screen>UiState` per ViewModel — what its `state` flow emits. Auxiliary state types keep
  plain names (`SaveState`, `ConnectionTestState`, `CriterionState`) and nothing else ends in `UiState`.
- Sealed state hierarchies are named after the concept with the states nested inside, no `State` suffix
  on the interface: `Segment { Idle, Open }`, `Ride { Idle, Open }`.
- CompositionLocals are `Local<Thing>`.

### Casing

Kotlin's official style, which is three rules and not one:

- **Deeply immutable scalar data** — `const` or not — is `SCREAMING_SNAKE`: `DIM_AFTER`, `EARTH_TILT`,
  `LATE_END_GRACE`, `SINGLETON_ID`.
- **References to singleton objects** stay `PascalCase`: `LocalNight`, `BicycleIcon`,
  `InsecureTransportGuard`, `AppColors`, `ExitModifier`.
- **Objects with behaviour or mutable data** are `camelCase`: `appModules`, `dbModule`, `subPages`.
- Enum entries are `SCREAMING_SNAKE`.

Only the first and last are mechanically checkable, so `check.sh` enforces `const val` and enum
entries; the singleton-versus-scalar call is yours.

### Tests

- `<Subject>Test`, in the package of its subject.
- Test methods are `camelCase` and read as sentences. No backticks, no `should`, no `test` prefix:
  `staleCacheIsServedWhenTheServerIsUnreachable`.
- Fakes are named exactly `Fake<Interface>` and live in `testing`, one per file.

### Dependency injection

Modules follow the top-level packages, so a binding's package says which module declares it:
`dbModule`, `domainModule` (which also holds `daylight`, being domain logic in its own package),
`serviceModule`, `uiModule`, and `androidModule` in `androidMain`.
