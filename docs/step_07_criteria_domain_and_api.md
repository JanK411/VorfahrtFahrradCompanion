# Step 7 — Criteria domain, DTOs and API

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md) · Depends on: steps 3, 5

## Do

`shared/src/commonMain/.../criteria/`:

- `Criterion.kt` — `Criterion(id: String, kind: CriterionKind, values: List<String>)`,
  `enum class CriterionKind { SINGLE, MULTI }`, `Catalogue(criteria: List<Criterion>)`,
  `Observation(location, values: Map<String, Set<String>>)`
- `CriteriaDto.kt` — `@Serializable` DTOs + `toDomain()`. Ignores `weightRange`,
  `importanceRange`, `defaultImportance`. **Unknown `kind` strings degrade to `MULTI`** rather
  than failing the parse.
- `CriteriaApi.kt` — `interface CriteriaApi { suspend fun catalogue(): Catalogue; suspend fun submit(o: Observation) }`
- `KtorCriteriaApi.kt` — reads baseUrl/credentials from `SettingsRepository` **per call**, so
  editing settings takes effect on the next request with no client rebuild.

URL building must never concatenate strings, because the base URL may carry a path prefix:

```kotlin
url {
    takeFrom(settings.baseUrl)
    appendPathSegments("admin", "evaluation-model", "criterion-catalogue")
}
```

Submit target: `POST {baseUrl}/admin/evaluation-model/observations`, guessed body:

```json
{ "latitude": 52.1, "longitude": 4.3, "accuracyMeters": 8.0,
  "recordedAt": "2026-07-20T12:43:37Z",
  "values": { "WIDTH": ["W_2"], "ALLOWED_USERS": ["CARS", "CYCLISTS"] } }
```

`kotlin.time.Instant` serialises to ISO-8601 via `toString()` — no kotlinx-datetime needed.

## Watch out

**The POST contract is invented** — nothing in `http-testing/` documents it. Keep the DTO and its
mapping in one small file so correcting it is a 5-line change.

## Tests

- parsing the sample catalogue JSON (from `http-testing/`) into the domain model
- URL building against a prefixed base yields `/api/admin/evaluation-model/criterion-catalogue`

## Done when

`./gradlew :shared:testAndroidHostTest` and `:shared:compileKotlinIosArm64` pass.
