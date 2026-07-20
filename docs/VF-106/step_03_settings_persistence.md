# Step 3 — Settings persistence (Room)

Parent plan: [criteria-screen-plan.md](criteria-screen-plan.md) · Depends on: step 1, step 2

## Do

`shared/src/commonMain/.../settings/`:

- `Settings.kt` — `data class Settings(val baseUrl: String, val username: String, val password: String)`
- `db/SettingsEntity.kt` — single-row entity, fixed `@PrimaryKey id = 0`
- `db/SettingsDao.kt` — `Flow<SettingsEntity?>` observe + `suspend upsert`
- `db/AppDatabase.kt` — `@Database`, `@ConstructedBy(AppDatabaseConstructor::class)`
- `db/AppDatabaseConstructor.kt` — `@Suppress("KotlinNoActualForExpect")`
  `expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>` (the actual is
  generated; write no actual yourself)
- `SettingsRepository.kt` — `val settings: Flow<Settings>` (defaults when the row is missing) +
  `suspend fun save(settings: Settings)`

Builder config: `BundledSQLiteDriver()`, `setQueryCoroutineContext(Dispatchers.IO)`.

The database **builder** is platform-specific (needs `Context` on Android) — it is *not* created
here. It gets declared in `MainActivity`'s `androidModule` in step 6.

## Notes

- The password lands in the DB in plaintext. Acceptable for a personal admin tool; write it down,
  don't hide it.
- Room is heavier than three strings warrant (KSP, schema dir, `expect object`). This is a
  deliberate deviation from the YAGNI ladder, taken because queued/offline observations will want
  Room anyway.

## Done when

`./gradlew :shared:compileKotlinIosArm64` passes and the schema JSON appears under
`shared/schemas/`.
