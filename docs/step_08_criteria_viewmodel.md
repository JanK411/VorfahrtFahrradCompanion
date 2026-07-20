# Step 8 — CriteriaViewModel

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md) · Depends on: step 7

## Do

`criteria/CriteriaViewModel.kt`:

- `sealed interface CriteriaUiState`: `Loading` / `Failed(message)` /
  `Ready(catalogue, selections, submitState)`
- `submitState`: `Idle` / `InFlight` / `Error(message)`
- selections: `Map<String, Set<String>>` (criterionId → chosen values)
- selection reducer, one function, driven by `CriterionKind`:
  - `SINGLE`: tapping a chip replaces the selection; tapping the selected chip clears it
  - `MULTI`: tapping toggles membership
- `retry()` re-fetches the catalogue (no cache — fetch on screen open)

Submit flow:

1. `locationProvider.locations().first()` inside `withTimeout(~15 s)` — surface a clear error if
   no fix arrives or permission is missing (`AndroidLocationProvider` closes the flow with
   `SecurityException`)
2. build `Observation(location, selections.filterValues { it.isNotEmpty() })`
3. `api.submit(...)`; on success clear selections

No validation — unset criteria are simply omitted.

## Tests

- SINGLE vs MULTI selection reducer
- empty selections are omitted from the submitted body

## Done when

`./gradlew :shared:testAndroidHostTest` is green.
