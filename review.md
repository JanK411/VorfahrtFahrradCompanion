# Code review — VorfahrtFahrradCompanion

Date: 2026-07-30 · Reviewed at `df75f3c` (branch `claude/extensive-code-review-jmlk0x`)

## Scope and method

Full read of every hand-written source file in `:shared`, `:androidApp`, the Gradle build, the Room
schemas, the CI workflow and the project docs. Findings come from static reading only — this container has
no Android SDK, so `:shared:testAndroidHostTest` and `:androidApp:assembleDebug` could **not** be executed.
Nothing below is a compile error; the observations are about behaviour, structure and process.

Severity key: **H** = will bite users or loses data · **M** = real defect or notable debt · **L** = polish.

---

## What is done well

Worth stating, because these are the parts that should not be traded away in any refactoring:

- **The catalogue-agnostic UI.** `CriterionKind` reaches only the reducer in `Selections.select`
  (`criteria/Criterion.kt:23`), never the layout, so `CriteriaScreen` renders a catalogue it has never seen.
  That is the right seam and it is documented where it matters.
- **`LeaveGuard` / `NavigationGate` / `rememberConfirmPrompt`.** Turning a dialog into a suspending
  question and funnelling *every* exit path (tab switch, toolbar arrow, predictive back) through one
  predicate is a genuinely good piece of design, and the "unanswered question completes with `false` on
  dispose" detail (`navigation/ConfirmPrompt.kt:22`) shows the failure mode was thought through.
- **Transport policy.** `net/TransportSecurity.kt` puts the rule in one place, enforces it at the client
  as well as in the settings UI, and backs it with a table-driven test. The manifest comment explaining
  *why* the platform can't express the same rule (`androidApp/src/main/AndroidManifest.xml:10`) is exactly
  the comment that should exist.
- **Comments explain intent, not mechanics** — `MIGRATION_4_5`'s justification for being destructive,
  `SettingsViewModel`'s "one-shot seed", `CachingCriteriaApi`'s cache-keying rationale. This is above
  average and should be kept up.
- **Migrations are hand-written and schemas are exported** rather than relying on destructive fallback.

---

## H1 — The DI container is scoped to the composition, so "singletons" are rebuilt per Activity

`App.kt:73` calls `KoinApplication(...)`, which creates a Koin container tied to the *composition* and
closes it when that composition leaves. `MainActivity.onCreate` (`MainActivity.kt:22-32`) builds the
Android module and hands it in on every `onCreate`.

Every Activity recreation — rotation, dark-mode switch, font-size or locale change, "don't keep
activities" — therefore builds a **second** `AppDatabase`, `HttpClient`, `ObservationRepository`,
`SettingsRepository` and `ConnectionTester`, while the ViewModels retained in the Activity's
`ViewModelStore` (Koin's `viewModel {}` resolves through `LocalViewModelStoreOwner`, i.e. the
`NavBackStackEntry`, which *does* survive recreation) keep the previous copies alive and in use.

Consequences, in rough order of importance:

1. **Two or more Room instances open on `vorfahrt.db` at once.** Each has its own connection pool and its
   own invalidation tracker, so a `Flow` query on instance A never fires for a write made through
   instance B. Today nothing observes the `observations` table, so no user-visible corruption — but
   `SettingsDao.observe` and `PatchNotesStateDao.observeLastSeenVersion` are exactly this shape, and the
   first feature that reads across the boundary will produce a bug that is very hard to attribute.
2. **A leaked SQLite connection pool and OkHttp engine per recreation.** Koin's `close()` does not close
   these — there is no `onClose { }` on any binding — and neither does anything else.
3. **The in-memory draft has no single owner.** `ObservationRepository` holds the open segment
   (`criteria/ObservationRepository.kt:31`) precisely so it outlives a ViewModel, but as bound it is
   *shorter*-lived than the ViewModel that uses it. The comment at `ObservationRepository.kt:22-26`
   states the opposite of what the wiring delivers.
4. Minor: the module lambda at `MainActivity.kt:23` closes over the Activity (`applicationContext` is an
   implicit `this.` access), so the container holds an Activity reference for its lifetime.

**Fix.** Start Koin once per process in an `Application` subclass and let the composable read the ambient
container:

```kotlin
class VorfahrtApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin { modules(appModules + androidModule(applicationContext)) }
    }
}
// App.kt
@Composable fun App() = KoinContext { AppTheme { … } }
```

Keeping the `App(additionalModules)` parameter for previews/tests is fine, but the production path should
not build the graph inside a composition. This is the single highest-value change in the review: it is
small, it is mechanical, and it removes a whole class of future bugs.

---

## H2 — An open segment is lost on process death, and there is no warning

`Draft` lives only in a `MutableStateFlow` (`criteria/ObservationRepository.kt:31`). The code comment
acknowledges "it is still memory only and does not outlive the process", but weigh that against the use
case: a rider starts a segment, pockets the phone, rides for minutes with the screen off. Android
routinely kills backgrounded processes under memory pressure. When it does, the open segment and all chip
selections are gone silently — and because the segment only materialises in the DB at `end()`, an
interrupted recording leaves **no trace at all**, not even a partial row.

`TODO VF-116` at `criteria/CriteriaScreen.kt:16` covers the navigation half of this (a `LeaveGuard` for an
open segment), which is the smaller half.

**Fix.** Persist the open segment. It is one extra table (or one nullable-ended row in `observations`)
with the same columns the entity already has minus the end boundary, written on `start()` and completed on
`end()`. That also makes H1's draft-ownership problem moot, gives crash recovery for free, and is the
natural place to hang "you have a segment running since 14:03 — continue or discard?" on next launch.

---

## H3 — Recorded observations are write-only, and no GPS track exists to resolve them against

`ObservationDao` (`criteria/db/ObservationDao.kt`) exposes `insert` and nothing else; a repo-wide search
confirms nothing reads the `observations` table. Meanwhile `ObservationEntity`'s doc comment
(`criteria/db/ObservationEntity.kt:7-11`) states the design: "No location is stored — the segment's
position is recovered later by matching these timestamps against the GPS track."

That GPS track does not exist. `LocationViewModel` is a live readout: `provider.locations()` is collected
only while `LocationScreen` is composed (`SharingStarted.WhileSubscribed(5_000)`), nothing is written to
storage, and there is no foreground service, so on Android 10+ the stream stops when the app leaves the
foreground anyway.

As it stands the app collects timestamps that can never be resolved to positions, and the user has no way
to see, export or delete what was recorded. That is not a bug in any single file — it is the missing
middle of the feature — but it should be on the plan explicitly rather than implied by a comment. The
minimum viable next steps: a foreground-service-backed track recorder writing fixes to Room, plus a screen
or export that makes stored observations visible.

---

## M1 — A poisoned cache row bricks the Criteria screen with no recovery

`CachingCriteriaApi` decodes the cache with a bare `Json` (`criteria/CachingCriteriaApi.kt:60`), which by
default **rejects unknown keys and requires every declared field**. Both decode call sites are outside any
recovery path:

```kotlin
if (cached != null && !cached.isStale()) return cached.decode()   // throws straight out of catalogue()
return try { delegate.catalogue().also { store(baseUrl, it) } }
catch (e: Exception) { cached?.decode() ?: throw e }               // throws here too
```

So if a future app version adds a required field to `CriterionDto`, every user with a cached catalogue hits
`MissingFieldException` on the Criteria tab, lands on `CriteriaUiState.Failed`, and **Retry re-reads the
same poisoned row**. The only way out is clearing app data. The same applies to any corrupted row.

**Fix.** Treat an undecodable cache row as no cache: wrap `decode()` in `runCatching`, delete the row on
failure, fall through to the network. Also give the cache its own lenient `Json` (`ignoreUnknownKeys =
true`) so forward-compatible reads work, matching the client's config at `net/HttpClient.kt:16`.

---

## M2 — Double-tap on "End" can store a zero-length segment / a phantom "saved" toast

`CriteriaViewModel.end` (`criteria/CriteriaViewModel.kt:56-70`) reads the in-flight guard **before**
launching, but sets `InFlight` **inside** the coroutine:

```kotlin
val ready = _state.value as? Ready ?: return
if (ready.saveState is SaveState.InFlight) return     // checked here…
viewModelScope.launch { updateReady { copy(saveState = InFlight) } … }   // …set here
```

Two taps dispatched in the same frame both pass the guard (the button's own `enabled` flag doesn't help —
the state hasn't changed yet). With `SegmentAction.STOP` the second call finds the segment `Idle`,
`ObservationRepository.end` returns silently, and the UI still runs InFlight → Idle, which
`CriteriaScreen.kt:54-59` interprets as success and shows "Segment saved" for a write that never happened.
With `START_NEXT` it is worse: the second call stores a **zero-length segment** (`startedAt == endedAt`,
both at the boundary instant) as a real row.

**Fix.** Make the transition atomic — flip to `InFlight` with a compare-and-set on `_state` before
`launch`, and bail if the CAS loses.

---

## M3 — Success is reconstructed in the UI instead of signalled by the ViewModel

`criteria/CriteriaScreen.kt:52-59` infers "saved" from an `InFlight → Idle` edge tracked in a `remember`d
boolean. This is what makes M2 visible, and it is fragile on its own terms: `wasInFlight` is `remember`,
not `rememberSaveable`, so a configuration change mid-save drops the toast; and any future state that
returns to `Idle` for another reason will fire a false success.

**Fix.** Emit a one-shot event from the ViewModel (a `Channel<SaveEvent>` consumed as a `Flow`, or a
`SaveState.Saved` the screen consumes and acknowledges). The ViewModel already knows the write succeeded —
the UI should not have to re-derive it.

---

## M4 — Editing settings can silently discard your changes

`SettingsUiState.hasUnsavedChanges` (`settings/SettingsViewModel.kt:37-38`) is `false` whenever
`normalizedBaseUrl == null` — i.e. whenever the URL is blank, malformed, or plain-http to a public host.
`hasUnsavedChanges` drives both the Save button's visibility (`ServerConnectionScreen.kt:116`) and the
`LeaveGuard` (`ServerConnectionScreen.kt:56`).

Concrete loss: the user changes the **password**, then mistypes the URL. The Save button disappears, the
leave guard goes quiet, and navigating back throws away both edits without a word.

**Fix.** Compute "dirty" from the raw fields against the saved snapshot, and gate only *saving* on
validity. The dialog then still appears, with Save disabled and the reason visible.

---

## M5 — The location permission screen is a dead end

`AndroidLocationPermissions.rememberState` (`androidMain/.../AndroidLocationPermissions.kt:20-32`) seeds
`granted` once and only ever updates it from the launcher callback. Two failure modes:

- **Grant made outside the app.** The user goes to system settings, enables location, comes back — the app
  still shows the rationale until the process restarts, because nothing re-checks on resume.
- **Permanent denial or coarse-only grant.** `isGranted()` requires *both* `ACCESS_FINE_LOCATION` and
  `ACCESS_COARSE_LOCATION` (`:34`). If the user picks "Approximate" in the system dialog, or denies twice,
  `launcher.launch` no longer shows anything. The button then does nothing, forever, with no explanation
  and no route to app settings.

**Fix.** Re-read the permission on `Lifecycle.Event.ON_RESUME`, and when a request cannot be shown again
(`shouldShowRequestPermissionRationale` false after a denial) swap the button for "Open app settings".
Also decide whether coarse-only is genuinely unusable — if the app can work with it, don't require both.

---

## M6 — Server password is stored in cleartext and is included in Android Auto Backup

`SettingsEntity.password` (`settings/db/SettingsEntity.kt:18`) is a plain `TEXT` column in `vorfahrt.db`,
and the manifest sets `android:allowBackup="true"` (`androidApp/src/main/AndroidManifest.xml:18`) with no
`fullBackupContent` / `dataExtractionRules`. The credential therefore leaves the device in Google Auto
Backup and device-to-device transfer, and is trivially readable on a rooted or debuggable device.

The blast radius is one self-hosted server, which is why this is M and not H — but it is a real credential
crossing a boundary the user never opted into.

**Fix, cheapest first:** (a) exclude the database from backup via `dataExtractionRules` +
`fullBackupContent`; (b) keep the password out of Room entirely — `EncryptedSharedPreferences` behind the
same repository interface, or an API token instead of Basic auth. (a) alone removes the off-device
exposure and is a five-line change.

---

## M7 — CI writes the release keystore into the workspace on every branch push

`.github/workflows/build.yml:24-35` decodes `KEYSTORE_BASE64` into `androidApp/release.jks` and signs the
debug APK with it, on `on: push` for **every** branch. Signing debug with the release key is a deliberate
choice per `androidApp/build.gradle.kts:61` and fine on its own; the exposure is that the private key sits
unencrypted in the workspace for the remainder of the job, where any later step — or any dependency with a
build-time hook — can read it, and the APK is then pushed to an external service (Dropbox).

Also: `echo "$KEYSTORE_BASE64" | base64 -d > …` succeeds with an empty secret, producing an empty `.jks`
and an inscrutable signing failure later.

**Fix.** Guard the decode (`[ -n "$KEYSTORE_BASE64" ] || { echo "missing secret"; exit 1; }`), delete the
keystore in an `if: always()` cleanup step, and consider restricting the signing + Dropbox steps to pushes
on the default branch.

---

## M8 — Build/release version has been frozen at 1.0 across five changelog releases

`androidApp/build.gradle.kts:40-41` still says `versionCode = 1`, `versionName = "1.0"`, while
`patchnotes/PatchNotes.kt` is at 1.4. Two problems: release APKs cannot be installed over one another
(Android requires a strictly greater `versionCode`), and the changelog's `version` — which is also the key
`patch_notes_state` tracks "seen" against (`patchnotes/PatchNote.kt:6-8`) — corresponds to nothing in the
build.

**Fix.** Derive `versionName` from `patchNotes.first().version` (or the reverse — a single constant read by
both), and bump `versionCode` per release, e.g. from the CI run number.

---

## M9 — Configuration-cache-unsafe build logic

`androidApp/build.gradle.kts:4-8` reads `keystore.properties` with `File.exists()` / `inputStream()` at
configuration time. Unlike `System.getenv`, direct file reads in a build script are **not** tracked as
configuration-cache inputs, so adding or changing `keystore.properties` will not invalidate the cached
configuration — you get a stale, unsigned (or wrongly signed) build with no indication why.
`gradle.properties:6` has the configuration cache on, and CLAUDE.md requires build logic to stay
compatible with it.

**Fix.** Use the provider API: `providers.fileContents(layout.projectDirectory.file("../keystore.properties")).asText`
and `providers.environmentVariable("KEYSTORE_FILE")`, resolved lazily.

---

## M10 — Two pinned pre-release dependencies, contrary to the project's own rule

CLAUDE.md: *"Pin stable versions. No alpha/RC without a concrete reason."*

- `material3 = "1.11.0-alpha07"` (`gradle/libs.versions.toml:22`)
- `androidx-lifecycle = "2.11.0-beta01"` (`:10`)

Neither carries a comment explaining the reason. Either record the reason next to the pin (the way
`LeaveGuard.kt:52-55` records why `BackHandler` is still used) or move to stable.

---

## M11 — Structure: the database package, the DI module names, and the URL-building duplication

Three related organisational drifts, all cheap to fix and all getting more expensive as features land:

1. **`AppDatabase` lives in `settings.db`** (`settings/db/AppDatabase.kt`) but owns entities from four
   features, and `Migrations.kt` — which migrates the observations table and imports
   `criteria.BoundaryKind` — lives there too. Move `AppDatabase`, `AppDatabaseConstructor`, `Migrations`
   and `AndroidAppDatabase` to a feature-neutral `db` package.
2. **`settingsModule` binds the HTTP client** (`di/AppModules.kt:35`) and `ConnectionTester`. Networking
   is not settings; split out a `netModule`.
3. **The catalogue endpoint and the URL/auth assembly are duplicated** between `KtorCriteriaApi.kt:21-27`
   and `ConnectionTester.kt:28-34`, and the `normalizeBaseUrl(raw) ?: raw` fallback appears in both
   `KtorCriteriaApi.kt:23` and `CachingCriteriaApi.kt:45`. One `HttpRequestBuilder.catalogueRequest(settings)`
   extension (or a `Settings.resolvedBaseUrl` property) removes all three copies. Right now, moving the
   endpoint means remembering two files — and `ConnectionTester` would keep reporting OK against a path
   the real client no longer uses.

---

## Low severity

- **L1 — Unformatted numbers on the Ride screen.** `location/LocationScreen.kt:58-61` interpolates raw
  `Double`/`Float`: `"52.37021999999999, 4.895168"`, `"17.640001 km/h"`. Needs a small rounding helper in
  `commonMain` (no `String.format` there).
- **L2 — Redundant SDK branch.** `AndroidLocationSettings.kt:25-29` hand-rolls the TIRAMISU check and then
  calls `ContextCompat.registerReceiver` in the else branch — the compat call already handles both. Drop
  the `if`.
- **L3 — Cancellation is swallowed.** `CriteriaViewModel` catches bare `Exception` at `:66` and `:78`,
  which also catches `CancellationException`. `ConnectionTester.kt:40-42` gets this right; make the
  ViewModel consistent.
- **L4 — Raw exception text is shown to users.** `e.message ?: "Could not save the segment"`
  (`CriteriaViewModel.kt:67`) can surface a SQLite error string in the UI.
- **L5 — Hardcoded pop target.** `App.kt:143` uses `popUpTo(CriteriaRoute)`; prefer
  `navController.graph.findStartDestination().id` so changing the start destination can't silently break
  tab back-stack behaviour.
- **L6 — Light-only Android theme.** `AndroidManifest.xml:23` pins
  `@android:style/Theme.Material.Light.NoActionBar` while Compose has a full dark scheme
  (`ui/Theme.kt:37`), giving a white launch background in dark mode. Use a `DayNight` parent.
- **L7 — Userinfo survives normalisation.** `normalizeBaseUrl` keeps `user:pass@` in the stored base URL
  (`settings/BaseUrl.kt:21-24`); it will then be sent on every request alongside the explicit `basicAuth`.
  Strip it, or reject it.
- **L8 — Redirects carry the `Authorization` header.** Ktor follows redirects by default. For a fixed
  self-hosted origin, `followRedirects = false` on the client is the tighter default.
- **L9 — All UI strings are hardcoded English in `commonMain`**, although `composeResources` is already
  wired up in the build. Fine for now; decide before the string count grows.
- **L10 — Naming.** `Tab.Location` is labelled "Ride" and routes to `RideRoute` (`App.kt:67`). Pick one
  name.
- **L11 — Release builds are unminified** (`androidApp/build.gradle.kts:69`). Not urgent, but Room, Ktor
  and kotlinx-serialization all need attention when R8 is switched on — better to do it early than at
  release time.
- **L12 — No `.editorconfig`** despite `kotlin.code.style=official`.

---

## Testing

The tests that exist are good — table-driven, behaviour-named, and they cover the two subtle domains
(URL/transport rules, segment boundaries) properly. The gaps are about placement and coverage:

- **Dead template tests.** `SharedCommonTest.example`, `SharedLogicAndroidHostTest.example` and
  `SharedLogicIOSTest.example` all assert `1 + 2 == 3`. Delete them.
- **A test of a third-party library.** `CriteriaTest.urlBuildingWithPrefixedBase` asserts Ktor's
  `URLBuilder` behaviour. If the intent is to pin the "base URL may carry a path prefix" assumption, assert
  it against *our* request builder once M11.3 exists.
- **Misplaced tests.** `singleSelectionReplacesAndClears` and `multiSelectionToggles`
  (`CriteriaViewModelTest.kt:55-80`) never touch the ViewModel — they belong in a `SelectionsTest`.
  `BaseUrlTest.rejectsPlainHttpOutsideTheLocalNetwork` tests `SettingsUiState`, not `BaseUrl`.
- **Untested logic that matters**, roughly in priority order: `SettingsViewModel`
  (dirty-tracking/save/discard — see M4), the `InsecureTransportGuard` plugin itself (only the pure
  predicate is covered, not the interception), `NavigationGate`/`LeaveGuard`, `PatchNotesViewModel`,
  `ConnectionTester`'s status mapping.
- **No migration tests.** Five schema versions are exported and hand-written migrations run on real user
  data; a mistake in one is a crash loop on launch with no recovery. Room's `MigrationTestHelper` over the
  exported schemas closes this.
- **CI runs tests only** — no Android Lint, no ktlint/detekt. Android Lint alone would have flagged the
  backup/manifest issues in M6.

---

## Documentation

Both top-level docs are stale enough to actively mislead — including any agent working from them, which is
what CLAUDE.md is for:

- **`CLAUDE.md:5-6`**: *"Still at the KMP wizard template stage — only domain code is the
  `Greeting`/`Platform` sample."* Neither type exists; there are four features, Room with five migrations,
  Ktor, navigation and DI. The Architecture and Stack sections are accurate and useful — it is the framing
  that is out of date.
- **`README.md`** is the unedited wizard README, down to instructions for a `jvmMain` source set that does
  not exist. It says nothing about what the app is, what server it talks to, or how to configure one.

Fixing these two is the cheapest high-leverage change in this document.

---

## Suggested order

1. **H1** — move Koin startup to `Application`. Small, mechanical, removes a class of latent bugs.
2. **M1**, **M2**, **M4** — three contained correctness fixes, each a few lines.
3. **H2** — persist the open segment; also the right moment to close `TODO VF-116`.
4. **M6**, **M7** — backup exclusion and the CI keystore guard; both small, both security-relevant.
5. **Docs** (CLAUDE.md + README) and **M8** version wiring.
6. **M11** structural moves, **M5** permission flow, then the L items opportunistically.
7. **H3** is a planning item, not a patch — decide what the GPS track and the observation read path look
   like before more is built on top of the current write-only storage.
