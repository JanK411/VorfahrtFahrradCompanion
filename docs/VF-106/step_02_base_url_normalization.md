# Step 2 — Base URL normalization

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md) · Depends on: nothing

Pure Kotlin, no Ktor, no Room. Fully unit-testable on its own — do it before anything that uses it.

## Do

`shared/src/commonMain/.../settings/BaseUrl.kt`:

```kotlin
fun normalizeBaseUrl(raw: String): String?   // null = invalid
```

Rules, in order:

1. `trim()`; blank → `null`
2. no `://` → prepend `http://` (so `192.168.178.42:8080` just works)
3. scheme other than `http`/`https` → `null`
4. strip trailing `/`
5. empty host → `null`

The stored base URL is a **full URL — origin plus optional path prefix**, not a hostname.

## Tests (`../../shared/src/commonTest`)

Table-driven:

| input                               | expected                           |
|-------------------------------------|------------------------------------|
| `192.168.178.42:8080`               | `http://192.168.178.42:8080`       |
| `https://vorfahrt.example.com/api/` | `https://vorfahrt.example.com/api` |
| `ftp://x`                           | `null`                             |
| `""`                                | `null`                             |
| `http://`                           | `null`                             |

## Done when

`./gradlew :shared:testAndroidHostTest` is green.
