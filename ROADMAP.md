# ROADMAP.md

Prioritized backlog. The daily run takes the **top open item** first. Mark an item done by
moving it to "Done" with its PR number and squash commit SHA. Replan (see CLAUDE.md, daily loop
step 5) when fewer than 5 items are open.

**Priority:** P0 = data safety / privacy / broken build · P1 = next phase of the core
product · P2 = important, not blocking · P3 = cleanup.
**Effort:** S = under half a day · M = about a day · L = split before starting.

Phases refer to `docs/BUILD_PLAN.md`. Phases 1–4 are done (git history to `1fc66f5`). Phases 5
and 6 are done in CI; their device checks are in `NEEDS_APPROVAL.md`.
Phase 0 (tile pack) tooling is done (`tools/`, item 12). A real Ethiopia pack has not been
built yet: Planetiler needs a machine with ~20 GB scratch space, not the cloud container.

Acceptance criteria that need a physical phone are not claimed by CI. They are listed in
`NEEDS_APPROVAL.md` under "Needs a device".

---

## Open

### 20. [P1 · M] Phase 7a — Record button starts, pauses and stops a track
The map's `RecordButton` and `TrackingBar` are drawn but `MapRoute` passes `{}`. Tap with no
recording: a `StartTrackingUseCase` writes a new track row (UUIDv7, the current project id or
a local default; check `TrackEntity.projectId`), then `TrackingController.start` and the
foreground service, from this user action only (Android 14 rule). Tap while recording:
pause/resume. Long-press: stop. `TrackingBar` shows the live distance, duration and point
count from the controller. en/am strings. ViewModel tests with a fake controller; the service
start stays on a device row.

### 21. [P1 · M] Phase 7b — Live track line on the map
The recording track as its own `GeoJsonSource` and line layer, refreshed every 2 s from
`TrackPointDao.observeTrackGeometry` (simplified on `Dispatchers.Default`, raw points
untouched), re-added after every style load like the puck. Tested on the geometry and the
refresh cadence; the frame-rate criterion (2-hour track, no drops) is a device row.

### 22. [P1 · M] Phase 7c — Track list
Replace the `TrackListScreen` placeholder: newest first, name/date, distance, duration, sync
state. Delete with an undo window (the row stays until the snackbar times out, and a process
death inside the window keeps the track). en/am.

### 23. [P1 · M] Phase 7d — Track detail and stop undo
Stats, segment list and elevation profile for one track; "stopped by accident" undo after a
long-press stop (resume the same track in a new segment). Then Phase 7's acceptance goes to
the device list.

### 24. [P3 · S] Give the remaining DataStore tests a test scope
Run 12 found a 45-minute CI hang from a DataStore on its default `Dispatchers.IO` scope in a
`runTest`. `OnboardingViewModelTest` was fixed. `TrackingSessionStoreTest`,
`OnboardingStoreTest`, `TrackingConfigStoreTest` and `DefaultTrackingControllerTest` still
build some stores with no scope. Pass `backgroundScope` everywhere and drop the fallback.

### 25. [P2 · S] Release signing (blocked on A12)
There is no `signingConfig`, so `assembleRelease` makes unsigned APKs that no phone installs.
Needs Alpha's call on who holds the upload key (A12). Then: the key in GitHub Actions
secrets, a signed release built by a manual workflow, never a keystore in the repo.

### 14. [P2 · M] Encrypt the Room database at rest
Blocked: needs a decision in NEEDS_APPROVAL.md (SQLCipher size and speed cost on
low-end phones, key in Android Keystore, migration of existing plaintext DBs).

### 15. [P3 · S] Remove Android Studio template leftovers
`proguard-rules.pro` comments, template comments in `res/xml`, unused launcher assets if any.

### 18. [P3 · S] Cover `tools/package-lock.json` with Dependabot
`tools/` now has npm dependencies (fontnik, sharp, Maki). Dependabot alerts (A10) will flag
advisories in the lockfile, but nothing proposes routine updates. Add an `npm` entry for
`/tools`, monthly, sharing the 2-open-PR budget (lower the Gradle limit or group). sharp was
pinned to 0.35.5 in run 7 because 0.34.4 had a high-severity libvips advisory.

### Later phases (expand into items when they reach the top)
- Phase 8 — Waypoints, forms, photos
- Phase 9 — Sync (Supabase schema + RLS tests first, free tier only)
- Phase 10 — Measurement
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
| 19. Per-ABI release APKs (`armeabi-v7a`, `arm64-v8a`, universal) and the `release-size` workflow | #54 | `SHA54` |
