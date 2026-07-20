# Step 10 — Verification

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md) · Depends on: all previous steps

## Automated

1. `./gradlew :shared:compileKotlinIosArm64` — proves nothing platform-specific leaked into
   `commonMain`.
2. `./gradlew :shared:testAndroidHostTest` — catalogue parsing, selection reducer, omission of
   empty selections, `normalizeBaseUrl` table, prefixed-base URL building.

## On a physical phone

`./gradlew :androidApp:assembleDebug`, install, backend running:

- Settings → base URL `http://<dev-box-LAN-IP>:8080` + `vorfahrt` / password → save.
  **This is the case that fails with `localhost`.**
- Criteria tab → 8 criterion groups appear; `ALLOWED_USERS` allows multiple chips, the rest one
- select a few, submit outdoors or with a mock fix, confirm the server receives the POST
- if the public endpoint is reachable, repeat with `https://vorfahrt.example.com`
- change to a deliberately wrong base URL → error + Retry; change back → recovers with **no app
  restart**, proving settings are read per call
- stop the backend, reopen the tab → error state with a working Retry
- temporarily add a criterion/value to the server response → it appears with no rebuild

## Release manifest

`./gradlew :androidApp:assembleRelease`, then confirm
`androidApp/build/intermediates/merged_manifests/release/AndroidManifest.xml` contains **no**
`usesCleartextTraffic="true"`.
