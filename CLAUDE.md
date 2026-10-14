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

Two modules exist beyond this list, because `ARCHITECTURE.md`'s layering needs them:
`:domain` (use cases, pure Kotlin) and `:core:data` (repositories — the only module
allowed to see both `:core:database` and `:core:network`).

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
- `minSdk 26`, `targetSdk 36`, `compileSdk 37`. *(Was written as 35/35. The toolchain
  this project was generated with — AGP 9.3.1, Compose BOM 2026.02 — pulls
  `lifecycle 2.11.0`, which refuses to compile below API 37. The levels live in
  `BuildConfig` in `build-logic/.../KotlinAndroid.kt`; change them there only.)*

**AGP 9 differences that will bite you**
- `CommonExtension` is no longer generic, and `compileOptions`/`lint`/`testOptions`/
  `packaging` lost their lambda overloads. Use the property getters.
- Library modules no longer declare `targetSdk`.
- AGP applies Kotlin itself and registers the `kotlin` extension. Applying
  `org.jetbrains.kotlin.android` on top is a hard error. KSP still registers Kotlin
  source sets, hence `android.disallowKotlinSourceSets=false` in `gradle.properties`.
- Hilt below 2.58 looks for the `BaseExtension` AGP 9 removed.

**Kotlin**
- Explicit API mode on for `:core:*` modules.
- `ktlint` + `detekt` in CI. Fix warnings, do not suppress them.
- Sealed interfaces for UI state. One `UiState` per screen, no `isLoading` +
  `errorMessage` + `data` triples.
- Repositories expose `Flow<T>` for reads and `suspend fun` returning
  `AppResult<T>` for writes. *(Not `Result<T>`: that name collides with `kotlin.Result`,
  which is in the default imports, and `runCatching {}` silently yields the wrong type.
  Do not "fix" the code back.)*

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

**Room `@Insert` must not return row ids.** Declaring a bulk insert as returning
`Long`/`List<Long>` makes Room run `SELECT changes()` and `SELECT last_insert_rowid()` per
row: measured 800 µs/row vs 65 µs/row on the reference device. Return `Unit`; if you need
a count, take a `SELECT total_changes()` delta inside the transaction
(`TrackPointDao.appendBatch` is the worked example).

**The Kalman filter is a random walk, not constant-velocity.** Deliberately. See the KDoc
on `KalmanLocationFilter` before touching it.

**Device clocks are wrong.** Never trust `System.currentTimeMillis()` for ordering.
Store both `elapsedRealtimeNanos` and wall-clock time on every fix, and use
`location.elapsedRealtimeNanos` for intra-track ordering.


---

## Commands

```bash
# One-time: copy local.properties.example to local.properties (Android Studio adds sdk.dir).
./gradlew assembleDebug                      # build the debug APK
./gradlew ktlintCheck detekt                 # style + static analysis
./gradlew ktlintFormat                       # auto-fix style
./gradlew testDebugUnitTest :core:model:test :core:common:test :domain:test   # unit tests
./gradlew lint                               # Android Lint, all modules (MissingTranslation is an error)
./gradlew koverVerify                        # 85% line coverage on :core:model, :core:common
./gradlew assembleDebugAndroidTest           # compile instrumented tests (need a device to run)
./gradlew :core:database:connectedDebugAndroidTest   # migration + benchmark tests, device only
```

CI (`.github/workflows/ci.yml`) runs all of the above except the connected tests, in one
Gradle invocation, and uploads the debug APK as the `meret-debug-apk` artifact. A PR that
changes only `*.md` files runs no CI.

There is no dashboard in this repo. The Next.js supervisor dashboard (BUILD_PLAN phase 14)
is planned as a separate repo, `meret-dashboard`. There is no `supabase/` directory yet
(phase 9).

---

## Workflow expectations

This project is developed by an autonomous agent with no human at the keyboard. The owner
(Alpha) reads reports on a phone and answers questions in `NEEDS_APPROVAL.md`. The
"wait for confirmation" rules that applied to interactive sessions are replaced by the
policies below. Where a decision would previously have waited for Alpha, do the safe
version and log the question in `NEEDS_APPROVAL.md`.

- Work from `ROADMAP.md`, top item first. Phases from `docs/BUILD_PLAN.md` are split into
  small, CI-verifiable roadmap items; a phase is done only when its acceptance criteria
  pass, and criteria that need a physical device are listed in `NEEDS_APPROVAL.md` under
  "Needs a device" rather than claimed.
- Before writing code for an item, write a short plan into the PR description: acceptance
  criteria, files to change, tests to add.
- Run the CI command locally where a JDK/Android SDK exist. Report actual output, never an
  assumption that it passed.
- Room schema changes require a migration and a migration test. Never use
  `fallbackToDestructiveMigration()`.
- If a requirement in these docs is wrong or has become wrong, say so in the PR and the
  log, and fix the doc in the same PR. Do not silently work around it.
- Never commit secrets. Supabase URL and anon key go in `local.properties` and are read
  through `BuildConfig`. `local.properties` is gitignored (verified 2026-10-14).
- Memory between runs lives only in this repo: `CLAUDE.md`, `ROADMAP.md`,
  `DAILY_LOG.md`, `NEEDS_APPROVAL.md`. Anything the next run must know goes in one of them.

### Context files

| File | What it is |
|---|---|
| `ROADMAP.md` | Prioritized backlog. Top open item is next. Each item has priority, effort, and acceptance. |
| `DAILY_LOG.md` | One entry per run, newest first, in the REPORT format below. |
| `NEEDS_APPROVAL.md` | Decisions, money, device testing, and anything destructive waiting on Alpha. |

### Actions minutes budget

The repo is private on the GitHub Free plan: 2,000 Actions minutes a month and no branch
protection. A full CI run takes about 10–17 minutes. Do not re-run CI without a code change,
do not push to a PR branch commit by commit (push once when the work is ready), and keep
log-only changes in Markdown so they skip CI. If `gh` reports that Actions minutes are
exhausted, stop opening PRs for the rest of the run and say so in the report.

---

## Autonomous operation policies

These come from Alpha and are copied here in full. They override anything above that
conflicts with them.

### The daily loop (every run)

1. Read the context files. Check CI on main. If main is broken, fixing it is the first task.
2. MODEL CHECK (see MODEL POLICY).
3. Pick the top ROADMAP item. Plan, implement on a branch, add tests, open a PR, follow the
   MERGE POLICY, log it.
4. Repeat for the next item until the PER-RUN LIMIT.
5. REPLAN: when ROADMAP.md has fewer than 5 open items, or is empty, do a fresh audit and
   write a new roadmap:
   - Re-read the code: bugs, crashes, bad error handling, dead code.
   - Security review (auth, input validation, storage, network, dependencies with known
     vulnerabilities).
   - Performance, UX, accessibility, and localization gaps.
   - Test coverage gaps and tech debt.
   - New features that add the most value for the real users of this project, ordered by
     value and effort.
   Write the new items into ROADMAP.md with priority and effort, then continue. Never end a
   run with "nothing left to do".
6. Update DAILY_LOG.md, then send the REPORT.

### Per-run limit

Up to 2 roadmap items per run, or stop earlier if blocked, out of time, or CI keeps
failing. Small, safe, tested changes. (Alpha may raise this later.)

### Project rules (never break; add a test for each)

- Collected records are sacred. They are saved to Room first, before any network call, and
  are never lost to a crash, failed sync, or app update.
- Sync is idempotent: client-generated UUIDs, safe retries, documented conflict rules, no
  duplicates.
- Every Room schema change needs a versioned migration plus a test. Never use
  fallbackToDestructiveMigration.
- Records can identify people and describe their health or farms. No personal data in
  logs, crash reports, or analytics. No third-party analytics or ad SDKs. Request the
  minimum permissions. Encrypt sensitive data at rest where feasible.
- Review Supabase row-level-security policies and PostGIS queries for data leaks whenever
  you touch them.
- Performance budget: must stay usable on low-RAM, old Android devices. Measure startup
  time, memory, APK size, and battery impact before and after risky changes.
- Keep every localized string complete (check Amharic and any other languages already in
  the repo).

### Merge policy

- Never push directly to main. Always branch, PR, then merge.
- Wait for every CI check to pass.
- All checks pass: squash-merge the PR yourself, then tag the release
  (v<YYYY-MM-DD>-<n>) so Alpha can roll back.
- A check fails: fix it on the same branch (max 2 attempts). Still failing: do NOT merge.
  Leave the PR open and report why.
- If branch protection blocks a merge, do not bypass it. Report it.
- Never force-push, rewrite history, or bypass failed checks.
- Split risky or large changes into smaller PRs.

*Repo-specific notes:* branches are named `claude/<short-slug>`. A PR that touches only
Markdown runs no CI; "no checks reported" on such a PR counts as passing. The tag goes on
the squash commit on main: `git tag v2026-10-14-1 <sha> && git push origin <tag>`, with
`<n>` counting up per day.

### Deploy and database policy

- Merging to main may auto-deploy. Treat every merge as a release.
- Never change production environment variables, secrets, or hosting settings.
- Database changes need a versioned migration file plus a test. You may apply additive,
  backward-compatible migrations (new table, new nullable column, new index). Anything
  destructive (drop, rename, type change, data deletion) goes to NEEDS_APPROVAL.md and
  waits for Alpha.
- Never delete or overwrite user data.

### Model policy

- Always use the newest Opus-class model, or any higher tier, that is available to you.
  Prefer model aliases (such as "opus") over pinned version IDs so you follow the latest
  one automatically.
- At the start of every run, check Anthropic's official models documentation for a newer
  Opus or higher model. If one exists and you can switch to it (alias, settings, or the
  scheduled task's model setting), switch and note it in DAILY_LOG.md.
- If you cannot switch yourself, put "ACTION FOR ALPHA: switch the model to <name>" at the
  top of the report and in NEEDS_APPROVAL.md.
- Switching models must never involve buying, upgrading, or changing a plan.

*Alpha's decision, 2026-10-14 — overrides the "or any higher tier" clause above:* use the
**Opus line only**, currently Claude Opus 5.5 (`claude-opus-5-5`). Do **not** switch to
Fable or any tier above Opus: it uses Alpha's plan usage faster and Alpha has not budgeted
for it. The model check still runs every day, but it only looks for a **newer Opus**
(e.g. Opus 5.6 or Opus 6). If one exists, switch the routine to it and log it. If you cannot
switch, put "ACTION FOR ALPHA" in the report. Models page:
<https://platform.claude.com/docs/en/models/overview>.

### No purchases policy

- Never buy, subscribe to, or upgrade anything. Never enter payment details.
- Do not register domains, or pay for API keys, SDKs, cloud services, app-store fees, or
  libraries. Use free and open-source tools only.
- Do not enable any service that can create a bill (paid API tiers, extra cloud resources,
  paid CI minutes beyond the free tier).
- If a feature would need money or a paid account, do not do it. Write it in
  NEEDS_APPROVAL.md with the cost and the alternatives, and move on.
- Stay inside the free tiers of Supabase and any hosting. Do not add map tile providers or
  services that bill by usage; use free or self-hostable sources.

### Safety rules

- No secrets, API keys, or keystores in the repo. Use the platform's secret storage.
- Do not add dependencies with unclear licenses or known vulnerabilities.
- Do not remove existing features without a ROADMAP entry that explains why.
- If a build or test fails and you cannot fix it, say so plainly. Never hide failures or
  fake results.
- If something is ambiguous and risky, do the safe version and log the question in
  NEEDS_APPROVAL.md instead of guessing.

### Report (after every run, as the notification and in DAILY_LOG.md)

| Field | Content |
|---|---|
| Done | Items completed, with PR links |
| Merged | Yes/No per PR, and release tag |
| Failed or blocked | What and why |
| Roadmap | Items left, and whether a replan happened |
| Model | Model used; any "ACTION FOR ALPHA" |
| Needs approval | Anything in NEEDS_APPROVAL.md |
| Next | What the next run will do |
