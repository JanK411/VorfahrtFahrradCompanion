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
| Submit | `POST {baseUrl}/admin/evaluation-model/observations` — body shape is a **guess**, see below |
| Location | One-shot current GPS fix taken at submit time |
| Backend config | In-app settings screen — **full base URL** / username / password |
| Cleartext HTTP | Debug builds only, via `androidApp/src/debug/AndroidManifest.xml` |
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

## Base URL handling

`localhost` only works on the dev machine. On a physical phone the app must be pointable at a LAN
address (`http://192.168.178.42:8080`) or a public HTTPS endpoint (`https://vorfahrt.example.com`),
possibly with a path prefix (`.../api`). So the stored `baseUrl` is a **full URL — origin plus
optional path prefix**, not a hostname. One editable field; you retype it when switching networks.

`settings/BaseUrl.kt` holds one pure, unit-testable function:

```kotlin
fun normalizeBaseUrl(raw: String): String?   // null = invalid, show inline error
```

Rules in order: `trim()`, blank → `null`; no `://` → prepend `http://` (so typing
`192.168.178.42:8080` just works); reject a scheme other than `http`/`https`; strip trailing `/`;
reject an empty host. Store the normalised string; keep the raw text in the screen's local state so
typing isn't fought with.

Because the base URL may carry a path prefix, requests must not concatenate strings:

```kotlin
url {
    takeFrom(settings.baseUrl)
    appendPathSegments("admin", "evaluation-model", "criterion-catalogue")
}
```

`appendPathSegments` preserves the prefix, so `https://vorfahrt.example.com/api` resolves to
`…/api/admin/evaluation-model/…`. `KtorCriteriaApi` reads settings per call, so editing the field
takes effect on the next request with no client rebuild — the shared `HttpClient` gets no
`defaultRequest` base URL.

## Dependencies to add (`gradle/libs.versions.toml` first, per conventions)

Latest stable versions, verified against Maven Central / Google Maven on 2026-07-20:

```toml
kotlinx-serialization = "1.9.0"
ksp                   = "2.3.9"
ktor                  = "3.5.1"
room                  = "2.8.4"
sqlite                = "2.7.0"
```

- Ktor client: `ktor-client-core`, `ktor-client-content-negotiation`,
  `ktor-serialization-kotlinx-json`, `ktor-client-auth`, engines `ktor-client-okhttp`
  (androidMain) + `ktor-client-darwin` (iosMain). Note `criterion-catalogue.http` uses IntelliJ's
  two-token `Authorization: Basic user pass` form, which the IDE encodes — Ktor needs real base64,
  i.e. `basicAuth(...)` or `ktor-client-auth`.
- `kotlinx-serialization-json` (**not** `-core`) + the `kotlin.plugin.serialization` plugin
- Room KMP + `androidx-sqlite-bundled` + KSP plugin, registered per target;
  `room { schemaDirectory("$projectDir/schemas") }`
- No kotlinx-datetime needed — `kotlin.time.Instant` (already used by `Location`) serialises to
  ISO-8601 via `toString()`.
- **Unverified**: KSP 2.3.x uses its own version line, decoupled from the Kotlin version. 2.3.9 is
  the newest release on that line (Maven Central, 2026-07-20 — there is no 2.3.10). Compatibility
  with Kotlin 2.4.10 needs to be confirmed by an actual build; if it fails, fall back to the newest
  `2.4.10-*` KSP release.

**Android manifests**:

- `androidApp/src/main/AndroidManifest.xml`: add `android.permission.INTERNET`. It currently
  declares only the two location permissions, so every request would fail.
- new `androidApp/src/debug/AndroidManifest.xml` setting `android:usesCleartextTraffic="true"` on
  `<application>`, so plaintext `http://` to the LAN box works in debug builds only. Release builds
  inherit the platform default (cleartext blocked at targetSdk 36), leaving the HTTPS URL as the
  only thing that works there — which is correct. Add `tools:replace` only if the manifest merger
  complains.

## Package layout (all in `shared/src/commonMain/kotlin/nl/jjt/vorfahrtfahrradcompanion/`)

```
criteria/
  Criterion.kt          domain: Criterion(id, kind: CriterionKind, values: List<String>), Catalogue
  CriteriaDto.kt        @Serializable DTOs + toDomain(); ignores weightRange/importanceRange/defaultImportance
  CriteriaApi.kt        interface CriteriaApi { suspend fun catalogue(): Catalogue
                                                suspend fun submit(o: Observation) }
  KtorCriteriaApi.kt    impl; reads baseUrl/creds from SettingsRepository per call
  CriteriaViewModel.kt  CriteriaUiState + selection state + submit
  CriteriaScreen.kt     the UI
settings/
  Settings.kt           data class Settings(baseUrl, username, password)
  BaseUrl.kt            normalizeBaseUrl(); pure, no Ktor needed
  SettingsRepository.kt Flow<Settings> + suspend save()
  SettingsScreen.kt     three TextFields + save
  db/                   Room entity, DAO, AppDatabase, expect object constructor
net/
  HttpClient.kt         Ktor client factory; engine bound per-platform in DI
```

The base-URL `TextField` in `SettingsScreen` uses `KeyboardType.Uri`, `autoCorrectEnabled = false`
and `singleLine = true`; its `supportingText` shows the normalised value or the validation error,
`isError` is wired to the same, and Save is disabled while invalid. The placeholder carries both
real examples: `http://192.168.178.42:8080` and `https://vorfahrt.example.com`.

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
- new `androidApp/src/debug/AndroidManifest.xml`
- `shared/src/commonMain/.../App.kt`, `.../di/AppModules.kt`
- `androidApp/src/main/kotlin/.../MainActivity.kt`
- new: the `criteria/`, `settings/`, `net/` packages above (+ tiny `androidMain`/`iosMain` engine files)

## Verification

1. `./gradlew :shared:compileKotlinIosArm64` — proves nothing platform-specific leaked into `commonMain`.
2. `./gradlew :shared:testAndroidHostTest` — unit tests for (a) parsing the sample catalogue JSON
   into the domain model, (b) the SINGLE vs MULTI selection reducer, (c) omission of empty
   selections from the request body, (d) a table-driven `normalizeBaseUrl` test:
   `192.168.178.42:8080` → `http://192.168.178.42:8080`; `https://vorfahrt.example.com/api/` →
   `https://vorfahrt.example.com/api`; `ftp://x`, `""`, `http://` → `null`; plus a test that URL
   building against a prefixed base yields `/api/admin/evaluation-model/criterion-catalogue`.
3. `./gradlew :androidApp:assembleDebug`, install **on a physical phone**, then with the backend
   running:
   - Settings tab → base URL `http://<dev-box-LAN-IP>:8080` + `vorfahrt` / password → save. This
     is the case that fails with `localhost`.
   - Criteria tab → 8 criterion groups appear; `ALLOWED_USERS` allows multiple chips, the rest one
   - select a few, submit outdoors/with a mock fix, confirm the server receives the POST
   - if the public endpoint is reachable, repeat with `https://vorfahrt.example.com`
   - change to a deliberately wrong base URL → error + Retry; change back → recovers with no app
     restart, proving settings are read per call
4. Stop the backend and reopen the tab → error state with a working Retry.
5. Temporarily add a criterion/value to the server response → it appears with no rebuild.
6. `./gradlew :androidApp:assembleRelease`, then confirm
   `androidApp/build/intermediates/merged_manifests/release/AndroidManifest.xml` contains no
   `usesCleartextTraffic="true"`.
