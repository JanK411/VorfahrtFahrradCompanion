# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Kotlin Multiplatform + Compose Multiplatform app targeting Android and iOS. Currently at the KMP wizard template stage — the only domain code is the `Greeting`/`Platform` sample.

## Commands

```bash
./gradlew :androidApp:assembleDebug        # build Android debug APK
./gradlew :shared:testAndroidHostTest      # JVM-side tests (commonTest + androidHostTest)
./gradlew :shared:iosSimulatorArm64Test    # iOS simulator tests (commonTest + iosTest); needs macOS
./gradlew build                            # everything buildable on this host
```

Single test (both test tasks accept the filter):

```bash
./gradlew :shared:testAndroidHostTest --tests "com.example.vorfahrtfahrradcompanion.SharedCommonTest"
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

## Conventions

- All dependency and plugin versions go through `gradle/libs.versions.toml`; never hardcode a version in a `build.gradle.kts`.
- JVM target is 11 across both modules; keep them in sync if you change one.
- iOS targets are `iosArm64` and `iosSimulatorArm64` only — no x64 simulator.
- Configuration cache and build cache are on (`gradle.properties`); build logic must stay configuration-cache compatible (no reading `project` at execution time).
- Package is `com.example.vorfahrtfahrradcompanion` — still the template default, worth renaming before release.

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
