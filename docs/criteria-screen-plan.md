# Dynamic criterion-capture screen (replaces "Dummy 1")

## Context

The first bottom-nav tab is a placeholder (`Box { Text("Hello 1") }` in `App.kt`). It should
become the app's primary screen: fetch the criterion catalogue from the backend, render one
input group per criterion, and POST the filled-in values together with the current GPS fix.

The catalogue is authoritative and may change server-side (new criteria, new allowed values).
The UI must therefore be driven entirely by the response — no criterion or value may appear in
Kotlin source. That constraint drives most decisions below.

This is greenfield: the project currently has no HTTP client, no serialization, no persistence,
and no navigation library. Only location handling exists (`shared/src/commonMain/.../location/`).

## Decisions

| Topic | Decision |
| --- | --- |
| Submit | `POST {host}/admin/evaluation-model/observations` — body shape is a **guess**, see below |
| Location | One-shot current GPS fix taken at submit time |
| Backend config | In-app settings screen (host / username / password) |
| Settings storage | Room |
| Labels | Raw IDs shown verbatim (`SURFACE_QUALITY`, `W_0_5`) |
| Catalogue cache | None — fetch on screen open, retry on error |
| Validation | None — unset criteria are omitted from the body |

### Three things to sanity-check before/while building

1. **The POST contract is invented.** Nothing in `http-testing/` documents it. Build against the
   guess and keep DTO + mapping in one small file so correcting it is a 5-line change.
2. **Room for three strings is heavier than the job needs** (it pulls in KSP, a schema dir, an
   `expect object` constructor). DataStore Preferences would be the right-sized tool. Going with
   Room as chosen — noting it since CLAUDE.md's YAGNI ladder points the other way. Room will very
   likely be wanted later anyway for queued/offline observations.
3. The password is stored in plaintext in the DB. Fine for a personal admin tool; not for
   anything shipped.

## Dependencies to add (`gradle/libs.versions.toml` first, per conventions)

Latest stable versions, verified against Maven Central / Google Maven on 2026-07-20:

```toml
kotlinx-serialization = "1.9.0"
ksp                   = "2.3.10"
ktor                  = "3.5.1"
room                  = "2.8.4"
sqlite                = "2.7.0"
```

- Ktor client: `ktor-client-core`, `ktor-client-content-negotiation`,
  `ktor-serialization-kotlinx-json`, `ktor-client-auth`, engines `ktor-client-okhttp`
  (androidMain) + `ktor-client-darwin` (iosMain)
- `kotlinx-serialization-json` (**not** `-core`) + the `kotlin.plugin.serialization` plugin
- Room KMP + `androidx-sqlite-bundled` + KSP plugin, registered per target;
  `room { schemaDirectory("$projectDir/schemas") }`
- No kotlinx-datetime needed — `kotlin.time.Instant` (already used by `Location`) serialises to
  ISO-8601 via `toString()`.
- **Unverified**: KSP 2.3.x uses its own version line, decoupled from the Kotlin version.
  Compatibility with Kotlin 2.4.10 needs to be confirmed by an actual build; if it fails, fall
  back to the newest `2.4.10-*` KSP release.

**Android manifest**: `androidApp/src/main/AndroidManifest.xml` currently has no
`android.permission.INTERNET` — it must be added, or every request fails. `http://` to a dev box
additionally needs `android:usesCleartextTraffic="true"` (or a network-security-config). Also note
`localhost` from a device means `10.0.2.2` (emulator) or the LAN IP (physical phone) — the settings
screen makes this fixable without a rebuild.

## Package layout (all in `shared/src/commonMain/kotlin/nl/jjt/vorfahrtfahrradcompanion/`)

```
criteria/
  Criterion.kt          domain: Criterion(id, kind: CriterionKind, values: List<String>), Catalogue
  CriteriaDto.kt        @Serializable DTOs + toDomain(); ignores weightRange/importanceRange/defaultImportance
  CriteriaApi.kt        interface CriteriaApi { suspend fun catalogue(): Catalogue
                                                suspend fun submit(o: Observation) }
  KtorCriteriaApi.kt    impl; reads host/creds from SettingsRepository per call
  CriteriaViewModel.kt  CriteriaUiState + selection state + submit
  CriteriaScreen.kt     the UI
settings/
  Settings.kt           data class Settings(host, username, password)
  SettingsRepository.kt Flow<Settings> + suspend save()
  SettingsScreen.kt     three TextFields + save
  db/                   Room entity, DAO, AppDatabase, expect object constructor
net/
  HttpClient.kt         Ktor client factory; engine bound per-platform in DI
```

Everything is in `commonMain`; the only `androidMain`/`iosMain` additions are the Ktor engine and
the Room `RoomDatabaseConstructor` actual (the Room constructor actual is generated, so the
`expect object` just needs `@Suppress("KotlinNoActualForExpect")`).

## The dynamic UI

`CriteriaScreen` renders a `LazyColumn` over `catalogue.criteria`. Per criterion:

- header `Text(criterion.id)`
- a `FlowRow` of `FilterChip`s, one per value in `criterion.values`
- `SINGLE`: tapping a chip replaces the selection; tapping the selected chip clears it
- `MULTI`: tapping toggles membership

One composable handles both — the kind only changes the click reducer. No `when` over criterion
IDs anywhere, which is what keeps the screen dynamic.

Selection state lives in the ViewModel as `Map<String, Set<String>>` (criterionId → chosen values),
exposed in the UI state. Unknown `kind` strings from the server degrade to MULTI rather than
crashing the parse.

`CriteriaUiState`: `Loading` / `Failed(message)` (with Retry) /
`Ready(catalogue, selections, submitState)`. `submitState` covers Idle / InFlight / Error — a
`Snackbar` reports success and clears selections.

Submit flow in the ViewModel:

1. `locationProvider.locations().first()` wrapped in `withTimeout` (~15 s) — surface a clear error
   if no fix arrives or permission is missing (`AndroidLocationProvider` closes the flow with
   `SecurityException`).
2. Build `Observation(location, selections.filterValues { it.isNotEmpty() })`.
3. `api.submit(...)`.

Guessed request body:

```json
{ "latitude": 52.1, "longitude": 4.3, "accuracyMeters": 8.0,
  "recordedAt": "2026-07-20T12:43:37Z",
  "values": { "WIDTH": ["W_2"], "ALLOWED_USERS": ["CARS", "CYCLISTS"] } }
```

## Wiring

- `App.kt`: rename the enum entry `Dummy1` → `Criteria` (label "Criteria", `Icons.Filled.Checklist`),
  dispatch to `CriteriaScreen(modifier)`, and add a third `Settings` tab. Keep the existing
  `mutableStateOf` tab switching — three tabs don't justify adding a navigation library.
- `di/AppModules.kt`: new `criteriaModule` (`viewModel { CriteriaViewModel(get(), get()) }`,
  `single<CriteriaApi> { KtorCriteriaApi(get(), get()) }`, `single { HttpClient(...) }`) and
  `settingsModule`; append both to `appModules`.
- Room needs a platform builder → declare it in the `androidModule` inside `MainActivity`
  alongside the existing `Context`/location bindings, matching the established pattern.

## Files touched

- `gradle/libs.versions.toml`, `shared/build.gradle.kts`, `androidApp/src/main/AndroidManifest.xml`
- `shared/src/commonMain/.../App.kt`, `.../di/AppModules.kt`
- `androidApp/src/main/kotlin/.../MainActivity.kt`
- new: the `criteria/`, `settings/`, `net/` packages above (+ tiny `androidMain`/`iosMain` engine files)

## Verification

1. `./gradlew :shared:compileKotlinIosArm64` — proves nothing platform-specific leaked into `commonMain`.
2. `./gradlew :shared:testAndroidHostTest` — unit tests for (a) parsing the sample catalogue JSON
   into the domain model, (b) the SINGLE vs MULTI selection reducer, (c) omission of empty
   selections from the request body.
3. `./gradlew :androidApp:assembleDebug`, install, then with the backend running:
   - Settings tab → enter host (`http://10.0.2.2:8080` on emulator) + `vorfahrt` / password → save
   - Criteria tab → 8 criterion groups appear; `ALLOWED_USERS` allows multiple chips, the rest one
   - select a few, submit outdoors/with a mock fix, confirm the server receives the POST
4. Stop the backend and reopen the tab → error state with a working Retry.
5. Temporarily add a criterion/value to the server response → it appears with no rebuild.
