# DAILY_LOG.md

One entry per run, newest first, in the REPORT format from CLAUDE.md.

---

## 2026-10-20 — Run 6 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | Item 9 in two PRs. Part 1: `TrackingHealthTracker` gets every fix and every engine start and stop, and a ticker polls it, so an open gap shows on the screen while it is still open. `TrackingController.health` exposes it, and `DiagnosticsReportBuilder` builds the copied report ([#23](https://github.com/Alphaguyasa/Meret/pull/23)). Part 2: the diagnostics screen in en/am, opened from the GPS chip on the map, with «Copy diagnostics» ([#24](https://github.com/Alphaguyasa/Meret/pull/24)). Item 10: after a reboot with a track still open, one notification offers «Resume recording». The service is never started automatically ([#25](https://github.com/Alphaguyasa/Meret/pull/25)). **Phase 5 code is complete.** Its device criteria are in NEEDS_APPROVAL. |
| Merged | #23 yes, `b9955cd` · #24 yes, `987cc6e` · #25 yes, `f18ca7e`. All passed CI on the first attempt, about 3 min each. #25 got a main merge plus one commit before merging, because #24's test fake implements the interface #25 extends. |
| Failed or blocked | Nothing. |
| Roadmap | 7 open items: 11–15, plus new items 16 (re-run onboarding from diagnostics) and 17 (`tracking_gap` telemetry). No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). Checked the models page: no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7 unchanged. A9 extended with 52 more Amharic strings (diagnostics and the after-restart prompt). 2 new "Needs a device" rows (the diagnostics screen and paste, reboot mid-track). The Phase 5 survival test is now ready to run. |
| Next | Item 11 (Dependabot, monthly, at most 2 open PRs), then item 12 (Phase 0 glyphs, sprite, style). |

Notes for the next run:
- The health tracker lives in `DefaultTrackingController`, not `TrackingService` as the old
  roadmap text said. The controller owns the engine transitions and is tested without a
  service. It runs from `beginRecording` to `stop` and starts empty after process death.
- The copied report is fixed-format English on purpose; the screen is localised. The
  `DiagnosticsSnapshot` type has no field for a position, a name or an id. Keep it that way:
  the report goes to WhatsApp.
- **Interface changes ripple into test fakes**: `TrackingController` is implemented by the
  fakes in `TrackingServiceTest` and `DiagnosticsViewModelTest`. Two open PRs that both touch
  it need a main merge before the second one merges.
- `BootReceiver` is `exported="true"`: only the system can send `BOOT_COMPLETED`, and the
  receiver ignores every other action. It uses `BOOT_COMPLETED`, not the locked variant,
  because DataStore lives in credential-protected storage.
- `:domain` tests can be run locally: compile `:core:model`, `:core:common` (minus
  `DispatcherProvider`) and `:domain` with kotlinc, then run JUnit. Maven Central worked first
  time this run.

## 2026-10-19 — Run 5 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | Item 8b, split into two PRs. Part 1 adds the onboarding state: `OnboardingStore` saves skipped steps in DataStore, `AndroidPermissionSnapshotSource` reads the permissions fresh on every call, and `OnboardingViewModel` drives the step picker ([#20](https://github.com/Alphaguyasa/Meret/pull/20)). Part 2 adds the screens: one per step in en and am, a rationale before background location, a settings route for a denied or approximate-only location, the battery-optimisation dialog, and the OEM deep link or manual steps. `MeretApp` shows the flow before the map ([#21](https://github.com/Alphaguyasa/Meret/pull/21)). Phase 5f is done. |
| Merged | #20 yes, `1574771` · #21 yes, `026a710`. Both passed CI on the first attempt (about 2.5 min each). |
| Failed or blocked | Nothing. |
| Roadmap | 7 open items (9–15). No replan needed (the threshold is fewer than 5). |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). Checked the models page: no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7 unchanged. A9 extended: 21 new Amharic onboarding strings, plus a question about which app name the Amharic text should use («መሬት» or "Meret"). New "Needs a device" row: the onboarding flow on Android 8, 10, 11+ and 13+. |
| Next | Item 9 (diagnostics screen, wiring `TrackingHealthMonitor` into `TrackingService`), then item 10 (boot receiver). |

Notes for the next run:
- **ViewModel tests outside Android work.** Compile against stubs (`androidx.lifecycle.ViewModel`
  with a `viewModelScope`, an in-memory store) with `kotlinc`, plus coroutines, turbine,
  JUnit and Truth from Maven Central. Maven Central returns 429 now and then; retry with a
  backoff. The real tests end every ViewModel scope inside `runTest`
  (`runOnboardingTest`). Without that, the store's flow resumes on Main after `resetMain`
  and fails the next test.
- Onboarding runs once per process (`rememberSaveable` in `MeretApp`). A skip is permanent.
  Nothing in Settings can re-run the flow yet; add that to item 9's diagnostics screen if
  a supervisor needs it.
- Lint's `BatteryLife` check warns about `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in
  `:feature:settings`. It is deliberate (APK distribution, Transsion/Xiaomi) and is not
  suppressed.
- detekt's `CyclomaticComplexMethod` (threshold 15) counts each `when` branch inside a
  composable's lambdas. Move the dispatch into a small class (`StepActions`).

## 2026-10-18 — Run 4 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | Item 7: `DeviceCompat` OEM guidance. It covers Tecno/Infinix/Itel, Xiaomi (incl. Redmi/POCO), Oppo/realme, Vivo/iQOO, Huawei/Honor, Samsung and OnePlus. Each brand gets autostart deep links tried in order, with manual steps in en and am if none opens ([#17](https://github.com/Alphaguyasa/Meret/pull/17)). Item 8 was split. 8a, the `OnboardingStepPicker` state machine for the permission flow, is done and tested on API 26–36 ([#18](https://github.com/Alphaguyasa/Meret/pull/18)). 8b, the screens, is now the top roadmap item. |
| Merged | #17 yes, `d784d20` · #18 yes, `704dbe8`. Both passed CI on the first attempt (about 2.5 min each). |
| Failed or blocked | Nothing. |
| Roadmap | 8 open items (8b, 9–15). No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). Checked the models page: no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7 unchanged. A9 extended: 13 new Amharic OEM-step strings to review. New "Needs a device" row: the OEM deep links on real Tecno/Xiaomi firmware. |
| Next | Item 8b (onboarding screens), then item 9 (diagnostics screen). |

Notes for the next run:
- **Maven Central worked this run.** JUnit 4.13.2, hamcrest-core 1.3, Truth 1.4.4 and Guava
  33.3.1 downloaded from `repo1.maven.org`. With `kotlinc` you can now **run** pure-Kotlin
  unit tests locally. Compile main with `-Xexplicit-api=strict` and the tests in a second
  pass without it, then run `org.junit.runner.JUnitCore`. For code that uses `R`, a stub
  `object R { object string { const val x = 1 } }` works.
- Standalone ktlint 1.8.0 flags `build-logic/.../KotlinAndroid.kt:79` (multiline-expression-wrapping)
  on main. CI's ktlint task does not cover `build-logic`, so it is not a CI failure. Left alone.
- `DeviceCompat.create(context)` is the production entry point. Call `open(link)` from a button
  only. `oemGuidanceFor(OemFamily)` is public, so 8b can show any brand's steps in a preview.
- `OnboardingStepPicker.next(snapshot, skipped)`: 8b must build `PermissionSnapshot` fresh on
  every resume. Add `OEM_AUTOSTART` to `skipped` once the worker returns from the OEM screen:
  no API reports whether autostart is on.
- Debug APK after #17: **13,224 KiB** (+271 KiB since #14's 12,953 KiB artifact zip; that
  figure was the zip, so the two are not directly comparable).

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
