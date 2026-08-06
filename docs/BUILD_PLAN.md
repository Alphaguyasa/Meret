# BUILD_PLAN.md

14 phases. One Claude Code session each, roughly. Phases 1–8 are the shippable core;
9–14 are what turn it into a product and a portfolio piece.

Do not start a phase before the previous one's acceptance criteria pass. The temptation
to build the map before the location engine is strong and it is a trap — you end up
debugging two unfamiliar systems at once.

---

## Phase 0 — Tile pack (do this first, outside the app)

Before any Android code. If the tile pipeline does not work, the project does not work,
and you want to know that on day one.

- Install Planetiler, build `ethiopia.mbtiles` at z0–14
- Generate glyph PBFs for Noto Sans and Noto Sans Ethiopic
- Build the sprite atlas from Maki
- Assemble `style.json` with `asset://` glyphs and sprite, placeholder tile URL
- Verify in MapLibre GL JS in a browser with a local `pmtiles`/`mbtiles` server, and
  confirm **Amharic labels render**

**Done when:** you can pan a map of Addis Ababa in a browser, offline, with Amharic
place names, served entirely from your own files.

---

## Phase 1 — Project skeleton

- Gradle version catalog, `build-logic` convention plugins
- Module graph from `ARCHITECTURE.md`, all modules empty but wired
- Hilt, Compose, Material 3, navigation
- ktlint, detekt, CI workflow running `ktlintCheck detekt test`
- App theme and tokens from `DESIGN_SYSTEM.md`
- `strings.xml` for `en` and `am`, with a lint rule failing on missing translations

**Done when:** `./gradlew build` is green, the app launches to a themed empty screen, and
CI passes on a clean checkout.

---

## Phase 2 — Core model and geometry

Pure Kotlin, no Android. This is where the unit tests live.

- Domain models in `:core:model`
- `:core:common`: `Result`/`AppError`, dispatchers, `TimeProvider`, UUIDv7 generator
- `GeoMath`: haversine, bearing, geodesic polygon area (spherical excess), point-in-
  polygon, bbox ops, Douglas–Peucker
- `KalmanLocationFilter`
- Unit tests with fixtures: a known 1 km walk, a synthetic noisy trace, a polygon of known
  area (validate against a published figure), degenerate cases (2 points, duplicate
  points, antimeridian)

**Done when:** area computation matches a reference value within 0.1%, DP simplification
of a 5,000-point trace at ε=3 m removes >80% of points and stays within 3 m of the
original, and coverage of `:core:common` and `:core:model` exceeds 85%.

---

## Phase 3 — Database

- Room entities, DAOs, type converters, indices per `DATA_MODEL.md`
- Exported schema JSON committed
- In-memory DAO tests, including a 100k-point insert benchmark
- Batched insert path proven: 20 points in one transaction

**Done when:** inserting 100,000 track points takes under 3 seconds in a device test, and
`observeTrackWithPoints` for a 20,000-point track emits in under 200 ms.

---

## Phase 4 — Location engine

- `LocationEngine` interface + `Fused`, `Platform`, `Replay` implementations
- Filter chain: validity gate → Kalman → distance accumulation → auto-pause
- GPX fixture traces in test resources
- `GnssStatus` monitoring

**Done when:** `ReplayLocationEngine` plays a fixture GPX and produces a filtered track
whose total distance is within 2% of the fixture's known distance, and injected outliers
are rejected without displacing the line.

---

## Phase 5 — Tracking service

- `TrackingService` with FGS type `location`, wakelock, sticky restart
- Live notification with duration/distance/quality and Pause/Stop actions
- State persisted so a process death resumes the active track
- Permission flow: fine → notifications → background → battery optimisation
- `DeviceCompat` OEM guidance with deep links and manual fallbacks
- `TrackingHealthMonitor` gap detection
- Diagnostics screen

**Done when:** a track survives 30 minutes with the screen off and the app swiped away on
a real Transsion or Xiaomi device, with no gap longer than 3× the sampling interval — and
when a gap *is* induced, the health monitor reports it.

---

## Phase 6 — Offline map

- `MbtilesReader` with the TMS→XYZ flip and gzip passthrough
- `LocalTileServer` on loopback, ephemeral port, lifecycle-bound
- Network security config permitting cleartext for `127.0.0.1` only
- `StyleProvider` with placeholder substitution
- `MapLibreMap` Compose wrapper with full lifecycle forwarding
- Map screen: user location puck, follow mode, compass, scale bar, zoom controls

**Done when:** the device is in aeroplane mode, the map renders Addis Ababa with Amharic
labels, panning holds 60 fps, and rotating the device does not leak or crash.

---

## Phase 7 — Tracking UI + track rendering

- `RecordButton`, `TrackingBar`, `GpsQualityChip`
- Active track as a `GeoJsonSource` updated every 2 s
- Historical tracks loaded for the viewport
- Track list and track detail with stats, elevation profile, segment list
- Undo-window snackbars for stop and delete

**Done when:** a 2-hour walked track renders smoothly, the live line keeps up without
frame drops, and stopping a track by accident is recoverable.

---

## Phase 8 — Waypoints, forms, photos

- Waypoint capture sheet over the map
- JSON form template renderer: text, number, select, multiselect, date, photo, GPS-point,
  and a `visible_if` conditional
- CameraX capture, compress to 1600px q80, preserve the GPS EXIF block
- Draft autosave on every field change
- Waypoint list, clustered symbol layer on the map

**Done when:** a waypoint with three photos and a twelve-field form survives a forced
process death mid-entry with no data loss.

---

## Phase 9 — Sync

- Supabase project, PostGIS schema, RPCs, RLS policies
- Auth: email/password plus an offline-capable session (long-lived refresh token, and the
  app must work fully while the token is expired)
- Outbox processor, `SyncWorker`, exponential backoff with jitter
- `PhotoUploadWorker`, unmetered by default
- Delta pull for projects, templates, assignments
- Conflict capture and a resolution UI
- Sync screen

**Done when:** aeroplane mode for a full day of data collection, then connect — everything
uploads exactly once, and running the sync twice creates no duplicates. Verify with a row
count in Postgres, not by looking at the UI.

---

## Phase 10 — Measurement

- Walk-the-perimeter and tap-the-vertices modes
- Live perimeter and geodesic area
- Units: m², hectare, *timad* (configurable, default 0.25 ha)
- Save, name, export, overlay on the map

**Done when:** walking a known rectangular plot yields an area within 3% of the surveyed
figure, and the tapped-vertex mode matches a reference GIS computation within 0.1%.

---

## Phase 11 — Assignments and coverage

- Pull assignment polygons, render with fill and outline
- Local coverage grid: ~50 m cells, bucket walked points, compute percentage
- Coverage overlay layer, recomputed on track completion
- Geofence entry/exit events on assignment boundaries
- Assignment list with progress

**Done when:** coverage percentage for a fully walked polygon exceeds 90% and the overlay
matches the walked path visually.

---

## Phase 12 — Export

- GPX 1.1 with track segments and waypoints
- KML with styled tracks
- GeoJSON `FeatureCollection`
- CSV, one row per point, and a second CSV for waypoints with flattened form fields
- PDF day report: map image, stats, waypoint table, photo thumbnails
- Share via `FileProvider` intent

**Done when:** exported GPX opens correctly in QGIS and in Google Earth, and the CSV
imports cleanly into Excel with UTF-8 Amharic intact (BOM included).

---

## Phase 13 — Peer sync

Reuse the Nearby Connections approach from the mesh chat project.

- `P2P_CLUSTER` advertise/discover scoped to `org_id`
- Digest exchange, then delta request
- Payload signature verification
- Photos only over Wi-Fi Direct, session caps enforced
- Peer sync UI: nearby devices, transfer progress, what was exchanged

**Done when:** device A collects data offline, hands it to device B over peer sync, B
goes online, and the server has A's rows exactly once with `origin_device_id` preserved.

---

## Phase 14 — Supervisor dashboard (Next.js)

- Next.js 15, Supabase auth, MapLibre GL JS
- Team map: all tracks, filter by user, project, date
- Coverage view per assignment
- Submission review with photos
- CSV/GeoJSON export
- Deploy to Vercel

**Done when:** a supervisor account sees only their org's data (verify by logging in as a
different org), and the map renders 50 tracks without stuttering.

---

## Then: make it a portfolio piece

- Seed a realistic demo dataset with `ReplayLocationEngine`
- Record a 90-second walkthrough: aeroplane mode on, map works, track a walk, capture a
  waypoint, measure a plot, sync
- Write the case study: the OEM battery problem and how you solved it, the tile pipeline,
  the sync guarantees, with real numbers from your benchmarks
- Publish the tile pipeline (`tools/`) as a standalone open-source repo — it is useful on
  its own and it is a credible signal
- Get one real user. An agricultural extension office or a small NGO. Even unpaid, even
  for two weeks. "Deployed with a field team in Oromia" is worth more than every other
  line in this document.
