# Refactoring notes

Project specifics for the global `refactor` skill.

## Verify

| | |
|---|---|
| Narrow | `./gradlew :shared:testAndroidHostTest --tests "*.CriteriaViewModelTest"` |
| Full gate | `./gradlew :shared:testAndroidHostTest` |
| Extra | `./gradlew :shared:compileIosMainKotlinMetadata` — required whenever an `expect`/`actual`, a `commonMain` signature or anything under `iosMain` was touched |
| Extra | `./gradlew :androidApp:assembleDebug` — only if `androidApp/` or `App.kt` wiring changed |

`testAndroidHostTest` compiles and runs `commonTest` *and* `androidHostTest`; it is the only test
task that runs on this machine.

## Not covered

- **iOS.** `iosTest` needs macOS and is never part of the baseline. The metadata compile is the
  only iOS check available here — it proves `iosMain` still compiles, nothing about its behaviour.
  Do not report an iOS-affecting refactoring as verified on the strength of the Android tests.
- **Anything needing a live `Context`, `LocationManager` or `Window`.** No Robolectric, no
  device-test suite. Those `actual`s cannot be pinned host-side.
- **Composables.** No UI test infrastructure exists; a Composable's rendering is unpinnable today.
  The common move is extracting logic *out* of a Composable or an Android `actual` (see `a85792b`,
  `d381bc3`) — legitimate, but the test can only be written after the extraction.

## Test layout

- Pure logic (`daylight/`, `settings/BaseUrl`, `net/TransportSecurity`, `criteria/CriterionLabel`,
  `patchnotes/` splitting) → `shared/src/commonTest/…`, plain `kotlin.test`.
- ViewModels and state → also `commonTest`, with `kotlinx-coroutines-test` and the shared
  `FakeClock` (`commonTest/…/FakeClock.kt`). **`CriteriaViewModelTest` is the pattern to copy.**
- Android `actual`s (`AndroidLocationProvider`, `AndroidScreenBrightness`, `AndroidAppDatabase`, …)
  → `shared/src/androidHostTest/…`.

## Commits

Ticket first, then an imperative sentence describing the structural change. `0000` when there is
no ticket.

```
VF-130: Move the boundary buttons out of the criteria screen
VF-115: Extract the elapsed-seconds ticker
```

Characterization commit: `VF-<n>: Characterize <target> before restructuring it`.

## Traps

- **`expect`/`actual` sets move as one.** `platformHttpClientEngine()` and
  `AppDatabaseConstructor` have `actual`s in `androidMain` *and* `iosMain`. Renaming or moving the
  `expect` without the `iosMain` side still passes `testAndroidHostTest` — that source set is not
  compiled by it. The metadata compile is what catches this.
- **Room entity properties are column names.** Renaming an `@Entity` class is free; renaming one of
  its properties renames the column, which needs a `version` bump in `AppDatabase` and a new
  `MIGRATION_n_n+1` — a schema change, not a refactoring. KSP verifies `@Query` strings against the
  entities; it does **not** verify that an installed app can still open the file. Watch
  `schemas/…AppDatabase/*.json` in the diff: a changed exported schema means the database changed.
- **`@Serializable` DTO properties are the server's wire format.** `CatalogueDto` / `CriterionDto`
  mirror what the backend sends. Renaming a property renames the JSON field and the app stops
  parsing real responses — no test notices, because the fakes are built from the same class. The
  `"SINGLE"` / `"MULTI"` literals in `toDomain`/`toDto` are wire values too, not enum names.
- **`@Serializable` route objects carry their qualified name.** The nav routes in `App.kt` are
  identified by class name; moving or renaming one changes the key a saved back stack is restored
  against. Fine within a session, a lost destination across process death.
- **Koin resolves `get()` by type, at runtime.** Reordering two same-typed constructor parameters
  of `CriteriaViewModel` or `CachingCriteriaApi` compiles and silently swaps the arguments;
  extracting a collaborator into its own class needs a new binding in `di/AppModules.kt` or the
  screen throws on first open. Neither is a compile error nor covered by a test — check
  `AppModules.kt` by hand after any change to a constructor signature.
- **`patchNotes` order is behaviour.** `splitPatchNotes` derives new-vs-older from list position,
  not from parsing versions; reordering the list changes what every user is shown. Conversely: a
  refactoring is by definition not user-visible, so it gets **no** patch note entry.
- **`commonMain` stays platform-free.** Moving code *into* `commonMain` must not bring `android.*`,
  `Context`, `java.time`, `java.io` or `java.util` with it. Moving code *out* of `commonMain` into
  `androidMain` is a portability decision, not a refactoring — raise it first.
