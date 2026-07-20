# Step 6 — Wiring: DI, tabs, Settings tab live

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md) · Depends on: steps 3–5

First step with a visible result: the Settings tab works end to end on a device.

## Do

- `di/AppModules.kt`: add `settingsModule` (`viewModel { SettingsViewModel(get()) }`,
  `single { SettingsRepository(get()) }`, `single { createHttpClient(get()) }`) and append it to
  `appModules`.
- `androidApp/.../MainActivity.kt`: declare the Room database builder and the Ktor
  `HttpClientEngine` in the existing `androidModule`, alongside the `Context`/location bindings.
  Keep passing it via `App(additionalModules)` — register the `Context` itself, don't rely on
  `androidContext()`.
- `App.kt`: add a third bottom-nav tab `Settings` dispatching to `SettingsScreen(modifier)`.
  Keep the existing `mutableStateOf` tab switching — three tabs don't justify a navigation library.

## Done when

`./gradlew :androidApp:assembleDebug`, install, open Settings, save a base URL, restart the app,
value is still there.
