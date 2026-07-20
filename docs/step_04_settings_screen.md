# Step 4 — Settings screen

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md) · Depends on: step 2, step 3

## Do

`shared/src/commonMain/.../settings/SettingsScreen.kt` + a `SettingsViewModel`:

- three `TextField`s: base URL, username, password
- base-URL field: `KeyboardType.Uri`, `autoCorrectEnabled = false`, `singleLine = true`
- `supportingText` shows either the normalised value or the validation error; `isError` wired to
  the same `normalizeBaseUrl` result
- placeholder carries both real examples: `http://192.168.178.42:8080` and
  `https://vorfahrt.example.com`
- Save disabled while the base URL is invalid; saves the **normalised** string
- keep the raw text in local screen state so typing isn't fought with

## Done when

The screen compiles and previews/renders; saving round-trips through `SettingsRepository`.
(It is not reachable in the app yet — that's step 6.)
