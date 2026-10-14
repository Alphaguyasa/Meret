# ROADMAP.md

Prioritized backlog. The daily run takes the **top open item** first. Mark an item done by
moving it to "Done" with its PR number and release tag. Replan (see CLAUDE.md, daily loop
step 5) when fewer than 5 items are open.

**Priority:** P0 = data safety / privacy / broken build · P1 = next phase of the core
product · P2 = important, not blocking · P3 = cleanup.
**Effort:** S = under half a day · M = about a day · L = split before starting.

Phases refer to `docs/BUILD_PLAN.md`. Phases 1–4 are done (git history to `1fc66f5`).
Phase 0 (tile pack) is only partly done: `tools/build-tiles.sh` exists, the glyph, sprite,
style, and preview steps do not.

Acceptance criteria that need a physical phone are not claimed by CI. They are listed in
`NEEDS_APPROVAL.md` under "Needs a device".

---

## Open

### 3. [P1 · M] Phase 5a — Persist the active tracking session
`TrackingSessionStore` in `:core:datastore`: active track id, current segment, paused flag,
start time (wall clock plus elapsed-realtime). This is what lets a restarted service resume
the same track instead of starting a new one (CLAUDE.md invariant 7).
- Tests: write, read back, clear; a new store instance on the same file sees the session
  (process-death simulation); corrupt or missing values read as "no active session".

### 4. [P1 · M] Phase 5b — TrackRecorder: fixes to Room in batches
Consumes `LocationEngine.fixes` through a `Channel`, runs `FixPipeline` on
`Dispatchers.Default`, writes `TrackPointDao.appendBatch` at 20 points or 10 s, and flushes
immediately on pause and stop. Outliers are stored with `is_outlier = 1`, never dropped.
- Tests (Robolectric, in-memory Room, `ReplayLocationEngine`): every replayed fix is in
  Room after stop; outliers stored and flagged; the batch boundary never loses the tail;
  the raw columns equal the fixture exactly.

### 5. [P1 · M] Phase 5c — TrackingService (foreground, type location)
Service + manifest (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`,
`POST_NOTIFICATIONS`, `WAKE_LOCK`), `startForeground` on every path, `START_STICKY`
resuming from `TrackingSessionStore`, partial wakelock held only while recording, live
notification with Pause/Stop actions, keeps running in `onTaskRemoved`. Strings in `en` and
`am`.
- Tests (Robolectric): start → foreground with a notification; stop → wakelock released and
  session cleared; restart with a persisted session → resumes the same track id.

### 6. [P1 · S] Phase 5d — TrackingHealthMonitor
Pure logic: expected vs actual fixes per 10-minute window, and a `tracking_gap` event when
a gap exceeds 3× the sampling interval while recording (not while paused).
- Tests with synthetic timelines: no gap, one gap, gap during pause ignored.

### 7. [P1 · S] Phase 5e — DeviceCompat OEM guidance
`oemGuidanceFor(manufacturer)` for Tecno/Infinix/Itel, Xiaomi, Oppo, Vivo, Huawei,
Samsung, OnePlus, and a generic fallback. Deep-link launch wrapped in try/catch with a
`ManualSteps` fallback. Step text in `en` and `am`.
- Tests: manufacturer string matching (case, whitespace, sub-brands); a missing activity
  falls back to manual steps.

### 8. [P1 · M] Phase 5f — Permission onboarding flow
Staged: fine location → notifications (13+) → background location (with rationale screen)
→ battery optimisation → OEM autostart. Handles the coarse-only grant on 12+.
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` added to the manifest here.
- Tests: the state machine that picks the next step, as a pure unit test per API level.

### 9. [P1 · M] Phase 5g — Diagnostics screen
Everything in `LOCATION_ENGINE.md` §Diagnostics, plus "copy report". The report must hold
no personal data: no coordinates, no names, no form answers. Counts, states, and versions
only.
- Tests: report builder output for a fixture state, and a test asserting no lat/lon appears.

### 10. [P1 · S] Phase 5h — Boot receiver
If a track was active at shutdown, post a notification offering to resume. Never auto-start
the service on boot.
- Tests (Robolectric): boot with an active session → notification; without → nothing.

### 11. [P2 · S] Dependency updates and vulnerability alerts
Dependabot for `gradle` and `github-actions`, monthly, at most 2 open PRs (Actions minutes
are limited). Free on GitHub.

### 12. [P2 · M] Phase 0 remainder — glyphs, sprite, style, preview
`tools/build-glyphs.sh`, `tools/build-sprite.sh`, `tools/style/meret-style.json`,
`tools/preview/`, `tools/README.md` per `docs/SESSION_PROMPTS.md` session 0. Only free,
self-hostable sources (OSM extract, Noto fonts, Maki icons).
- Tests: a JVM test that parses the style JSON and checks the font stacks match the
  generated glyph directory names, and that glyphs/sprite use `asset://`, never `http`.

### 13. [P2 · L] Phase 6 — Offline map (split before starting)
6a MbtilesReader (TMS→XYZ flip, gzip passthrough) with a tiny fixture `.mbtiles`;
6b LocalTileServer on loopback; 6c network security config (cleartext to 127.0.0.1 only);
6d StyleProvider; 6e MapLibre Compose wrapper (check the real API in the artifact first);
6f map screen controls; 6g SAF tile pack import.

### 14. [P2 · M] Encrypt the Room database at rest
Blocked: needs a decision in NEEDS_APPROVAL.md (SQLCipher size and speed cost on
low-end phones, key in Android Keystore, migration of existing plaintext DBs).

### 15. [P3 · S] Remove Android Studio template leftovers
`proguard-rules.pro` comments, template comments in `res/xml`, unused launcher assets if any.

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

| Item | PR | Tag |
|---|---|---|
| Phase 0 setup: context files, CI rewrite, doc drift fixes | #1 | v2026-10-14-1 |
| 1. Keep personal data out of backups and device transfer | #2 | v2026-10-14-2 |
| 2. Guard against personal data in logs | #3 | v2026-10-14-3 |
