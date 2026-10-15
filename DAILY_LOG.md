# DAILY_LOG.md

One entry per run, newest first, in the REPORT format from CLAUDE.md.

---

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
