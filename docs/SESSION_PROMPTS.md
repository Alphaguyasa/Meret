# SESSION_PROMPTS.md

Copy-paste prompts, one per Claude Code session. Start each session fresh with
`/clear` so the context is clean, then paste.

Every prompt assumes `CLAUDE.md` and the other docs sit in the repo root.

---

## Setup

```
mkdir meret && cd meret
git init
# copy CLAUDE.md, ARCHITECTURE.md, DATA_MODEL.md, MAPS_OFFLINE.md,
# LOCATION_ENGINE.md, DESIGN_SYSTEM.md, BUILD_PLAN.md into the repo root
mkdir -p docs && mv ARCHITECTURE.md DATA_MODEL.md MAPS_OFFLINE.md \
  LOCATION_ENGINE.md DESIGN_SYSTEM.md BUILD_PLAN.md docs/
claude
```

Keep `CLAUDE.md` in the root; Claude Code reads it automatically.

---

## Session 0 — Tile pipeline

```
Read docs/MAPS_OFFLINE.md, Phase 0 only.

Build the offline tile pipeline as a standalone toolchain in tools/. No Android code
in this session.

Deliver:
1. tools/build-tiles.sh — Planetiler invocation for an Ethiopia mbtiles pack at z0-14,
   with a --bbox variant for a district-sized pack. Include prerequisite checks (java
   version, disk space, planetiler jar present) and fail early with clear messages.
2. tools/build-glyphs.sh — generate glyph PBF ranges for Noto Sans Regular and
   Noto Sans Ethiopic Regular. Document where to obtain the TTFs.
3. tools/build-sprite.sh — build the Maki sprite atlas at 1x and 2x.
4. tools/style/meret-style.json — a MapLibre style with:
   - a vector source pointing at {{TILE_SERVER}}
   - glyphs at asset://map/fonts/{fontstack}/{range}.pbf
   - sprite at asset://map/sprite/sprite
   - text-font stack ["Noto Sans Regular", "Noto Sans Ethiopic Regular"]
   - text-field using ["coalesce", ["get","name:am"], ["get","name"]]
   - a restrained cartography suited to outdoor daylight reading: muted landcover,
     clear road hierarchy, high-contrast labels with halos
   - layer ids documented so the app can insert its own layers below the first symbol
     layer
5. tools/preview/ — a minimal static page using MapLibre GL JS plus a tiny Node or
   Python mbtiles server, so I can verify the pack in a browser before touching Android.
6. tools/README.md — the exact command sequence from zero to a verified pack, plus the
   sqlite3 verification queries.

Ask me before assuming any font file or icon set is present locally.
```

---

## Session 1 — Skeleton

```
Read CLAUDE.md and docs/ARCHITECTURE.md and docs/BUILD_PLAN.md Phase 1.

Set up the project skeleton. Before writing anything, list the files you plan to create
and confirm the module list with me.

Requirements:
- Gradle 8.x with a version catalog. No inline versions anywhere.
- build-logic convention plugins: meret.android.application, meret.android.library,
  meret.android.feature, meret.android.compose, meret.jvm.library, meret.hilt, meret.room
- Every module from ARCHITECTURE.md, created and wired but empty
- minSdk 26, targetSdk 35, compileSdk 35, Java 17, Kotlin 2.x
- Hilt, Compose BOM, Material 3, navigation-compose, lifecycle
- ktlint and detekt configured, with a config that we will actually keep green
- A GitHub Actions workflow running ktlintCheck, detekt, and test
- Theme, colour tokens, and typography from docs/DESIGN_SYSTEM.md
- strings.xml in values/ and values-am/, with the missingTranslation lint check set to
  error
- .gitignore covering local.properties, and a local.properties.example documenting
  SUPABASE_URL and SUPABASE_ANON_KEY

End state: ./gradlew build is green and the app launches to a themed placeholder screen.
Run the build and show me the actual output.
```

---

## Session 2 — Core model and geometry

```
Read docs/BUILD_PLAN.md Phase 2 and docs/DATA_MODEL.md.

Implement :core:model and :core:common. Pure Kotlin, no Android imports, explicit API
mode on.

:core:model — domain models for Project, Track, TrackPoint, Waypoint, WaypointPhoto,
Measurement, Assignment, FormTemplate, TilePack, plus the enums for sync state, track
status, and provider.

:core:common:
- Result<T> with a sealed AppError hierarchy (Network, Storage, Permission, Gps,
  Conflict, Unexpected)
- DispatcherProvider, TimeProvider (injectable, so tests control the clock)
- UuidV7 generator with monotonicity within the same millisecond
- GeoMath: haversineDistance, initialBearing, destinationPoint, geodesicPolygonArea
  using spherical excess, pointInPolygon, boundingBox, expandBbox
- DouglasPeucker.simplify with a metre-based epsilon using perpendicular distance on a
  local projection
- KalmanLocationFilter, constant-velocity 2D, with reset()

Tests are the deliverable here, not an afterthought:
- Geodesic area against a published reference polygon, within 0.1%
- Haversine against known city-pair distances
- DP simplification: on a 5,000-point trace, >80% reduction at eps=3m with max deviation
  under 3m
- Kalman: an injected-noise fixture where the filtered path is measurably closer to
  ground truth than the raw
- Degenerate inputs: empty, single point, duplicate points, antimeridian crossing,
  polygon with collinear vertices

Generate the fixture traces programmatically in the test source set and commit them.
Target >85% line coverage for both modules. Report the actual coverage number.
```

---

## Session 3 — Database

```
Read docs/BUILD_PLAN.md Phase 3 and docs/DATA_MODEL.md.

Implement :core:database.

- Room entities exactly matching the schema in DATA_MODEL.md, including every index
- Type converters for enums and for the JSON columns
- DAOs: ProjectDao, TrackDao, TrackPointDao, WaypointDao, WaypointPhotoDao,
  MeasurementDao, AssignmentDao, FormTemplateDao, TilePackDao, OutboxDao,
  SyncConflictDao
- Reads return Flow, writes are suspend
- TrackPointDao must have a batched insert taking a List and running in one transaction
- OutboxDao: claimBatch(limit, now) ordered by (priority, created_at), plus
  markFailed with backoff
- Schema export enabled and the JSON committed
- MeretDatabase with a migration strategy in place from the start; no
  fallbackToDestructiveMigration

Repository interfaces go in :core:model or :domain, implementations in :core:database's
data package — but the repositories must return :core:model types, never Room entities.
Write the mappers.

Instrumented tests with an in-memory database:
- Every DAO's happy path
- A benchmark inserting 100,000 track points in batches of 20; assert under 3 seconds
- observeTrackWithPoints on a 20,000-point track emits within 200ms
- Outbox claim/backoff ordering under concurrent access

Show me the benchmark numbers from a real run.
```

---

## Session 4 — Location engine

```
Read docs/LOCATION_ENGINE.md fully, and docs/BUILD_PLAN.md Phase 4.

Implement :core:location, excluding the foreground service — that is the next session.

- LocationEngine interface as specified
- FusedLocationEngine using FusedLocationProviderClient with the LocationRequest config
  and the four adaptive profiles
- PlatformLocationEngine using LocationManager with GPS_PROVIDER
- ReplayLocationEngine that parses a GPX file and emits fixes at 1x, 10x, or instantly
- EngineSelector using GoogleApiAvailability, falling back silently but exposing which
  engine is active
- GnssStatusMonitor exposing satellites used and a derived quality level
- The filter chain as a composable pipeline: ValidityGate, KalmanStage,
  DistanceAccumulator, AutoPauseDetector — each independently testable
- TrackingConfig as a data class, persisted via DataStore

Critical details from the doc that I want to see honoured:
- elapsedRealtimeNanos is the ordering key, not wall clock
- rejected fixes are persisted with is_outlier, not dropped
- distance only accumulates when the step exceeds max(4m, accuracy * 0.5)

Tests with GPX fixtures in core/location/src/test/resources/traces/:
- a clean 1km walk: filtered distance within 2% of known
- the same trace with injected 200m outliers: outliers rejected, distance unchanged
- a stationary 30-minute trace: accumulated distance under 50m
- a trace with a 5-minute gap: segment boundary detected, Kalman reset

Do not write the Service yet.
```

---

## Session 5 — Tracking service and permissions

```
Read docs/LOCATION_ENGINE.md and docs/BUILD_PLAN.md Phase 5.

Implement the foreground service and the permission/OEM onboarding.

TrackingService:
- foregroundServiceType="location", startForeground within 5 seconds on every path
- PARTIAL_WAKE_LOCK held while tracking, released on pause and stop
- START_STICKY with state persisted in DataStore so a restart resumes the active track
  rather than starting a new one
- Consumes LocationEngine.fixes through a Channel, runs the filter chain on
  Dispatchers.Default, batches Room writes at 20 points or 10 seconds
- Live notification: duration, distance, point count, GPS quality, Pause and Stop actions
- onTaskRemoved keeps running
- Commands via a bound interface or explicit intents; the UI never touches the engine

Permissions:
- A staged flow: fine location -> POST_NOTIFICATIONS -> background location (with a
  rationale screen before sending them to settings) -> battery optimisation exemption
- Handle the coarse-only grant on Android 12+ with a clear explanation

DeviceCompat:
- oemGuidanceFor(manufacturer) covering Transsion (Tecno/Infinix/Itel), Xiaomi, Oppo,
  Vivo, Huawei, Samsung, OnePlus, and a generic fallback
- Every deep-link startActivity wrapped in try/catch with a ManualSteps fallback
- Onboarding screens for battery optimisation, autostart, and recents-lock

TrackingHealthMonitor:
- Records expected vs actual fix counts per 10-minute window
- Logs a tracking_gap event when a gap exceeds 3x the sampling interval
- Surfaces it as a warning

Diagnostics screen with everything listed in LOCATION_ENGINE.md, plus a copy-to-clipboard
plain-text report.

Then tell me exactly how to test this on a physical device, including the adb commands to
simulate Doze and to inspect the service state.
```

---

## Session 6 — Offline map

```
Read docs/MAPS_OFFLINE.md fully, and docs/BUILD_PLAN.md Phase 6.

Implement :core:maps.

Before writing MapLibre code: add the dependency, run ./gradlew :core:maps:dependencies,
and verify the actual API surface of the version you pulled. Do not trust API signatures
from memory, including mine. Tell me the version and anything that differs from the doc.

- MbtilesReader: SQLite reads from a .mbtiles file, TMS->XYZ row flip, gzip detection by
  magic bytes with Content-Encoding passthrough, metadata parsing into a TileJSON model
- LocalTileServer: NanoHTTPD bound to 127.0.0.1 on an ephemeral port, serving
  /{packId}/tiles.json and /{packId}/{z}/{x}/{y}.pbf, lifecycle-bound start/stop
- Network security config permitting cleartext for 127.0.0.1 only — not globally
- StyleProvider: loads style JSON from assets, substitutes {{TILE_SERVER}} with the live
  port, writes to cacheDir, returns a file:// URI
- MapLibreMap Compose wrapper: AndroidView over MapView, DisposableEffect forwarding
  every lifecycle method including onLowMemory, camera position in rememberSaveable
- MapLayerManager: helpers to add GeoJsonSources and layers below the first symbol layer

:feature:map — the map screen with the location puck, follow mode, compass, scale bar,
zoom controls, and the GpsQualityChip.

Also add a tile pack import flow using the Storage Access Framework, with validation
(format=pbf, bounds present, tile count) and a TileImportWorker with progress
notification for the large copy.

Acceptance: aeroplane mode, map of Addis Ababa renders with Amharic labels, smooth pan
and zoom, no leak or crash on rotation. Tell me how to verify each of those.
```

---

## Session 7 — Tracking UI

```
Read docs/BUILD_PLAN.md Phase 7 and docs/DESIGN_SYSTEM.md.

Implement :feature:tracking and the track rendering on the map.

Components per the design system: RecordButton (72dp, tap to pause, long-press to stop),
TrackingBar (collapsed and expanded states), GpsQualityChip, SyncBadge, OfflineIndicator.

Map rendering — follow docs/MAPS_OFFLINE.md, do not use the annotation API:
- Active track: one GeoJsonSource updated at most every 2 seconds with the simplified
  line, never on every fix
- Historical tracks: viewport-scoped, loaded lazily
- Paused segments rendered dashed
- All track layers below the basemap's first symbol layer, with a white halo

Screens: track list grouped by day with distance and sync state; track detail with map,
stats, elevation profile, segment list, rename, delete, export entry point.

Destructive actions use a 5-second snackbar undo, not a confirmation dialog.

Use tabular figures for all live-updating numbers.

Acceptance: replay a 2-hour fixture trace at 10x through ReplayLocationEngine and confirm
the live line keeps up with no dropped frames. Show me the frame timing.
```

---

## Session 8 — Waypoints, forms, photos

```
Read docs/BUILD_PLAN.md Phase 8 and docs/DATA_MODEL.md.

Implement :feature:waypoint.

- WaypointSheet: bottom sheet over the map, expands to full height, photo strip,
  category chips, form fields
- Form template renderer from schema_json. Field types: text, number, select,
  multiselect, date, photo, gps_point. Support a visible_if conditional referencing
  another field's value. Define the schema format and document it in
  docs/FORM_SCHEMA.md.
- Validation: required, min/max, regex, with errors shown inline
- CameraX capture, compress to 1600px long edge at JPEG q80, strip EXIF except the GPS
  block, compute sha256
- Draft autosave to Room on every field change, restored on process death
- Waypoint list with filters, and a clustered SymbolLayer on the map

Acceptance test: enter a 12-field form with 3 photos, force-stop the app mid-entry with
adb, relaunch, and confirm nothing was lost. Write this as an instrumented test if you
can, and tell me the manual steps either way.
```

---

## Session 9 — Sync

```
Read docs/DATA_MODEL.md sync sections and docs/BUILD_PLAN.md Phase 9.

Two parts. Do the schema first and let me apply it before you write the client.

Part A — supabase/:
- migrations with the full PostGIS schema, all tables, indexes, and the RPCs
  ingest_track_points and finalise_track
- RLS policies for every table: own-row access for enumerators, org-scoped select for
  supervisors and admins
- A seed script with a demo org, two users, a project, and a form template
- A test script proving RLS actually works: authenticate as user B and confirm user A's
  rows are invisible. I want to see this run.

Part B — :core:network and :sync:
- Supabase client with the URL and anon key from BuildConfig
- Auth with a long-lived session; the app must be fully functional while the token is
  expired, and refresh opportunistically
- Remote data sources and DTOs, with mappers to :core:model
- OutboxProcessor: claims batches, groups by entity type, chunks track points at 500,
  posts, deletes on success
- Backoff: attempts++, next_attempt_at = now + min(2^attempts * 30s, 6h) with ±20% jitter
- Conflict handling: on version mismatch write a sync_conflict row, mark the entity, do
  not discard local data
- SyncWorker with a mutex so two workers cannot drain concurrently
- PhotoUploadWorker, unmetered by default
- Delta pull with per-table cursors in DataStore
- :feature:sync screen: outbox depth, oldest pending, last error, conflicts with a
  resolution UI, manual sync trigger

Acceptance: collect a day of data in aeroplane mode, connect, sync. Then run sync again.
Verify with a SQL count that there are no duplicates. Show me the query and the result.
```

---

## Session 10 — Measurement

```
Read docs/BUILD_PLAN.md Phase 10.

Implement :feature:measure.

- Walk mode: start, walk the perimeter, vertices sampled from filtered fixes with a
  minimum spacing; live perimeter and area as the polygon closes
- Tap mode: tap vertices on the map, drag to adjust, undo last, close polygon
- Distance mode: polyline, cumulative length with per-segment labels
- Geodesic area via GeoMath.geodesicPolygonArea
- Units: m², hectare, and timad with a configurable hectare ratio defaulting to 0.25
- MeasurementOverlay per the design system
- Save with a name and category, list view, map overlay, export as GeoJSON/KML

Acceptance: a tapped rectangle of known corner coordinates matches a reference GIS area
within 0.1%. Include that as a unit test with real coordinates. Then tell me how to
validate walk mode against a known plot in the field.
```

---

## Session 11 — Assignments and coverage

```
Read docs/BUILD_PLAN.md Phase 11.

- Pull assignments (polygon GeoJSON) with the sync delta pull
- Render as FillLayer + LineLayer per the design tokens
- CoverageCalculator: a ~50m grid over the assignment bbox, mark cells containing a
  walked point, coverage = covered cells inside the polygon / total cells inside. Handle
  the point-in-polygon test for partial edge cells sensibly and document the choice.
- Recompute on track completion, not continuously. Cache the result.
- Coverage overlay FillLayer
- Geofence entry/exit events on assignment boundaries, logged as events
- :feature:assignment list with progress bars and a map view

Acceptance: replay a trace that fully covers a test polygon and assert coverage > 90%;
replay one covering half and assert it lands between 40% and 60%. Unit tests on the
calculator with synthetic grids, plus one end-to-end replay test.
```

---

## Session 12 — Export

```
Read docs/BUILD_PLAN.md Phase 12.

Implement :feature:export.

- GPX 1.1: trk with trkseg per segment, wpt for waypoints, extensions for accuracy and
  speed
- KML: styled LineString per track, Placemarks for waypoints, photo references
- GeoJSON FeatureCollection: LineString features for tracks, Point features for
  waypoints and measurements, Polygon for measured areas
- CSV: one file of points, one of waypoints with form fields flattened to columns.
  UTF-8 with BOM so Excel renders Amharic correctly.
- PDF day report: a static map image rendered from MapLibre snapshot, summary stats,
  waypoint table, photo thumbnails
- Share via FileProvider, and a save-to-Downloads option
- Export runs in a Worker with progress for large tracks

Acceptance: exported GPX opens in QGIS and Google Earth with the geometry in the right
place; the CSV opens in Excel with Amharic intact. Write a round-trip test that exports
GPX and reimports it through the ReplayLocationEngine parser, asserting the points match.
```

---

## Session 13 — Peer sync

```
Read docs/ARCHITECTURE.md ADR-008 and docs/DATA_MODEL.md peer sync section, and
docs/BUILD_PLAN.md Phase 13.

Implement :peer using the Nearby Connections API with P2P_CLUSTER, following the same
approach as my previous offline mesh chat project.

- Advertise and discover with a service id scoped to org_id
- On connection, exchange a digest: (entity_type, entity_id, payload_sha256) for all
  outbox rows plus rows synced within a configurable recent window
- Each side requests only what it lacks, receives payloads, verifies the signature and
  hash, inserts into Room preserving origin_device_id, and enqueues into its own outbox
- Payloads use the identical JSON serialisation as the server path — one format, not two
- Session caps: 50MB, 5 minutes; photos only over Wi-Fi Direct, never BLE
- :feature:sync peer UI: nearby devices, connect, transfer progress, a summary of what
  was exchanged

Acceptance: device A collects offline, peer-syncs to B, B goes online. Verify in Postgres
that A's rows exist exactly once with origin_device_id set to A. Show me the query.

Tell me how to test this with two emulators, or whether physical devices are required.
```

---

## Session 14 — Supervisor dashboard

```
New repo: meret-dashboard. Next.js 15 App Router, TypeScript, Tailwind, MapLibre GL JS,
Supabase JS client against the same project.

- Auth with Supabase, org-scoped by RLS
- Team map: all tracks for the org, filterable by user, project, and date range, rendered
  from PostGIS GeoJSON. Use ST_AsGeoJSON in a view or RPC; do not ship raw point tables
  to the browser.
- Track detail: geometry, stats, associated waypoints, photos from Storage with signed
  URLs
- Coverage view per assignment
- Submission review table with photo lightbox
- Export CSV and GeoJSON
- Deploy to Vercel

Reuse the visual language from docs/DESIGN_SYSTEM.md so the app and dashboard read as one
product.

Acceptance: log in as an account from a different org and confirm zero rows are visible.
Render 50 tracks and confirm the map stays interactive.
```

---

## Useful mid-session prompts

**When something is off:**
```
Before changing anything: explain what you think is happening and what evidence supports
that. If you are not sure, say so and tell me what you would need to check.
```

**Before a risky change:**
```
List the files you will touch and what will change in each. Wait for my go-ahead.
```

**Reviewing a phase:**
```
Review the last phase against its acceptance criteria in docs/BUILD_PLAN.md. For each
criterion say pass, fail, or untested, with the evidence. Do not mark anything as passing
that you have not actually run.
```

**Keeping the docs true:**
```
The implementation has drifted from the docs in ways I have accepted. Update
docs/ARCHITECTURE.md and docs/DATA_MODEL.md to match reality, and list every change you
made so I can check it.
```
