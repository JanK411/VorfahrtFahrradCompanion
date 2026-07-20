# Step 5 — HTTP client

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md) · Depends on: step 1

## Do

`shared/src/commonMain/.../net/HttpClient.kt` — a factory taking an `HttpClientEngine`:

- `ContentNegotiation` + `json(Json { ignoreUnknownKeys = true })`
- no `defaultRequest` base URL — the base URL is read per call from settings (step 7)

Per-platform engine bindings only:

- `androidMain`: `OkHttp`
- `iosMain`: `Darwin`

## Auth note

`../../http-testing/criterion-catalogue.http` uses IntelliJ's two-token
`Authorization: Basic user pass` form, which the IDE base64-encodes for you. Ktor needs real base64 — use
`basicAuth(user, pass)` per request (simplest, since credentials come from settings per call) or `ktor-client-auth`.

## Done when

`./gradlew :shared:compileKotlinIosArm64` passes.
