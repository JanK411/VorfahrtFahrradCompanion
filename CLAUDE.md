# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Kotlin Multiplatform + Compose Multiplatform app. Currently at the KMP wizard template stage — the only domain code is the `Greeting`/`Platform` sample.

**Android is the only target being developed.** The iOS target exists to keep a future port cheap, not because iOS is being worked on. See "Android-first, iOS-ready" below — it governs every other decision in this file.

## Commands

```bash
./gradlew :androidApp:assembleDebug        # build Android debug APK
./gradlew :shared:testAndroidHostTest      # JVM-side tests (commonTest + androidHostTest)
./gradlew :shared:iosSimulatorArm64Test    # iOS simulator tests; needs macOS — not part of the workflow
./gradlew build                            # everything buildable on this host
```

Single test (both test tasks accept the filter):

```bash
./gradlew :shared:testAndroidHostTest --tests "nl.jjt.vorfahrtfahrradcompanion.SharedCommonTest"
./gradlew :shared:testAndroidHostTest --tests "*.SharedCommonTest.example"
```

The iOS app is built and run from Xcode: open `iosApp/` (`iosApp.xcodeproj`). Xcode invokes the Gradle framework task itself; `Configuration/Config.xcconfig` holds bundle id / team settings.

On Linux only the Android and common targets resolve — iOS tasks will fail, so verify iOS-affecting changes by compiling `:shared` and reasoning about `iosMain` rather than running iOS tasks.

## Architecture

Two Gradle modules (`settings.gradle.kts`): `:shared` and `:androidApp`. The iOS app is not a Gradle module.

`:shared` carries both the business logic **and the entire UI**. `App.kt` in `commonMain` is the single Composable root; the platform entry points are thin adapters into it:

- Android: `MainActivity.setContent { App() }` → `androidApp`
- iOS: `MainViewController.kt` wraps `App()` in `ComposeUIViewController`, exported as the static framework `Shared`, consumed by `ContentView.swift` via `MainViewControllerKt.MainViewController()`

So UI changes belong in `shared/src/commonMain`, not in `androidApp` or `iosApp`. Those two directories should only ever change when the platform host/plumbing changes.

Platform-specific behaviour uses `expect`/`actual` (`Platform.kt` → `Platform.android.kt` / `Platform.ios.kt`).

`:shared` uses AGP's `com.android.kotlin.multiplatform.library` plugin (not the classic `com.android.library`), so its Android config lives inside the `kotlin { android { … } }` block. Its test source-set names differ from a classic Android library: `androidHostTest` (unit tests) and a device-test builder wired to `sourceSetTreeName = "test"`.

Compose resources under `shared/src/commonMain/composeResources/` generate the `vorfahrtfahrradcompanion.shared.generated.resources.Res` accessor — after adding a resource, build `:shared` to regenerate before referencing it.

## Android-first, iOS-ready

Spend no effort on iOS. Spend no effort making iOS *impossible* either. Concretely:

**Do**

- Write everything in `commonMain` by default — UI included. Compose Multiplatform is not extra work over Android-only Compose; there is no Android-only fast path to take.
- Put platform APIs behind an interface (or `expect`) declared in `commonMain`, implemented in `androidMain`. Relevant here: location/GPS, sensors, permissions, background execution, file storage.
- Give `iosMain` a `TODO("iOS not implemented")` actual so the source set keeps compiling. That stub *is* the entire iOS investment.
- Prefer the KMP-capable library when there's a choice — the cost is zero now and a rewrite later. Ktor (not Retrofit/OkHttp), kotlinx-serialization (not Gson/Moshi), Room KMP or SQLDelight, Koin (not Hilt), coroutines/Flow (not LiveData), `androidx.navigation.compose` (multiplatform).

**Don't**

- Don't reference `android.*`, `Context`, `Activity`, or JVM-only APIs (`java.time`, `java.io.File`, `java.util.*`) from `commonMain` — use `kotlinx-datetime`, `kotlinx-io`, kotlin stdlib instead.
- Don't let platform types into domain models or shared function signatures. Map `android.location.Location` to an own data class at the `androidMain` boundary.
- Don't write iOS actuals, iOS tests, or Swift beyond the existing template plumbing. Don't design around an iOS constraint that isn't blocking Android today.
- Don't delete `iosMain`/`iosApp` to "clean up".

If an Android-only shortcut is genuinely worth it, take it in `androidMain` — never in `commonMain`.

## Conventions

- All dependency and plugin versions go through `gradle/libs.versions.toml`; never hardcode a version in a `build.gradle.kts`.
- JVM target is 11 across both modules; keep them in sync if you change one.
- iOS targets are `iosArm64` and `iosSimulatorArm64` only — no x64 simulator.
- Configuration cache and build cache are on (`gradle.properties`); build logic must stay configuration-cache compatible (no reading `project` at execution time).
- Package is `nl.jjt.vorfahrtfahrradcompanion`.

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
- Always use best practices. If the user asks something that violates best practices give a notice first and do not implement anything.
