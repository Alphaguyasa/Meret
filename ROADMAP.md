# ROADMAP.md

Prioritized backlog. The daily run takes the **top open item** first. Mark an item done by
moving it to "Done" with its PR number and squash commit SHA. Replan (see CLAUDE.md, daily loop
step 5) when fewer than 5 items are open.

**Priority:** P0 = data safety / privacy / broken build · P1 = next phase of the core
product · P2 = important, not blocking · P3 = cleanup.
**Effort:** S = under half a day · M = about a day · L = split before starting.

Phases refer to `docs/BUILD_PLAN.md`. Phases 1–4 are done (git history to `1fc66f5`). Phases 5,
6, 7 and 8 are done in CI; their device checks are in `NEEDS_APPROVAL.md`.
Phase 0 (tile pack) tooling is done (`tools/`, item 12). A real Ethiopia pack has not been
built yet: Planetiler needs a machine with ~20 GB scratch space, not the cloud container.

Acceptance criteria that need a physical phone are not claimed by CI. They are listed in
`NEEDS_APPROVAL.md` under "Needs a device".

---

## Open

### 37. [P1 · S] Area units: m², hectare and timad (Phase 10a)
A pure `:core:model`/`:core:common` formatter for an area in m²: m² below 1,000, hectares to
2 decimals above, and *timad* with a configurable hectares-per-timad (default 0.25, Phase 10
of `docs/BUILD_PLAN.md`), plus perimeter in m / km. Locale-aware digits, en and am unit
labels in `strings.xml`. Unit tests with known fixtures (1 ha = 4 timad at 0.25; 0.3 ha at a
0.5 ratio = 0.6 timad), including 0 and an invalid ratio (≤ 0 falls back to 0.25).

### 38. [P1 · M] Measurement repository and save use case (Phase 10b)
`MeasurementRepository` in `:core:data` (Room only) and `SaveMeasurementUseCase` in
`:domain`: a ring of vertices (tapped or walked) is validated (at least 3 distinct vertices,
non-zero area), its area (`GeoMath.geodesicPolygonArea`) and perimeter computed, its
vertices stored as GeoJSON, and the row written `pending` with a UUIDv7 and `project_id`
`local`, as tracks and waypoints are. A name is normalised like a track's. Tests: a
reference plot from a GIS computation matches within 0.1% (the Phase 10 acceptance for the
tapped mode), a two-point ring is refused, the GeoJSON round-trips. No schema change
expected (`measurement` exists since Phase 3); if one is needed, a migration and its test.

### 39. [P1 · M] Tap-the-vertices measurement on the map (Phase 10c)
`:feature:measure`: from the map, «Measure» opens a mode where each tap adds a vertex at the
crosshair; the ring is drawn (MapLibre line + fill; check the API against the sources jar),
with live area (item 37's units) and perimeter, «Undo last point», «Clear» and «Save» with a
name. The ring in progress survives process death (Room or DataStore, not the ViewModel).
en/am strings, TalkBack labels. ViewModel tests with a fake repository.

### 40. [P1 · M] Walk-the-perimeter measurement (Phase 10d)
The same screen in walking mode: vertices are taken from filtered fixes while the worker
walks the boundary (a new vertex every few metres of movement, fixes worse than the accuracy
gate skipped, raw fixes never changed). Uses the tracking service so it survives the screen
going off and process death. Tests replay a GPX fixture of a walked rectangle through
`ReplayLocationEngine`: the area is within 3% of the fixture's known area (the Phase 10
acceptance, on the desk; the field check goes in "Needs a device").

### 41. [P2 · S] Saved measurements: list and map overlay (Phase 10e)
A list of saved measurements (name, area in the chosen unit, date), rename and delete
(marked `discarded`, like tracks), and saved plots drawn on the map as outlines. Export is
Phase 12.

### 42. [P2 · S] Timad size setting (Phase 10f)
Settings: hectares per timad (default 0.25; the size differs by region), stored in DataStore
and used everywhere item 37's formatter is. Validated input, en/am.

### 26. [P2 · S] Purge discarded tracks (waiting on A13)
Since #59 a deleted track is marked `discarded` and keeps all its points. Nothing purges
them, so a phone that records and deletes a lot keeps the storage. The policy (when, and
whether only after the server has the discard) is Alpha's call, A13. Phase 9 sync must also
send the discard (the row is re-queued `pending` by `TrackDao.discardTrack`).

### 25. [P2 · S] Release signing (blocked on A12)
There is no `signingConfig`, so `assembleRelease` makes unsigned APKs that no phone installs.
Needs Alpha's call on who holds the upload key (A12). Then: the key in GitHub Actions
secrets, a signed release built by a manual workflow, never a keystore in the repo.

### 14. [P2 · M] Encrypt the Room database at rest
Blocked: needs a decision in NEEDS_APPROVAL.md (SQLCipher size and speed cost on
low-end phones, key in Android Keystore, migration of existing plaintext DBs).

### Later phases (expand into items when they reach the top)
- Phase 9 — Sync (Supabase schema + RLS tests first, free tier only). Waypoints (since #69)
  are written `pending` with no outbox row, like tracks: sync builds the outbox payload from
  pending rows once the wire format exists, and must also move them off `project_id` `local`. Tracks recorded before
  sign-in have `project_id` and `user_id` `"local"` (`RoomNewTrackStore`); sync must give them
  the signed-in user and a real project before upload. `device_id` is a random per-install id
  (`DeviceIdentityStore`), never a hardware id.
- Phase 10 — Measurement: split into items 37–42 above (run 27).
- Phase 11 — Assignments and coverage
- Phase 12 — Export
- Phase 13 — Peer sync
- Phase 14 — Supervisor dashboard (separate repo)

---

## Done

| Item | PR | Tag / squash commit |
|---|---|---|
| Phase 0 setup: context files, CI rewrite, doc drift fixes | #1 | v2026-10-14-1 |
| 1. Keep personal data out of backups and device transfer | #2 | v2026-10-14-2 |
| 2. Guard against personal data in logs | #3 | v2026-10-14-3 |
| 3. Persist the active tracking session (`TrackingSessionStore`) | #7 | `98a3728` |
| 4. TrackRecorder: fixes to Room in batches | #8 | `14d760f` |
| 5a. TrackingController: one owner for a recording | #12 | `cd17fe0` |
| Fix: double-encoded Amharic strings in 3 feature modules (found during item 5) | #13 | `13c9546` |
| 6. TrackingHealthMonitor: fix-gap detection | #15 | `9db0ff8` |
| 5b. TrackingService, foreground type location | #14 | `19bf20a` |
| 7. DeviceCompat OEM guidance | #17 | `d784d20` |
| 8a. OnboardingStepPicker: permission flow order per API level | #18 | `704dbe8` |
| 8b-1. Onboarding state: skipped steps, permission snapshot, ViewModel | #20 | `1574771` |
| 8b-2. Permission onboarding screens, en/am, app gate | #21 | `026a710` |
| 9a. Tracking health in the controller, diagnostics report builder | #23 | `b9955cd` |
| 9b. Diagnostics screen, copy report, en/am | #24 | `987cc6e` |
| 10. Boot receiver: offer to resume an open track | #25 | `f18ca7e` |
| 11. Dependabot for Gradle and GitHub Actions | #27 | `30bc255` |
| 12. Phase 0 remainder: style, glyphs, sprite, preview | #28 | `a5fe889` |
| 13 · 6a. `MbtilesReader` (TMS flip, gzip passthrough, metadata) | #31 | `2cbba96` |
| 13 · 6b. `LocalTileServer` (loopback HTTP, TileJSON; not NanoHTTPD) | #32 | `c3f49c1` |
| 13 · 6c. Network security config (cleartext to 127.0.0.1 only); flaky `LocalTileServerTest` fixed | #34 | `622964f` |
| 13 · 6d-1. `StyleProvider` and the bundled style template | #35 | `54b46a5` |
| 13 · 6d-2. Glyph stack (Noto Sans + Ethiopic, 256 ranges) and sprite in `:core:maps` assets | #37 | `436ba95` |
| 13 · 6e-1. `OfflineMapSession` (ref-counted server + style), `PackDirectoryResolver` | #38 | `b4e2e46` |
| 13 · 6e-2. MapLibre 12.3.1 `MapView` wrapper, lifecycle, ambient cache off | #40 | `c0829be` |
| 13 · 6f-1. Map screen draws the installed pack; flaky `OnboardingViewModelTest` fixed, test and CI timeouts | #41 | `2efa101` |
| 17. Closed tracking gaps go to `Telemetry` (duration only, no-op by default) | #42 | `1b2537a` |
| Fix: main CI hung in `LocalTileServerTest` (test clients had no timeouts) | #44 | `5d0f73d` |
| 13 · 6f-2a. Map zoom buttons, compass, scale bar (`OfflineMapController`, `ScaleBars`) | #45 | `026dddf` |
| 13 · 6f-2b-1. Location puck (own GeoJSON layers), follow mode, recentre, pack that covers the user | #47 | `d59bceb` |
| 16. "Check permissions again" on the diagnostics screen | #48 | `d889766` |
| 13 · 6f-2b-2. Live puck while idle: map-only `PreviewLocationSource`, unbatched, foreground only | #50 | `7eabc40` |
| 13 · 6g-1. Pack import: `MbtilesPackInstaller` (copy, check pbf, rename) and `TilePackRepository.importPack` | #51 | `a914448` |
| 13 · 6g-2. Packs screen: picker, progress, en/am errors, pack list with delete, orphan sweep. **Phase 6 done in CI** (device rows in NEEDS_APPROVAL) | #53 | `b2d474a` |
| 19. Per-ABI release APKs (`armeabi-v7a`, `arm64-v8a`, universal) and the `release-size` workflow | #54 | `60fa667` |
| 20. Phase 7a: record button starts, pauses, stops (`StartTrackingUseCase`, `RecordingCommands`, `DeviceIdentityStore`), tracking bar stats | #56 | `fa90463` |
| 21. Phase 7b: live track line (`TrackingController.activeLine`, `throttleLatest` 2 s, Douglas–Peucker 3 m, own line layer under the puck) | #57 | `ff9a1d4` |
| 22. Phase 7c: track list (newest first, distance, moving time, recording / not finished / sync state), delete as a soft discard with a 10 s undo window | #59 | `1aad0d8` |
| 23a. Phase 7d-1: track detail (totals, elevation profile, segment list; `TrackSummariser`), list rows open it | #60 | `6db9994` |
| 23b. Phase 7d-2: undo a long-press stop (6 s snackbar, `TrackingController.reopen`, new segment), tracking bar segment count | #62 | `24d9969` |
| 24. Every DataStore test on a scope that ends with the test (`backgroundScope`, or cancelled in `@After`) | #63 | `74a918f` |
| 27. Phase 7e: earlier tracks on the map (completed tracks in view, 30 newest, cached lines, loaded for 2× the screen on camera rest). **Phase 7 done in CI** (device rows in NEEDS_APPROVAL) | #65 | `de1952a` |
| 15. Template leftovers: placeholder launcher icon (A14) instead of the robot, unused density webps removed, `gradle.properties` header | #66 | `8f222f3` |
| 28. Phase 8a: save a waypoint from the map (sheet with live position, title, category, notes; Room first, live fix only, `track_id` while recording) | #69 | `ce57e91` |
| 29a. Phase 8b-1: saved waypoints on the map, clustered (MapLibre cluster source, count labels, marker read capped at 5,000) | #70 | `b48a5c9` |
| 29b. Phase 8b-2: waypoint list (newest first, title or category or time, sync state); a tap centres the map with follow off (`OfflineMapController.centreOn`) | #72 | `10ba868` |
| 30. Phase 8c-1: form schema in `:core:model` (parse, 7 field types plus unsupported, validate, `visible_if` with cascading hiding, `form_data` JSON, bundled 12-field farm visit; `docs/FORM_SCHEMA.md`) | #73 | `1fbf492` |
| 31. Phase 8c-2: form renderer on the waypoint sheet (one control per field type, visible fields only, en/am chrome and one message per `FieldError`, errors from the first refused Save, `AppError.Invalid`, bundled farm visit via `WaypointFormSource`, answers stored as `answersToKeep`) | #75 | `443bd92` |
| 32a. CI prints new Room schema JSON and fails on a changed committed one (unblocks the first migration, 32b) | #76 | `9c46373` |
| 32b. Phase 8d: waypoint sheet draft autosave to Room (database version 2, `waypoint_draft`, `MIGRATION_1_2`; written on every change, restored from Room alone after a kill; `SavedStateHandle` dropped) | #78 | `0400f02` |
| 33a. Phase 8e-1: photo pipeline (`PhotoStore`/`JpegPhotoStore`: 1600 px q80, GPS/time/orientation EXIF kept, make/model dropped, SHA-256, `files/photos/`, atomic rename; `PhotoSizing` in `:core:common`; no new dependency) | #79 | `d5698bd` |
| 33b-1. Phase 8e-2: photo rows at Save (`PhotoStore.describe/delete`, `waypoint_photo` rows in one transaction with the waypoint, a missing file dropped from the answer, hidden fields' photo files deleted after the save, UUID-only ids) | #81 | `1a97018` |
| 33b-2. Phase 8e-2: photos on the sheet through the phone's camera app (`TakePicture` + non-exported `FileProvider`, no CAMERA permission, no new dependency; thumbnails, remove with confirmation, capture kept across a process death). **Phase 8 done in CI** (device rows in NEEDS_APPROVAL) | #82 | `107e997` |
| 34. «Show on map» from track detail (map fits the stored bounds, follow off; the opened track pinned past the 30-track cap of the earlier-tracks layer) | #84 | `fc84a07` |
| 35. Name a track from its detail screen (rename dialog en/am; `TrackNames.normalise`; only `name`, `sync_state` pending and `updated_at` change) | #85 | `6fd0dc3` |
| 36. Sweep orphaned photo files when the waypoint sheet opens (`PhotoSweeper`/`FilePhotoSweeper`: `<uuid>.jpg` a day old that no row, draft or saved answer names; `.jpg.part` and `photo_captures/` a day old; nothing deleted if Room cannot be read; `WaypointPhotoFiles` bundles store and sweeper) | #87 | `fc42fe3` |
| 18. Dependabot: monthly grouped npm updates for `tools/` (limits are per ecosystem, so up to 3 open) | #88 | `7d7bf0f` |
