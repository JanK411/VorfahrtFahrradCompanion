# Step 9 — CriteriaScreen and final wiring

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md) · Depends on: steps 6, 8

## Do

`criteria/CriteriaScreen.kt` — `LazyColumn` over `catalogue.criteria`. Per criterion:

- header `Text(criterion.id)` — raw IDs shown verbatim (`SURFACE_QUALITY`, `W_0_5`)
- a `FlowRow` of `FilterChip`s, one per value

**One composable handles both kinds** — the kind only changes the click reducer. No `when` over
criterion IDs anywhere; that is what keeps the screen dynamic.

Plus: Loading indicator, `Failed` state with a Retry button, a submit button, and a `Snackbar`
reporting submit success.

Wiring:

- `di/AppModules.kt`: `criteriaModule` — `viewModel { CriteriaViewModel(get(), get()) }`,
  `single<CriteriaApi> { KtorCriteriaApi(get(), get()) }`; append to `appModules`
- `App.kt`: rename the enum entry `Dummy1` → `Criteria` (label "Criteria",
  `Icons.Filled.Checklist`), dispatch to `CriteriaScreen(modifier)`

## Done when

`./gradlew :androidApp:assembleDebug` passes and the tab renders the live catalogue.
