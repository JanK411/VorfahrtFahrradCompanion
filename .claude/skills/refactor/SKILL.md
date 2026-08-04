---
name: refactor
description: Restructure existing code without changing its behaviour, under a fixed safety ritual — clean tree, characterization tests pinning the current behaviour, green baseline, refactoring, green again, ticket-prefixed commit. Use whenever code is reshaped rather than changed: "refactor X", "extract this into a service", "split this class", "rename across the codebase", "move this package", "clean up this file", "simplify this without changing what it does".
---

# Refactoring

A refactoring changes structure, never behaviour. The whole point of the ritual below is that
"behaviour unchanged" is *proven by a test run*, not asserted. Do not skip a step because the
change looks obviously safe — obviously-safe is exactly where silent behaviour changes hide.

If a step cannot be completed, **stop and report**. Never continue a refactoring on an unproven
baseline, and never widen the change to "fix" something noticed along the way — note it and raise
it after the commit.

## The six steps

### 1. Clean working tree

```shell
git status --porcelain
```

Must be empty. If it is not, stop and ask what to do with the changes — do **not** stash, commit
or discard them. A dirty tree makes step 5 meaningless: a test failure can no longer be attributed
to the refactoring.

Also confirm the branch is the intended one before touching anything.

### 2. Tests must pin the behaviour being refactored

Find what actually covers the target — not what covers the file's neighbours.

- **Pure logic** (`daylight/`, `settings/BaseUrl`, `net/TransportSecurity`, `criteria/CriterionLabel`,
  `patchnotes/` splitting) → `shared/src/commonTest/…`. Plain `kotlin.test`, nothing else needed.
- **ViewModels and state** → also `commonTest`, with `kotlinx-coroutines-test` and the shared
  `FakeClock` (`commonTest/…/FakeClock.kt`). `CriteriaViewModelTest` is the pattern to copy.
- **Android `actual`s** (`AndroidLocationProvider`, `AndroidScreenBrightness`, `AndroidAppDatabase`, …)
  → `shared/src/androidHostTest/…`. There is no Robolectric and no device-test suite in this repo, so
  anything needing a live `Context`, `LocationManager` or `Window` **cannot be pinned host-side**.
- **Composables** → no UI test infrastructure exists. A Composable's rendering is unpinnable today.

If coverage is missing or thin, **write characterization tests first**: tests that assert what the
code does *today*. Get them green, then commit them separately before the refactoring starts:

```
VF-<n>: Characterize <target> before restructuring it
```

That separate commit is what makes the refactoring commit reviewable — the diff of a refactoring
should contain no new test logic.

If the target's behaviour genuinely cannot be pinned by a test, say so and stop. An unpinnable
refactoring is a design discussion, not a mechanical task.

The common case here is extracting logic **out of** a Composable or an Android `actual` (see
`a85792b`, `d381bc3`). That is legitimate and the extracted logic is testable — but the test can only
be written *after* the extraction, so it cannot pin the before-state. Say plainly which part of the
move is test-proven and which rests on reading the diff.

#### Behaviour that looks wrong is raised, never pinned silently

Writing characterization tests means reading the code closely enough to notice things — an off-by-one,
a swallowed exception, a `when` branch that cannot be what was meant, a migration that drops a table.
**Stop and discuss it. Do not encode it in an assertion and move on.**

A characterization test is not a neutral record. It converts current behaviour into a stated
expectation, so a silently pinned bug becomes documented intent: the next reader sees a test
asserting it, and the next refactoring is obliged to preserve it. That is how a defect gets
promoted to a requirement.

So when something looks wrong:

1. Stop before writing the assertion.
2. Report it concretely — the input, the actual behaviour, the expected behaviour, and why the
   difference matters. Enough for a decision without re-reading the code.
3. Ask which way to go:
   - **Fix first** — the bug is fixed as its own change with its own test and its own commit
     (`VF-<n>: <fix>`), and only then does the refactoring start from the corrected behaviour.
   - **Pin deliberately** — the behaviour is kept for now, the test gets a comment saying it pins
     known-wrong behaviour and pointing at the ticket, so the next reader sees a decision rather
     than an endorsement.
   - **Not actually wrong** — it was intended, and the reasoning belongs in the test's KDoc so the
     question is not re-opened every time.

Never resolve this by choosing yourself. Fixing it inside the refactoring destroys the one
guarantee the ritual provides — that green-to-green means behaviour is unchanged — and pinning it
silently is the failure this section exists to prevent. Both look like a clean refactoring in the
diff, which is exactly why the decision has to be made out loud.

The same applies if the suspicion only surfaces during step 4: finish or abandon the refactoring
first, then raise it. Never fold a fix into a refactoring commit.

### 3. Green baseline

```shell
./gradlew :shared:testAndroidHostTest --tests "*.CriteriaViewModelTest"   # narrow
./gradlew :shared:testAndroidHostTest                                     # commonTest + androidHostTest
```

The suite must be **green before the refactoring**. A pre-existing failure is a blocker: report it
and stop, because after step 4 there is no way to tell that failure apart from one you introduced.

`testAndroidHostTest` compiles and runs `commonTest` *and* `androidHostTest` — it is the only test
task that runs on this machine. `iosTest` needs macOS and is never part of the baseline; do not
report an iOS-affecting refactoring as verified on the strength of it.

### 4. Refactor

Behaviour-preserving transformations only: extract/inline, rename, move, change visibility,
reorder declarations, replace a construct with an equivalent one.

Not part of a refactoring: new features, bug fixes, changed defaults, changed error handling,
changed persisted or serialized shapes, changed UI copy. If one of those is needed, finish the
refactoring, commit, and do it as separate work.

Prefer IntelliJ's refactorings over hand-editing for renames and moves — use the IntelliJ MCP
(`mcp__idea__rename_refactoring`). They update references the compiler alone would let you miss,
including the `actual`s in `iosMain` that no local Gradle task type-checks.

Repo-specific traps, all of which change behaviour while still compiling:

- **`expect`/`actual` sets move as one.** `platformHttpClientEngine()` and `AppDatabaseConstructor`
  have `actual`s in `androidMain` *and* `iosMain`. Renaming or moving the `expect` without the
  `iosMain` side still passes `testAndroidHostTest` — the iOS source set is simply not compiled by
  it. Step 5's metadata compile is what catches this.
- **Room entity properties are column names.** Renaming an `@Entity` class is free; renaming one of
  its properties renames the column, which needs a `version` bump in `AppDatabase` and a new
  `MIGRATION_n_n+1` — that is a schema change, not a refactoring. Room's KSP verifies `@Query`
  strings against the entities, so a mismatch there fails the build; it does **not** verify that an
  installed app can still open the file. Watch `schemas/…AppDatabase/*.json` in the diff: if a
  refactoring changed an exported schema, the refactoring changed the database.
- **`@Serializable` DTO properties are the server's wire format.** `CatalogueDto` / `CriterionDto`
  mirror what the backend sends. Renaming a property renames the JSON field and the app stops
  parsing real responses — no test in this repo would notice, because the fakes are constructed from
  the same class. The `"SINGLE"` / `"MULTI"` string literals in `toDomain`/`toDto` are wire values
  too, not enum names.
- **`@Serializable` route objects carry their qualified name.** The nav routes in `App.kt` are
  identified by class name; moving or renaming one changes the key a saved back stack is restored
  against. Fine within a session, a lost destination across process death.
- **Koin resolves `get()` by type, at runtime.** Reordering two same-typed constructor parameters of
  a `CriteriaViewModel` or `CachingCriteriaApi` compiles and silently swaps the arguments; extracting
  a collaborator into its own class needs a new binding in `di/AppModules.kt` or the screen throws on
  first open. Neither is a compile error and neither is covered by a test — check `AppModules.kt` by
  hand after any change to a constructor signature.
- **`patchNotes` order is behaviour.** `splitPatchNotes` derives new-vs-older from list position, not
  from parsing versions. Reordering the list changes what every user is shown. Conversely: a
  refactoring is by definition not user-visible, so it gets **no** patch note entry.
- **`commonMain` stays platform-free.** Moving code *into* `commonMain` must not bring `android.*`,
  `Context`, `java.time`, `java.io` or `java.util` with it. Moving code *out* of `commonMain` into
  `androidMain` is a portability decision, not a refactoring — raise it first.

### 5. Green again — the same tests, unchanged

```shell
./gradlew :shared:testAndroidHostTest                  # commonTest + androidHostTest
./gradlew :shared:compileIosMainKotlinMetadata         # iosMain still compiles (works on Linux)
./gradlew :androidApp:assembleDebug                    # only if androidApp/ or App.kt wiring changed
```

The iOS metadata compile is required whenever an `expect`/`actual`, a `commonMain` signature or
anything under `iosMain` was touched. It is the only iOS check available without a Mac, and it is
cheap — do not substitute "the Android tests pass" for it.

The test files should be **untouched** by step 4, apart from mechanical updates that a rename
forces (changed symbol names, changed imports). If you had to change what a test *asserts*, the
behaviour changed — either that is a bug you introduced, or the test was pinned to structure
rather than behaviour. Stop and report which; do not quietly adjust the assertion to match the new
output.

### 6. Commit

Match the existing history: ticket first, then an imperative sentence describing the structural
change. `0000` when there is no ticket. Never restate the diff.

```
VF-130: Move the boundary buttons out of the criteria screen
VF-115: Extract the elapsed-seconds ticker
```

The refactoring commit contains production-code movement only. If step 2 needed new tests, they
are already in their own commit.

Push only when asked.

## Reporting

State plainly: which tests pinned the behaviour, that they were green before *and* after, and
whether the iOS metadata compile passed. If the baseline could not be established (unpinnable
behaviour, pre-existing failure), say that instead of reporting success — an unverified refactoring
is the one outcome that must never be described as done.

List any behaviour raised under step 2 and how it was settled — fixed first, pinned deliberately,
or confirmed intended. A suspicion that was noticed but never resolved is reported as open, not
dropped because the refactoring itself went green.