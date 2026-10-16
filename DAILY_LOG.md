# DAILY_LOG.md

One entry per run, newest first, in the REPORT format from CLAUDE.md.

---

## 2026-10-16 — Run 3 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | Item 5, split into two PRs: `TrackingController` keeps the engine, recorder, persisted session and track status in step ([#12](https://github.com/Alphaguyasa/Meret/pull/12)). `TrackingService` adds the foreground service (type location), sticky restore, wakelock, live notification and en/am strings ([#14](https://github.com/Alphaguyasa/Meret/pull/14)). Item 6: `TrackingHealthMonitor` detects fix gaps ([#15](https://github.com/Alphaguyasa/Meret/pull/15)). **Bug found and fixed:** Amharic strings in `feature/tracking`, `feature/waypoint` and `feature/tiles` were mojibake (garbled text). They are restored, and a new test stops it from happening again ([#13](https://github.com/Alphaguyasa/Meret/pull/13)). |
| Merged | #12 yes, `cd17fe0` · #13 yes, `13c9546` · #15 yes, `9db0ff8` · #14 yes, `19bf20a` (first CI attempt failed lint `ForegroundServicePermission`; fixed on the branch, second attempt passed). |
| Failed or blocked | Nothing open. There is still no Android SDK in the container (`dl.google.com` is blocked). |
| Roadmap | 9 open items (7–15). No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). Checked the models page: no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7 unchanged. **A9 new:** a native speaker should review the new Amharic tracking strings (no cost). |
| Next | Item 7 (DeviceCompat OEM guidance) and item 8 (permission onboarding flow). |

Notes for the next run:
- **Tooling that works in the cloud container:** the ktlint binary from
  `github.com/pinterest/ktlint/releases/download/1.8.0/ktlint`, the detekt CLI jar from
  `github.com/detekt/detekt/releases/download/v1.23.8/detekt-cli-1.23.8-all.jar`, and
  `kotlinc` from `github.com/JetBrains/kotlin/releases/download/v2.2.10/kotlin-compiler-2.2.10.zip`.
  With these you can compile pure-Kotlin files (`:core:model`, and `:core:common` except
  `DispatcherProvider`, which needs coroutines) and run a scratch harness. Maven Central
  returned 404/429, so the JUnit and Truth jars could not be downloaded.
- **Module lint reads only the module's own manifest.** A service with type `location`
  needs FINE/COARSE declared in that module's manifest too (cost one CI cycle on #14).
- Recording has one entry point: `TrackingService.start(context, trackId)`, called from a
  user action. The track row must already exist. Nothing creates one yet; the record
  button (Phase 7) will. The service gets its controller from `TrackingServiceHost`
  (implemented by `MeretApplication`), not from Hilt injection, so Robolectric tests use
  a fake controller.
- `TrackingHealthMonitor` is not wired to anything yet. That is now part of item 9.
- Debug APK size was not captured this run (the step summary is not in the job log tail).
  The uploaded artifact zip after #14 is 12,953 KiB.

## 2026-10-15 — Run 2 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | Item 3: `TrackingSessionStore` persists the active session; the shared DataStore now has a corruption handler ([#7](https://github.com/Alphaguyasa/Meret/pull/7)). Item 4: `TrackRecorder` batches fixes into Room (20 points / 10 s, flush on pause and stop), outliers stored, and resuming a track continues its `seq` ([#8](https://github.com/Alphaguyasa/Meret/pull/8)). |
| Merged | #7 yes, squash `98a3728` · #8 yes, squash `14d760f`. Both passed CI on the first attempt. **No release tags**: tag push is blocked (403), see A8. |
| Failed or blocked | Tag push (A8). No Android SDK in the cloud container (Google Maven blocked by the network policy), so nothing was built locally; CI is the build. |
| Roadmap | 11 open items (5–15). No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). Checked the models page: no newer Opus. No action. |
| Needs approval | A2 Actions spending limit · A3 no branch protection · A5 D2D transfer · A6 SQLCipher · A7 GitHub app access (push and PR work; tags do not, see A8) · **A8 new: release tags** |
| Next | Item 5 (TrackingService) and item 6 (TrackingHealthMonitor). |

Notes for the next run:
- **Commit author rule (Alpha, after the report):** commits must show Alphaguyasa only.
  Set the git identity and drop co-author lines; see CLAUDE.md, Workflow expectations.
  The 4 commits already on main keep their "Co-authored-by: Claude" line, because
  removing it would mean rewriting main, which the merge policy forbids.
- **A8 resolved after the report:** Alpha dropped release tags. Record the squash SHA per
  merge instead (CLAUDE.md merge policy). Do not try to push tags.
- Cloud runs have JDK 21 but no Android SDK and no access to `dl.google.com`, so Gradle
  cannot configure the build. Maven Central works. Standalone checks that match CI:
  `ktlint-cli-1.8.0-all.jar` (clean on main) and `detekt-cli-1.23.8-all.jar` with
  `--build-upon-default-config --config config/detekt/detekt.yml`. detekt's default
  `ReturnCount` (max 2) and `LongParameterList` (constructor max 7) both apply.
- CI took about 3 minutes per PR. Debug APK after #8: **13,170 KiB**, up 446 KiB from the
  12,724 KiB baseline in run 1. Not yet attributed to a PR; check the per-PR CI summaries
  next run.
- `TrackRecorder` lives in `:core:data`, which now depends on `:core:location`. Its tests
  read GPX fixtures from `../location/src/test/resources/traces` (the module directory is
  the test working directory) and use `runBlocking` on one real thread, not `runTest`.
- `DispatcherProvider` and `UuidV7Generator` have no Hilt bindings yet; item 5 must add them.

## 2026-10-14 — Run 1 (interactive setup, on Alpha's PC)

| Field | Content |
|---|---|
| Done | Setup: CLAUDE.md policies, ROADMAP/DAILY_LOG/NEEDS_APPROVAL, CI rewrite, doc drift fixes ([#1](https://github.com/Alphaguyasa/Meret/pull/1)). Item 1: records excluded from backup and device transfer ([#2](https://github.com/Alphaguyasa/Meret/pull/2)). Item 2: loggers and stdout forbidden in production code ([#3](https://github.com/Alphaguyasa/Meret/pull/3)). Daily routine created. |
| Merged | #1 yes `v2026-10-14-1` · #2 yes `v2026-10-14-2` · #3 yes `v2026-10-14-3` (first CI attempt failed on ktlint formatting; fixed on the branch, second attempt passed) |
| Failed or blocked | None open. Local builds on Alpha's PC are unreliable (corrupted Gradle cache, then a test run killed for low memory); CI is the source of truth. |
| Roadmap | 13 open items. No replan needed. |
| Model | This session: Opus 5.5. Routine first set to Fable 5.1 without asking; Alpha said no budget, switched to Opus 5.5 the same day. |
| Needs approval | A1 set routine to "Automatically approve" · A2 confirm Actions spending limit $0 / private vs public · A3 no branch protection · A5 D2D transfer off (confirm) · A6 SQLCipher go-ahead · A7 Claude GitHub app write access |
| Next | Item 3 (persist the active tracking session) and item 4 (TrackRecorder batching). |

Notes for the next run:
- CI on PRs takes 2–8 minutes now (was 17). Debug APK baseline: **12,724 KiB** (PR #1 run).
- Routine: <https://claude.ai/code/routines/trig_019EFpcswGcoGFoKrWFfgczs>, daily 04:30 UTC
  (07:30 Nairobi). The create API attaches every account connector by default; they were
  cleared. Do not re-add connectors without a roadmap reason.
- detekt `ForbiddenMethodCall` needs type resolution and is a no-op under the plain
  `detekt` task; use `ForbiddenImport`, or a source-scan test as in
  `NoLoggingInMainSourcesTest`.
- `config/detekt/detekt.yml` had no trailing newline once; appending to it glued two YAML
  keys. Check the tail after editing.
