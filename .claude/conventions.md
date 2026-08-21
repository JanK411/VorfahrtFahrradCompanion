# Conventions


Everything is under `nl.jjt.vorfahrtfahrradcompanion`, in `shared/src/{commonMain,androidMain,iosMain}`.
`androidApp` holds host plumbing only. Tests mirror the package of what they test.

The trees below are a manifest, not a snapshot: **a folder that is not on them is a violation, not a
new convention.** A rule that has to change is changed here first, then in the code.

The `conventions` skill from the `kotlin-development` plugin prints the actual tree to compare
against these. Run it after moving files and before committing a structural change.

## Folder structure

```
shared/src/commonMain/kotlin/nl/jjt/vorfahrtfahrradcompanion
│                               App.kt, the composition root — the only file at the root
├── db                          AppDatabase, its constructor and Migrations; one folder per table
│   ├── catalogue               the cached criteria catalogue
│   ├── observation             what was answered for a segment
│   ├── patchnotes              which patch note the user last saw
│   ├── ride                    the recorded rides
│   └── settings                the single settings row
├── domain                      depends on nothing else in the app, and on no framework
│   ├── criteria                what a rider is asked and what an answer may be
│   ├── patchnotes              the changelog shown in Settings
│   ├── recording               what a ride and a segment both store, and the port they store it
│   │   │                         through — and nothing else
│   │   ├── ride                one thing the rider starts and ends
│   │   └── segment             one stretch recorded inside a ride
│   └── settings                what the rider configured
├── service                     talking to the server
│   ├── connection              the one-shot reachability check
│   ├── criteria                fetching the catalogue, and caching it
│   ├── http                    the client, its engine (expect/actual), base URL and transport rules
│   └── ride                    uploading a recorded ride
├── ui                          Compose — the only place @Composable appears, App.kt aside
│   ├── common                  widgets and effects no single screen owns
│   ├── criteria                the criteria tab: the two designs of it, and what both read
│   │   ├── jan                  one design: screen, cards, dialogs, ViewModel
│   │   └── till                 the other, its own copy of all four
│   ├── location                the location/permission screen
│   ├── navigation              routes and the guard that gates leaving a screen
│   ├── patchnotes              the What's New sub-page
│   ├── rides                   the ride tab
│   ├── settings                the settings tab and the server connection sub-page
│   └── theme                   AppTheme and what only it defines
└── util                        holds folders only, never files
    ├── daylight                is it light out
    ├── di                      one Koin module per layer, named <layer>Module
    ├── location                where the rider is, and whether we may ask
    └── platform                what the device does to itself: screen, bars, cache
```

`androidMain` and `iosMain` mirror this tree exactly, and hold only `actual`s and platform
implementations — `Android<X>.kt` / `Ios<X>.kt` beside the `commonMain` interface's path, plus
`MainViewController.kt` at the iOS root.

**`util` holds folders, never files.** A file that would sit directly in it belongs in a folder that
answers a question, or in the layer it actually serves. That restriction is the whole reason a
folder named `util` is allowed here at all.

**`domain/recording` is split by what owns the state.** A ride and a segment have separate
lifetimes, separate recorders, separate folders. What neither owns alone stays in
`domain/recording`, and a new file there has to earn that spot the same way.

**`ui/criteria` is split by design.** Jan and Till are two designs of the categorising-while-riding
workflow, being ridden against each other; which one the rider gets is picked under Settings. Each
owns its `<Design>CriteriaScreen`, `<Design>CriteriaViewModel` and `<Design>CriteriaUiState`, and the
cards and dialogs that screen is made of — a change to one must not touch the other. The split stops
at `ui`: both record through the same `SegmentRecorder`, `RideRecorder` and `ObservationStore` into
the same table, so what a segment means does not depend on which design described it. What sits
directly in `ui/criteria` is what neither owns alone — the entry point that picks between them, and
`Criterion.label()`.

## Where tests live

```
shared/src/commonTest/kotlin/nl/jjt/vorfahrtfahrradcompanion
├── …                           mirrors commonMain: a test sits in the folder of its subject
└── testing                     the fakes, one Fake<Interface> per file — no tests here
```

A test is `<Subject>Test.kt` with a `<Subject>.kt` at the mirrored path in `commonMain`. `testing` is
the one folder that is not a mirror: what its files share is that they stand in for a port, not a
subject. A design's tests mirror its design folder, so Jan's and Till's copies sit apart the same way
the screens they cover do.

## What may depend on what

- **`domain` depends on nothing else in the app**, and on no framework: no `db`, `service`, `ui`,
  `di`, and no Room, Ktor, Compose or Lifecycle. It declares the interfaces it needs and lets `db`
  implement them.
- Everything else may depend on `domain`. Only `domain` is held to purity.
- **Room stays in `db`.** `androidx.room` appears only under `db`, an `X<Dao>` is used only inside
  its own `db/<feature>` folder, and everything outside talks to a Store.
- **Ktor stays in `service`.**
- `@Composable` only under `ui`, `util/location` (the permission state) and `App.kt`.
- No `java.*`, `android.*` or `javax.*` in `commonMain`.

## Vocabulary

Each suffix means one thing and implies its folder: **`Store`** persists (only in `db`),
**`Recorder`** owns in-memory recording state that outlives a tab switch (only under
`domain/recording`), **`Api`** talks HTTP and **`Tester`** performs a one-shot check (only in
`service`), **`ViewModel`**, **`Screen`** and **`Dialog`** only in `ui`.

The `Room` prefix means one thing: *an interface of this name is declared outside `db`*.
`SettingsStore` and `PatchNotesStateStore` have no consumer in `domain`, so they get no port and no
prefix.

**`Repository` is retired.** It once named both a DAO wrapper and an in-memory engine, which is the
ambiguity this vocabulary exists to remove. `Util`, `Utils`, `Helper` and `Common` are retired as
file names for the same reason — `ui/common` survives as a folder because a widget's own name still
says what it is.

Exactly one `<Screen>UiState` per ViewModel — what its `state` flow emits. Auxiliary state types
keep plain names (`SaveState`, `ConnectionTestState`) and nothing else ends in `UiState`. Sealed
state hierarchies are named after the concept with the states nested inside: `Ride { Idle, Open }`.

Table names are `snake_case` of the concept, pluralised when the table holds many rows: `rides`,
`observations`, `settings`, `catalogue_cache`, `patch_notes_state`.

## Not checked mechanically

Nothing here is enforced by a script — the `conventions` skill prints the tree, and the rest is read.

- **A `commonMain` interface with an Android implementation must have an iOS one too**, even if its
  body is `TODO("iOS not implemented")`. That stub is the whole iOS investment.
- **File name matches its top-level declaration.** One public thing per file, named after it —
  Compose's idiom of several small `@Composable`s in one file holds only where they exist for the
  one the file is named after.
- **Test names read as sentences**, without a `test`/`should` prefix.