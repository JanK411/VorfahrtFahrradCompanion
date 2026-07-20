# Step 1 — Dependencies and build config

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md)

Nothing in this step is visible in the app. It only makes the later steps compilable.

## Do

1. `gradle/libs.versions.toml` — add versions and library/plugin aliases. No version may be
   hardcoded in a `build.gradle.kts`.

   ```toml
   kotlinx-serialization = "1.9.0"
   ksp                   = "2.3.9"
   ktor                  = "3.5.1"
   room                  = "2.8.4"
   sqlite                = "2.7.0"
   ```

   Libraries: `ktor-client-core`, `ktor-client-content-negotiation`,
   `ktor-serialization-kotlinx-json`, `ktor-client-auth`, `ktor-client-okhttp`,
   `ktor-client-darwin`, `kotlinx-serialization-json` (**not** `-core`),
   `androidx-room-runtime`, `androidx-room-compiler`, `androidx-sqlite-bundled`.
   Plugins: `kotlin.plugin.serialization`, `ksp`, `androidx.room`.

2. `shared/build.gradle.kts`
   - apply the serialization, KSP and Room plugins
   - `commonMain`: ktor core + content-negotiation + json + auth, serialization-json, room-runtime,
     sqlite-bundled
   - `androidMain`: `ktor-client-okhttp`
   - `iosMain`: `ktor-client-darwin`
   - register the Room KSP processor **per target** (`add("kspAndroid", …)`,
     `add("kspIosArm64", …)`, `add("kspIosSimulatorArm64", …)`)
   - `room { schemaDirectory("$projectDir/schemas") }`

3. `androidApp/src/main/AndroidManifest.xml` — add `android.permission.INTERNET`.

4. New `androidApp/src/debug/AndroidManifest.xml` — `android:usesCleartextTraffic="true"` on
   `<application>`. Add `tools:replace` only if the merger complains.

## Watch out

KSP 2.3.x runs on its own version line, decoupled from Kotlin. 2.3.9 is unverified against
Kotlin 2.4.10. If the build fails, fall back to the newest `2.4.10-*` KSP release.

## Done when

- `./gradlew :shared:compileKotlinIosArm64` passes
- `./gradlew :androidApp:assembleDebug` passes
- build stays configuration-cache compatible
