# ROADMAP.md

Prioritized backlog. The daily run takes the **top open item** first. Mark an item done by
moving it to "Done" with its PR number and squash commit SHA. Replan (see CLAUDE.md, daily loop
step 5) when fewer than 5 items are open.

**Priority:** P0 = data safety / privacy / broken build · P1 = next phase of the core
product · P2 = important, not blocking · P3 = cleanup.
**Effort:** S = under half a day · M = about a day · L = split before starting.

Phases refer to `docs/BUILD_PLAN.md`. Phases 1–4 are done (git history to `1fc66f5`).
Phase 0 (tile pack) tooling is done (`tools/`, item 12). A real Ethiopia pack has not been
built yet: Planetiler needs a machine with ~20 GB scratch space, not the cloud container.

Acceptance criteria that need a physical phone are not claimed by CI. They are listed in
`NEEDS_APPROVAL.md` under "Needs a device".

---

## Open

### 13. [P2 · L] Phase 6 — Offline map (split; 6a–6e, 6f-1, 6f-2a and 6f-2b-1 done)
Remaining parts, one PR each, in order:
6f-2b-2 live position while no track records. Today the puck only gets live fixes from the
recording's engine; when idle it shows the cached fix, grey. Needs a map-only request while
the map is in the foreground, never touching the recording's engine (a second engine from
`LocationEngineSelector`, or a "preview" mode), and **no batching**: the engine sets
`maxUpdateDelay` to 5× the interval, which would make the puck jump every 5–15 s. Stop it
when the map leaves the foreground or a recording starts. `DevicePositionRepository` is the
seam: the map already reads it;
6g SAF tile pack import into `filesDir/packs/{id}.mbtiles` (the layout
`PackDirectoryResolver` reads; validate with `MbtilesReader`, keep only `format=pbf`) and a
`tile_pack` row, which `MapViewModel` already observes (it now prefers the pack that covers
the user, so write correct bounds and `installedAt`).

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

### 19. [P2 · S] Cut the APK size MapLibre added
MapLibre's native libraries took the debug APK from 13.4 MB to 33.4 MB (#40), for 4 ABIs.
Field phones are low-end and data is paid by the megabyte. Measure the release build, then
ship per-ABI: an App Bundle for Play, and ABI splits (`armeabi-v7a`, `arm64-v8a`) for
sideloaded APKs. Drop `x86`/`x86_64` from release only. Record before and after in the log.

### Later phases (expand into items when they reach the top)
- Phase 7 — Tracking UI and track rendering
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
