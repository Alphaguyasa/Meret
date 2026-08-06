# CLAUDE.md — Meret

Persistent context for Claude Code. Read this before every task.

---

## What we are building

An offline-first Android app for field teams: offline vector maps, GPS track logging,
geotagged waypoints with forms, land area measurement, and offline-first sync to
Supabase/PostGIS. Target users are enumerators and extension workers in Ethiopia
carrying low-end Android devices with no reliable network.

**The defining constraint: the app must be fully functional with the network
permanently off.** Any feature that degrades without connectivity is a bug, not a
limitation.

---

## Non-negotiable invariants

1. **No network call is ever on the critical path of a user action.** Writes go to Room
   first, then to an outbox. The UI reads from Room only.
2. **Raw GPS points are never mutated or deleted by filtering.** Filters produce derived
   values stored alongside the raw fix. We must be able to re-run filtering later.
3. **All primary keys are client-generated UUIDs (v7, time-ordered).** The server never
   assigns IDs. This makes every sync operation idempotent.
4. **Every write is idempotent server-side.** `ON CONFLICT (id) DO NOTHING` or an explicit
   version check. Assume every request will be retried.
5. **No blocking work on the main thread.** All Room, file, and geometry work runs on
   `Dispatchers.IO` or `Dispatchers.Default`.
6. **No hardcoded strings in composables.** Everything through `strings.xml`. We ship
   `en` and `am` (Amharic) from day one; `values-am/` must never be allowed to drift.
7. **Tracking must survive process death.** State lives in Room + DataStore, not in a
   ViewModel or a companion object.

---

## Module structure

```
:app                      Application class, navigation host, DI wiring
:core:model               Pure Kotlin domain models. No Android imports.
:core:common              Result types, dispatchers, time provider, geo math
:core:database            Room entities, DAOs, migrations, converters
:core:datastore           Preferences, tracking state persistence
:core:network             Supabase client, DTOs, remote data sources
:core:designsystem        Theme, tokens, reusable composables
:core:location            LocationEngine abstraction, filters, foreground service
:core:maps                MapLibre wrapper, tile server, offline region manager
:feature:map              Main map screen
:feature:tracking         Track controls, track list, track detail
:feature:waypoint         Waypoint capture, form rendering, photo capture
:feature:measure          Area/distance measurement
:feature:tiles            Tile pack management UI
:feature:sync             Sync status, manual sync, peer sync
:feature:export           GPX/KML/GeoJSON/CSV/PDF export
:feature:settings         Settings, permissions onboarding, diagnostics
:sync                     WorkManager workers, outbox processor, conflict rules
:peer                     Nearby Connections peer sync
```

**Dependency rules, enforced by convention plugins:**
- `:core:model` depends on nothing.
- `:feature:*` may depend on `:core:*` and `:domain`, never on another `:feature:*`.
- `:core:database` and `:core:network` never leak their own types upward — repositories
  map to `:core:model` types at the boundary.

---

## Conventions

**Gradle**
- Version catalog in `gradle/libs.versions.toml`. No inline version strings anywhere.
- Convention plugins in `build-logic/` for `android-library`, `android-feature`,
  `jvm-library`, `hilt`, `room`, `compose`.
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`.

**Kotlin**
- Explicit API mode on for `:core:*` modules.
- `ktlint` + `detekt` in CI. Fix warnings, do not suppress them.
- Sealed interfaces for UI state. One `UiState` per screen, no `isLoading` +
  `errorMessage` + `data` triples.
- Repositories expose `Flow<T>` for reads and `suspend fun` returning
  `Result<T>` for writes.

**Compose**
- Stateless composables + a `*Route` composable that owns the ViewModel.
- `collectAsStateWithLifecycle()` only.
- No `LaunchedEffect(Unit)` for one-shot events — use a `Channel`-backed event Flow.

**Naming**
- DAOs: `TrackDao`, methods `observeX` / `getX` / `upsertX` / `deleteX`.
- Use cases: `StartTrackingUseCase`, one `operator fun invoke`.
- Workers: `SyncWorker`, `TileDownloadWorker`.

**Testing**
- Every geometry, filter, and area function gets a unit test with a known fixture.
- Room DAO tests run in-memory.
- `ReplayLocationEngine` plays a GPX file — every tracking feature is testable without
  leaving the desk. Fixtures live in `core/location/src/test/resources/traces/`.
- Do not write tests that assert on implementation detail. Test observable behaviour.

---

## Things that will go wrong, and what to do about them

**MapLibre API surface changes between versions.** Do not trust API signatures from
memory, including mine. Before writing MapLibre code, check the actual artifact:
`./gradlew :core:maps:dependencies` and read the sources jar or the version's docs.
Package root is `org.maplibre.android`.

**`asset://` and `file://` URLs in style JSON.** MapLibre Android resolves these, but the
style must be loaded with a URI that MapLibre understands. Test glyph and sprite loading
in aeroplane mode before assuming it works — a style that silently falls back to the
network will look fine on your desk and fail in the field.

**Amharic labels need Ethiopic glyph PBFs.** `LocalIdeographFontFamily` only covers CJK.
Ethiopic must come from generated glyph range PBFs bundled in assets. If labels render as
boxes, the glyph pack is wrong or the font stack name in the style does not match the
generated directory name.

**Android 14+ foreground service types.** `FOREGROUND_SERVICE_LOCATION` permission plus
`android:foregroundServiceType="location"` plus a running-notification. Starting the
service from the background will throw. Start it from a user-visible action only.

**OEM battery killers.** Tecno, Infinix, and Itel (Transsion, dominant in Ethiopia) plus
Xiaomi kill background services aggressively. We need an onboarding screen that detects
the manufacturer and deep-links to the right autostart/battery settings page, plus a
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` prompt. See `LOCATION_ENGINE.md`.

**Play Services may be absent or stale.** `LocationEngine` must have a
`PlatformLocationEngine` fallback using `LocationManager` with `GPS_PROVIDER`. Detect via
`GoogleApiAvailability` and fall back silently.

**Device clocks are wrong.** Never trust `System.currentTimeMillis()` for ordering.
Store both `elapsedRealtimeNanos` and wall-clock time on every fix, and use
`location.elapsedRealtimeNanos` for intra-track ordering.

---

## Workflow expectations

- Work one phase at a time. Do not start the next phase without being asked.
- Before writing code for a phase, restate the acceptance criteria from `BUILD_PLAN.md`
  and list the files you intend to create or change. Wait for confirmation on anything
  that touches the schema or the module graph.
- Run `./gradlew ktlintCheck detekt test` before declaring a phase done. Report actual
  output, not an assumption that it passed.
- Room schema changes require a migration and a migration test. No
  `fallbackToDestructiveMigration()` beyond phase 3.
- If a requirement in these docs is wrong or has become wrong, say so and propose the
  change. Do not silently work around it.
- Never commit secrets. Supabase URL and anon key go in `local.properties` and are read
  through `BuildConfig`. `local.properties` is gitignored — verify this before the first
  commit.
