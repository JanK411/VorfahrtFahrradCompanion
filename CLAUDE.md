# CLAUDE.md

## Project

Kotlin Multiplatform + Compose Multiplatform. Still at the KMP wizard template stage — only domain code is the
`Greeting`/`Platform` sample. Package `nl.jjt.vorfahrtfahrradcompanion`.

**Android is the only target being developed.** iOS exists to keep a future port cheap.

## Commands

```bash
./gradlew :androidApp:assembleDebug        # Android debug APK
./gradlew :shared:testAndroidHostTest      # commonTest + androidHostTest
./gradlew :shared:testAndroidHostTest --tests "*.SharedCommonTest.example"
```

iOS tasks need macOS and are not part of the workflow. On Linux, verify iOS-affecting changes by compiling `:shared`.

## Architecture

Modules: `:shared` and `:androidApp`. The iOS app is not a Gradle module (built from Xcode).

`:shared` carries the business logic **and the entire UI**. `App.kt` in `commonMain` is the single Composable root;
`MainActivity` and `MainViewController.kt` are thin adapters. UI changes go in `shared/src/commonMain` — `androidApp`/
`iosApp` change only for host plumbing.

`:shared` uses AGP's `com.android.kotlin.multiplatform.library` plugin, so Android config lives in
`kotlin { android { … } }` and test source sets are `androidHostTest` + a device-test builder with
`sourceSetTreeName = "test"`.

After adding a file under `commonMain/composeResources/`, build `:shared` to regenerate `Res` before referencing it.

## Android-first, iOS-ready

Spend no effort on iOS; spend no effort making it impossible either.

- Write everything in `commonMain` by default, UI included.
- Platform APIs (GPS, sensors, permissions, background, storage) go behind an interface or `expect` in `commonMain`,
  `actual` in `androidMain`, and a `TODO("iOS not implemented")` in `iosMain`. That stub is the entire iOS investment.
- No `android.*`, `Context`, `Activity`, or JVM-only APIs (`java.time`, `java.io`, `java.util`) in `commonMain` — use
  kotlinx-datetime, kotlinx-io, stdlib.
- No platform types in domain models or shared signatures — map `android.location.Location` to an own data class at the
  `androidMain` boundary.
- Don't write iOS actuals, iOS tests, or Swift. Don't delete `iosMain`/`iosApp`.
- An Android-only shortcut, if genuinely worth it, goes in `androidMain` — never `commonMain`.

## Stack

`~/Source/GpsTrackerApp` is a working KMP app in the same domain — prior art for reference implementations, not a
dependency.

- **DI**: Koin + `koin-compose-viewmodel`'s `viewModel { }`. Modules in `commonMain`; `Context`-needing bindings are
  built in `MainActivity` and passed to `App(additionalModules)` — register the `Context` itself rather than relying on
  `androidContext()`.
- **Persistence**: Room KMP. `@ConstructedBy` + `expect object : RoomDatabaseConstructor` (needs
  `@Suppress("KotlinNoActualForExpect")`), `BundledSQLiteDriver`, `setQueryCoroutineContext(Dispatchers.IO)`. KSP
  registered per target; schemas exported via `room { schemaDirectory(...) }`.
- **State**: multiplatform `ViewModel` + `StateFlow`; one sealed `UiState` per screen where states are genuinely
  exclusive.
- **Networking / JSON**: Ktor + `kotlinx-serialization-json` (`-core` is not enough).
- **Time**: kotlinx-datetime + `kotlin.time.Clock`; opt in to `kotlin.time.ExperimentalTime`.
- **Navigation**: `androidx.navigation.compose` — multiplatform-stable, type-safe routes from `@Serializable`. Not
  Navigation3: it's Android-first, and its only real win is adaptive list-detail, which this app doesn't need.
- **Streaming platform data uses `Flow`, not callbacks**: `fun locations(intervalMillis: Long): Flow<Location>` via
  `callbackFlow`, not `onUpdate(cb)`/`start()`/`stop()`. Applies to GPS, sensors, anything with a listener API.
- **Permissions**: permission *screen* in `commonMain`; only the request mechanism behind an interface implemented in
  `androidMain` (`rememberLauncherForActivityResult`). No `@Composable expect fun`, no Accompanist.
- **Pin stable versions.** No alpha/RC without a concrete reason.

## Conventions

- All versions via `gradle/libs.versions.toml`; never hardcoded in a `build.gradle.kts`.
- JVM target 11 in both modules.
- iOS targets `iosArm64` + `iosSimulatorArm64` only.
- Configuration cache and build cache are on; build logic must stay config-cache compatible.

## General Instructions

- Be brief. Don't use many words when few do trick.
- Before writing code, stop at the first rung that holds:
    1. Does this need to exist? → no: skip it (YAGNI)
    2. Stdlib does it? → use it
    3. Native platform feature? → use it
    4. Installed dependency? → use it
    5. One line? → one line
    6. Only then: the minimum that works
- Always running in IntelliJ. Use IntelliJ MCP when necessary.
- Always use best practices. If the user asks something that violates best practices give a notice first and do not
  implement anything.
- Don't commit any changes if I don't explicitly ask you to do so. But if I do, split your tasks into multiple small
  commits and write proper commit messages. If you have a ticket number in context, the message should start with the
  ticket number (for example "VF-123: <here the message>"). If you implemented a plan, reference the plan also in the
  commit message. If the plan is fully implemented, add one commit in the end deleting the markdown file of the plan.
