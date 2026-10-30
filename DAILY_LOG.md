# DAILY_LOG.md

One entry per run, newest first, in the REPORT format from CLAUDE.md.

---

## 2026-10-30 — Run 18 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | **Item 22 (Phase 7c): track list.** The Tracks tab lists every track newest first: name or date, distance, moving time, and where it stands (Recording, Paused, Not finished for an open row no recording holds, or its sync state). Delete hides the row and offers Undo for 10 s; nothing is written until then, so a process death keeps the track. A delete is a soft discard: the row and every point stay in Room, re-queued to sync. The track being recorded cannot be deleted ([#59](https://github.com/Alphaguyasa/Meret/pull/59)). **Item 23a (Phase 7d-1): track detail.** Tapping a track shows totals, an elevation profile with lowest/highest height, and one line per segment (distance, monotonic duration, points), live while recording. `TrackSummariser` derives it from the stored points with the same step gate as the total; raw points untouched ([#60](https://github.com/Alphaguyasa/Meret/pull/60)). Item 23 was split: 23b (stop undo, bar segment count) is next. |
| Merged | #59 yes, `1aad0d8`, after one fix (attempt 1 of 2): ktlint `function-signature` in the moved `formatDistance`. #60 yes, `6db9994`, after one fix (attempt 1 of 2): ktlint `chain-method-continuation` inside `assertThat(...)` in two tests. CI took 4–5 min per run. |
| Failed or blocked | Nothing is blocked. ktlint/detekt still cannot run locally (no SDK, binaries refused), so each PR needed one lint fix. |
| Roadmap | 7 open: 23b, 26 (new: purge discarded tracks, waits on A13), 24, 25 (A12), 14 (A6), 15, 18. No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). Anthropic's models page lists no newer Opus. No action. |
| Needs approval | **New A13:** when to purge deleted tracks (default: never, until sync exists). A9: 23 new Amharic strings (track list and detail). 2 new "Needs a device" rows (track list, track detail). A2, A3, A5, A6, A7, A10, A11, A12 unchanged. **Dependabot #30** (Gradle group, 27 updates) still open, unmerged, waiting on A10. |
| Next | 23b (stop undo and the tracking bar's segment count), then 24 (DataStore test scopes). |

Notes for the next run:
- **ktlint `chain-method-continuation` counts chains inside arguments too.**
  `assertThat(a.b().c.d())` has 4 operators and fails. The run-17 script only counted the top
  level. This regex catches it:
  `[A-Za-z_]\w*(\([^()]*\))?(\??\.[A-Za-z_]\w*(\([^()]*\)|\s*\{[^{}]*\})?){4,}`.
- **`function-signature` again:** changing `internal` to `public` changes the line length.
  Re-check expression bodies after any change to visibility.
- `TrackRepository` (`:core:data`) has `observeTracks`, `observeTrackDetail` and `discardTrack`.
  Every fake needs all three (`TrackListViewModelTest`, `TrackDetailViewModelTest`).
- `formatDuration`/`formatDistance` now live in `:core:designsystem/format`.
- Shared track text helpers (`title()`, `stats()`, `standing()`, `distanceText()`) are in
  `feature/tracking/TrackText.kt`; row mapping in `TrackItems.kt`.

## 2026-10-29 — Run 17 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | **Item 20 (Phase 7a): the record button works.** Tapping it with nothing recording writes a new track row in Room (UUIDv7), then starts the foreground service from that tap. If the system refuses the service, the empty row is deleted. A tap pauses, the next tap resumes, and a long-press stops. A second tap before the service starts makes no second track. If the service has not started recording within 10 s, an en/am message says so. The tracking bar shows the duration since start, distance, points and accuracy. The map never calls the tracking controller: commands go to the service as intents through a `:domain` interface (ADR-009) ([#56](https://github.com/Alphaguyasa/Meret/pull/56)). **Item 21 (Phase 7b): live track line.** While recording, a green line follows the dot, under the puck. It is refreshed at most every 2 s from Room's filtered positions and simplified (Douglas–Peucker, 3 m) off the main thread. Raw points are never touched. A pause leaves a gap in the line ([#57](https://github.com/Alphaguyasa/Meret/pull/57)). |
| Merged | #56 yes, `fa90463`, after one fix (attempt 1 of 2): ktlint `chain-method-continuation` (4 calls on one line). #57 yes, `ff9a1d4`, after one fix (attempt 1 of 2): ktlint `function-signature`. CI took about 4 min per run. |
| Failed or blocked | Nothing is blocked. ktlint and detekt could not run locally again (auto mode refused the downloaded binaries), so both PRs needed one lint fix each. Each fix cost one CI run. See the notes for the two checks I now script. |
| Roadmap | 7 open items: 22, 23, 24, 25 (blocked on A12), 14 (blocked on A6), 15, 18. No replan needed. Item 22 now notes that an open row with no session can exist. The Phase 9 line notes that tracks have `project_id`/`user_id` `"local"` until sign-in. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). I checked Anthropic's models page: there is no newer Opus. No action. |
| Needs approval | A9: 1 new Amharic string (map: "Recording did not start…"). 2 new "Needs a device" rows: the record button and the live line, which includes the Phase 7 frame-rate check. The two Phase 5 rows that waited for a record button can now be tested. A2, A3, A5, A6, A7, A10, A11, A12 unchanged. **Dependabot #30** (Gradle group, 27 updates) is still open and unmerged, waiting on A10. |
| Next | 22 (track list with delete and undo), then 23 (track detail and stop undo). |

Notes for the next run:
- **Two ktlint rules the eye misses.** I check both with a short Python script before pushing,
  because the binaries are refused:
  - `chain-method-continuation`: 4 or more `.`/`?.` calls at the top level of one line must
    be wrapped, one per line.
  - `function-signature`: an expression body whose first line fits within 100 columns
    after `=` must start on the signature line. A block body avoids both.
- `TrackingController` now has `activeLine`. Every fake needs it (Diagnostics, TrackingService,
  DevicePositionRepository and MapViewModel tests).
- `RecordingCommands` (`:domain`) is bound in `:feature:tracking` (`RecordingCommandsModule`).
  `StartTrackingUseCase` is provided by `:core:data`'s `TrackingModule`, because `:domain` has
  no `javax.inject`.
- detekt's `LongParameterList` constructor threshold is 7. `MapViewModel` has 6, so any new
  dependency there needs to go through an existing one (the live line went through the
  controller for this reason).

## 2026-10-29 — Run 16 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | **6g-2, the last part of Phase 6:** the Packs tab now lists the installed packs, with name, size and Delete (it asks first). «Import map pack» opens the system file picker for any file type, because many file managers give `.mbtiles` no type. A progress bar shows while the pack copies. The result is a dialog: installed, or one en/am message per failure (not a map pack, no space, could not read, could not save). Opening the screen deletes pack files that have no row, which a crash mid-import leaves. A lock keeps that sweep away from an import in progress ([#53](https://github.com/Alphaguyasa/Meret/pull/53)). **Item 19:** `assembleRelease` now makes per-ABI APKs. A new `release-size` workflow builds the R8 release, the first time CI ran R8, and reports the sizes ([#54](https://github.com/Alphaguyasa/Meret/pull/54)). **Release APK: 51,440 KiB with every ABI → 15,573 KiB (arm64-v8a) or 12,003 KiB (armeabi-v7a).** The universal APK still carries all four ABIs (51,440 KiB). The AAB is 22,482 KiB. |
| Merged | #53 yes, `b2d474a`, green on the first attempt. #54 yes, `60fa667`, after one fix (attempt 1 of 2): AGP refuses ABI splits and `bundleRelease` in one Gradle invocation, so the bundle now builds on its own. |
| Failed or blocked | Nothing is blocked. **The release is unsigned** (there is no signing config), so no phone can install it. A12 asks who holds the key. |
| Roadmap | **Replan done:** after 13 and 19, only 3 items were open. New: 20–23 (Phase 7: record button, live track line, track list, track detail and stop undo), 24 (DataStore test scopes, the run 12 hang), 25 (release signing, blocked on A12). 9 open in all. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). I checked Anthropic's models page: there is no newer Opus. No action. |
| Needs approval | **New A12:** the signing key. A9: 15 new Amharic strings and 1 rewritten (the packs screen). New "Needs a device" row: pack import. A2, A3, A5, A6, A7, A10, A11 unchanged. **Dependabot #30** (Gradle group, 27 updates) is still open and unmerged, waiting on A10. It touches the version catalog, so its next rebase also runs `release-size`. |
| Next | 20 (Phase 7a: the record button starts, pauses and stops a track), then 21 (live track line). |

Notes for the next run:
- **`release-size.yml`** runs on PRs that touch `app/build.gradle.kts`, `app/proguard-rules.pro`
  or `gradle/libs.versions.toml`. It takes about 6 minutes. Read the sizes with
  `get_job_logs(tail_lines=3000)`, then `jq -r .logs_content | grep '| '`.
- `TilePackRepository` now has `deletePack` and `deleteUnregisteredPacks`. Every fake needs
  both (`MapViewModelTest` has them).
- The `RecordButton`/`TrackingBar` on the map are drawn but not wired (`MapRoute` passes `{}`).
  That is item 20.

## 2026-10-28 — Run 15 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | **6f-2b-2:** with no recording, the puck now shows live fixes too. The map has its own request (`PreviewLocationSource`): 1 fix a second, **not batched**, and never touching the recording's engine. It runs only while the map is collecting. It stops 5 s after the map leaves the foreground, and the moment a recording starts. A paused recording gets it back. It uses the same provider (fused or GPS) as the engine ([#50](https://github.com/Alphaguyasa/Meret/pull/50)). **6g-1**, split from 6g: the pack import without its UI. `MbtilesPackInstaller` copies the picked file to a `.partial` file, checks it (readable metadata, `format=pbf`, a `tiles` table), then renames it to `filesDir/packs/{id}.mbtiles`. `TilePackRepository.importPack` writes the `tile_pack` row the map already reads. A failed import leaves no file and no row ([#51](https://github.com/Alphaguyasa/Meret/pull/51)). |
| Merged | #50 yes, `7eabc40`. #51 yes, `a914448`. Both were green on the first attempt (CI about 4 min each). |
| Failed or blocked | Nothing is blocked. Users still cannot import a pack: the picker and the pack list are 6g-2. |
| Roadmap | 5 open items: 13 (only 6g-2 left), 14, 15, 18, 19. No replan yet; the next run replans if it closes one. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). I checked Anthropic's models page: there is no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7, A9, A10, A11 unchanged; no new strings this run. One new "Needs a device" row: the live puck while idle. **Dependabot #30** (Gradle group, 27 updates) is still open and unmerged, waiting on A10. |
| Next | 6g-2 (picker, progress, error messages and pack list on the packs screen, en/am), then 19 (APK size). |

Notes for the next run:
- **Local compile without the SDK works for pure code.** kotlinc 2.2.10 (GitHub release) plus
  Maven Central jars ran `DevicePositionRepositoryTest` (9/9) on the JVM. For code that
  touches `android.*`, `com.google.android:android:4.1.1.4` from Maven Central is enough to
  type-check. Its stubs throw `Stub!`, so tests that construct Android classes fail there
  but pass in CI (`isReturnDefaultValues`). Google Maven is refused (403), so Room was
  stubbed with six annotation classes. Robolectric tests still only run in CI.
- **`TilePackRepository.importPack(displayName, sizeBytes, open)`**: 6g-2 passes
  `contentResolver.openInputStream(uri) ?: throw FileNotFoundException()` as `open`, and reads
  `DISPLAY_NAME`/`SIZE` from `OpenableColumns`. A null size skips the free-space check.
- `:core:data` now depends on `:core:maps` (for the installer). Every `TilePackRepository`
  fake needs `importPack` (`MapViewModelTest` has one).

## 2026-10-28 — Run 14 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | **6f-2b-1:** the map has a location puck: a dot with a ground-sized accuracy circle, drawn from our own GeoJSON layers. The cached fix shows grey and fainter. **Follow mode:** on at start, so the first fix centres the map at zoom 16. A pan or pinch turns it off. The recentre button, a no-op until now, turns it back on and is filled while following. The map now draws the newest pack that **covers the user**, else the newest pack ([#47](https://github.com/Alphaguyasa/Meret/pull/47)). **Item 16:** "Check permissions again" on the diagnostics screen. It shows when onboarding would have a step to show, and always on Transsion and Xiaomi phones (no API reports autostart). It clears the stored skips, then shows the onboarding flow ([#48](https://github.com/Alphaguyasa/Meret/pull/48)). |
| Merged | #47 yes, `d59bceb`. #48 yes, `d889766`. Both were green on the first attempt (CI about 3–4 min each), and the main push run on `d59bceb` was green too. |
| Failed or blocked | Nothing is blocked. **Limit (6f-2b-2, new):** the puck gets live fixes only while a track records, because the map never starts the recording's engine. When idle it shows the cached fix. |
| Roadmap | 5 open items: 13 (6f-2b-2 and 6g left), 14, 15, 18, 19. No replan yet; the next run replans if it closes one. Item 16 was taken ahead of 13's next parts: each of those is about a day, and 16 was small. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). I checked Anthropic's models page: there is no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7, A10, A11 unchanged. A9: 2 new Amharic strings (diagnostics). Two new "Needs a device" rows: the puck and follow mode, and the recheck button. **Dependabot #30** (Gradle group, 27 updates) is still open and unmerged, waiting on A10. |
| Next | 6f-2b-2 (live position when idle, unbatched, map in the foreground only), then 6g (SAF pack import) or 19 (APK size). |

Notes for the next run:
- **ktlint and detekt ran locally this run.** The ktlint 1.8.0 binary and
  `detekt-cli-1.23.8-all.jar` were downloaded to the scratchpad and run with `java -jar`.
  detekt caught `ReturnCount` and `LongParameterList` (the limit is 7 constructor
  parameters) before push. Both PRs went green on the first try.
- **MapLibre 12.3.1 facts** (sources jar): `GeoJsonSource(id, String)` and
  `setGeoJson(String)`, so the puck needs no `org.maplibre.geojson` types.
  `MapLibreMap.getStyle(OnStyleLoaded)` calls back once the style is *fully* loaded and
  queues otherwise. `getStyle()` returns null while loading. A new style drops runtime
  sources, so `OfflineMap` re-adds the puck on `MapView.OnDidFinishLoadingStyleListener`.
  `OnCameraMoveStartedListener.REASON_API_GESTURE` = 1 is a user gesture; our own
  `easeCamera` reports 2.
- **Why not `LocationComponent`:** it brings its own location engine. A second GPS client
  would draw positions that differ from the recorded track.
- `hiltViewModel(key = ...)` (hilt-navigation-compose 1.2.0) compiled. The onboarding
  ViewModel is activity-scoped, so a re-run needs a new key or it keeps the old skips in
  memory. Google's Maven sources jars return 403 through the proxy; Maven Central works.

## 2026-10-27 — Run 13 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | **Main was red, and that came first.** CI on `1b2537a` (#42's merge) hung in `LocalTileServerTest > stop closes the port...` until the 10-minute task timeout. Run 12 logged #42 as green, which was true for the PR run but not the main run. **Fix:** every test HTTP request and raw socket in `:core:maps` now has a 5 s connect and read timeout ([#44](https://github.com/Alphaguyasa/Meret/pull/44)). **6f-2a**, split from 6f-2: map zoom buttons, a compass that shows only when the map is turned, and a 1-2-5 scale bar. They go through a new `OfflineMapController`, so no MapLibre type reaches `:feature:map` ([#45](https://github.com/Alphaguyasa/Meret/pull/45)). |
| Merged | #44 yes, `5d0f73d`, after one ktlint fix (attempt 1 of 2). #45 yes, `026dddf`, green on the first attempt. CI took about 2 and 5 minutes. |
| Failed or blocked | Nothing is blocked. ktlint and detekt could not run locally: auto mode refused the downloaded detekt jar again. CI was the only check, and it caught one ktlint issue (see notes). |
| Roadmap | 6 open items: 13 (6f-2b and 6g left), 14, 15, 16, 18, 19. No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). I checked Anthropic's models page: there is no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7, A10, A11 unchanged. A9: 5 new Amharic map strings. One new "Needs a device" row: the map controls. **Dependabot #30** (Gradle group, 27 updates) is still open and unmerged, waiting on A10. |
| Next | 6f-2b (location puck and follow mode, which wires `onRecentreMap`), then 6g (SAF pack import) or 19 (APK size). |

Notes for the next run:
- **Check the main push run, not only the PR run.** #42 went green as a PR and then hung on
  main. Look at `list_workflow_runs` with `branch: main` at the start of every run.
- **Hang cause (likely, not reproduced):** after `stop()`, the test asked the freed port for a
  tile. Once another loopback listener, such as a Gradle worker, takes that port, a client with
  no read timeout waits forever. Give every test socket a deadline.
- **ktlint `function-signature`:** an expression body that spans several lines starts on the
  signature line (`fun f(): Socket = Socket().apply {`), not on the next line. A one-line
  expression that is too long goes on the next line.
- **MapLibre 12.3.1 facts** (sources jar): there is no scale bar in the SDK.
  `Projection.getMetersPerPixelAtLatitude` is per logical pixel, because `NativeMapView`
  divides by `pixelRatio`, so it gives metres per dp. `CameraPosition.bearing` runs 0–360,
  so 359.9 means north up (`degreesOffNorth`). The compass is on by default at top-end;
  `OfflineMap` turns it off when a controller is attached. The CI log's ktlint report blob is
  refused by the proxy (403); read the Gradle log instead.

## 2026-10-26 — Run 12 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | **Run 11 (earlier today) wrote no log.** It merged 6e-2, the MapLibre `MapView` wrapper ([#40](https://github.com/Alphaguyasa/Meret/pull/40)), and opened 6f-1 ([#41](https://github.com/Alphaguyasa/Meret/pull/41)), then stopped. #41's CI hung, so this run picked it up. **6f-1:** the map screen draws the newest installed pack through `OfflineMap`. With no pack it shows the placeholder. A bad pack id or an unwritable style leaves the map blank instead of crashing. **Item 17:** each closed tracking gap goes to `Telemetry` as `tracking_gap`, with `duration_ms` as its only attribute. `NoOpTelemetry` is the binding ([#42](https://github.com/Alphaguyasa/Meret/pull/42)). |
| Merged | #40 yes, `c0829be` (run 11). #41 yes, `2efa101`, after one fix (attempt 1 of 2, see notes). #42 yes, `1b2537a`, green on the first attempt. Each green run took about 4 min. |
| Failed or blocked | #41's first CI run cost 45 Actions minutes: a test outside the PR timed out, and then the build sat idle for 38 min. It is fixed, and new timeouts limit a future hang (see notes). Linters and kotlinc could not run locally: auto mode refused to run downloaded binaries this run. CI was the only check. |
| Roadmap | 6 open items: 13 (6f-2 and 6g left), 14, 15, 16, 18, and **19, new:** the APK went from 13.4 to 33.4 MB with MapLibre (4 ABIs); ship per-ABI. No replan needed. Item 17 was taken ahead of 13: 6f-2 is larger and needs MapLibre API checks. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). I checked Anthropic's models page: there is no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7, A9, A10, A11 unchanged. **Dependabot #30** (Gradle group, 27 updates) is still open and unmerged, waiting on A10. New "Needs a device" row: the map screen draws a sideloaded pack. |
| Next | 6f-2 (map controls: puck, follow, compass, scale bar, zoom), then 6g (SAF pack import) or 19 (APK size). |

Notes for the next run:
- **The #41 hang.** `OnboardingViewModelTest > returning from the OEM screen ...` failed with
  `UncompletedCoroutinesError` after 1 min. It waited on `store.skippedSteps.first { }` for a
  write made on DataStore's default `Dispatchers.IO` scope. After that, every requested task
  had printed its header, yet Gradle never printed `BUILD FAILED`. It sat idle from 11:51 to
  12:29 until the 45-min job timeout. The root cause of the idle time was not proven (no SDK
  to reproduce). Fixes: the test's DataStore now uses `scope = backgroundScope`; every `Test`
  task times out after 10 min (`KotlinAndroid.kt`, `configureTestTimeout`); and the CI job
  times out after 30 min. **Any other test that builds a DataStore without a test scope is a
  candidate for the same flake:** `DefaultTrackingControllerTest`, and the unscoped
  `OnboardingStoreTest`/`TrackingSessionStoreTest` paths.
- Read a long CI log with `get_job_logs(return_content=true, tail_lines=3000)`. The result
  is saved to a file; extract it with `jq -r .logs_content`. The blob URL itself is refused
  by the proxy (403).
- `Telemetry` was not bound in Hilt before #42. It is now `CommonModule.provideTelemetry()`
  in `:core:data`. A deployment's reporter replaces that one line.
- `MapViewModel` picks `packs.firstOrNull()`; `TilePackDao.observeTilePacks` orders newest
  first. 6g must write `installedAt` so that the order holds.

## 2026-10-24 — Run 10 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | **6d-2:** the glyph stack `Noto Sans Regular,Noto Sans Ethiopic Regular` (all 256 ranges, 2.4 MB) and the 1x/2x Maki sprite are in `:core:maps` assets. Both were built with `tools/build-glyphs.sh` and `build-sprite.sh`, with `OFL.txt` beside them. `MapAssetsTest` checks every range of every stack the style uses, Ethiopic `4608-4863` (130 KB), the sprite icons and the licence ([#37](https://github.com/Alphaguyasa/Meret/pull/37)). **6e-1:** 6e was split. `OfflineMapSession` reference-counts the loopback server: the first acquire starts it, every acquire writes the style for the current port, and the last release stops it. `PackDirectoryResolver` maps an id to `{dir}/{id}.mbtiles` and refuses paths, `..`, links out of the directory and corrupt packs ([#38](https://github.com/Alphaguyasa/Meret/pull/38)). |
| Merged | #37 yes, `436ba95` (CI green on the first attempt). #38 yes, `b4e2e46`. Its first CI run failed on detekt `ReturnCount` in `PackDirectoryResolver.open`. Attempt 1 of 2 fixed it, and CI went green. |
| Failed or blocked | Nothing is blocked. One detekt failure was mine, and it is fixed (see notes). |
| Roadmap | 6 open items: 13 (6e-2, 6f, 6g left), 14–18. No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). I checked Anthropic's models page: there is no newer Opus. No action. |
| Needs approval | A11: the default (a), commit the assets, is now applied. Tell me if you want CI-generated assets instead. A2, A3, A5, A6, A7, A9, A10 are unchanged. **Dependabot #30** (Gradle group, 27 updates) is still open and unmerged, waiting on A10. |
| Next | 6e-2: add MapLibre Android 12.3.1 and the Compose `MapView` wrapper, with the ambient cache set to 0 and the APK measured before and after. Then 6f (map screen controls). |

Notes for the next run:
- **Run detekt locally before pushing.** ktlint alone missed `ReturnCount` (max 2 returns).
  `detekt-cli-1.23.8-all.jar` from the GitHub release works:
  `java -jar detekt.jar --config config/detekt/detekt.yml --build-upon-default-config --input core/maps/src`.
- **MapLibre facts checked in the 12.3.1 sources jar.** `MapView` has
  `onCreate/onStart/onResume/onPause/onStop/onDestroy/onLowMemory/onSaveInstanceState`.
  `MapLibre.getInstance(context)` exists, and so does
  `OfflineManager.setMaximumAmbientCacheSize(size, callback)`. The Java and Kotlin sources
  never mention `no-store`, so set the ambient cache to 0. Do not call
  `MapLibre.setConnected(false)`: that would also stop requests to 127.0.0.1. The POM pulls in
  OkHttp 4.12.0 and Timber 5.0.1. The sources jar has overlapping zip entries, so `unzip` and
  Python's `zipfile` refuse it. Grep it with Python after catching the error (most files
  extract first), or use `jar xf`.
- **The APK has no glyphs yet.** `:app` does not depend on `:core:maps`, so the debug APK
  artifact stayed at about 13.4 MB (zip). The ~2.4 MB glyphs arrive with 6e-2.
- `OfflineMapSessionTest` uses real loopback sockets and a `StandardTestDispatcher` for `io`.
  Never wrap a session call in `runBlocking` inside `runTest`: the test dispatcher would
  deadlock. Use a suspend `expect(...)` helper.
- Local runs this time: kotlinc 2.2.10 with a stub `MbtilesReader` (it needs Android
  SQLite). 27 tests passed. Maven Central returned one 429 (junit), and a retry worked.

## 2026-10-23 — Run 9 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | **6c** network security config: cleartext to `127.0.0.1` only (no subdomains, no `localhost`), system CAs only, no debug overrides. `NetworkSecurityConfigTest` guards it ([#34](https://github.com/Alphaguyasa/Meret/pull/34)). **6d-1** `StyleProvider`: the build copies `tools/style/meret-style.json` into `:core:maps` assets as `map/style.json`. `StyleProvider` swaps in the loopback TileJSON URL, refuses any URL that is not on 127.0.0.1, and writes the style to `cacheDir` by write-then-rename. It returns a `file://` URI ([#35](https://github.com/Alphaguyasa/Meret/pull/35)). 6d was split: glyphs and sprite are now 6d-2. |
| Merged | #34 yes, `622964f` · #35 yes, `54b46a5`. #34 failed CI once on a flaky test it did not cause (`LocalTileServerTest`, see notes). It passed after the test fix, which was attempt 1 of 2. #35 passed on the first attempt. Each CI run took about 2 min. |
| Failed or blocked | Nothing is blocked. One flaky test was found and fixed in #34. |
| Roadmap | 6 open items: 13 (6d-2 to 6g left), 14–18. No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). Checked Anthropic's models page: no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7, A9, A10 unchanged. **A11 new** (low stakes, has a default): commit the generated glyphs and sprite (~2.4 MB) or generate them in CI. **Dependabot #30** (Gradle group, 27 updates) is still open and unmerged, waiting on A10. |
| Next | 6d-2 (bundle the glyph stack directory and the sprite), then 6e (MapLibre Compose wrapper). |

Notes for the next run:
- **Flaky test, fixed:** `LocalTileServerTest` checked that a port was closed with a bare
  `Socket(...)` connect after `stop()`. In CI that connect once succeeded. After a port is
  freed, another process can reuse it, or a loopback connect can land on its own socket. The
  test now sends a request and expects no tile back. Never assert "port closed" by connect.
- **Placeholder trap:** `meret-style.json` has `{{TILE_SERVER}}` twice. One is the source
  URL; the other is inside the `metadata` notes text. `StyleProvider` replaces only the
  quoted JSON value `"{{TILE_SERVER}}"`. The source URL is the TileJSON
  (`baseUrl(packId) + "/tiles.json"`), not the pack root. MAPS_OFFLINE.md had both wrong;
  it is fixed.
- **Generated assets in AGP 9 work:** a per-variant task registered in `onVariants`, then
  `variant.sources.assets?.addGeneratedSourceDirectory(task, Task::outputDir)`, defined in
  `core/maps/build.gradle.kts`. Robolectric sees the result (`StyleAssetTest`, through
  `isIncludeAndroidResources`). Use the same pattern for 6d-2 if the glyphs end up generated.
- The squash message of #34 corrects the branch commit: with targetSdk 36, cleartext is
  already off by default on every API level. It is not on for API 26–27.
- Local runs: kotlinc 2.2.10 + JUnit, Truth, Guava, coroutines(-test) 1.10.2 and
  kotlinx-serialization 1.9.0 from Maven Central, with no 429s this run. `LocalTileServerTest`
  passed 13/13, three times in a row, with `-Xfriend-paths`. ktlint 1.8.0 needs explicit
  file paths: a `**` glob walks `/proc` and crashes.

## 2026-10-22 — Run 8 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | Item 13 (Phase 6) split into 6a–6g, and two parts done. **6a** `MbtilesReader` opens a pack read-only, flips XYZ rows to TMS, returns tiles as stored (gzip flagged, not unpacked), and validates the metadata. Tested with real SQLite under Robolectric, on plain and deduplicated (Planetiler view) packs ([#31](https://github.com/Alphaguyasa/Meret/pull/31)). **6b** `LocalTileServer` serves `/{packId}/tiles.json` and `/{packId}/{z}/{x}/{y}.pbf` on `127.0.0.1`, on a port picked at start, with keep-alive and gzip passthrough, and returns 204 for a missing tile ([#32](https://github.com/Alphaguyasa/Meret/pull/32)). |
| Merged | #31 yes, `2cbba96` · #32 yes, `c3f49c1`. Both passed CI on the first attempt (about 2 min each). |
| Failed or blocked | Nothing. |
| Roadmap | 6 open items: 13 (6c–6g left), 14–18. No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). Checked Anthropic's models page: no newer Opus. No action. |
| Needs approval | A2, A3, A5, A6, A7, A9, A10 unchanged. **Dependabot #30** (Gradle group, 27 updates) is open and green. It is left unmerged until A10 is answered. No GitHub Actions Dependabot PR was opened. New "Needs a device" row: the loopback tile server on a real phone (after 6c and 6e). |
| Next | 6c (network security config, cleartext to 127.0.0.1 only), then 6d (StyleProvider and bundled glyphs and sprite). |

Notes for the next run:
- **Doc correction in #32: no NanoHTTPD.** It has been unmaintained since 2.3.1 and has open
  advisories (CVE-2020-13697, CVE-2022-21230). `LocalTileServer` is hand-written HTTP/1.1,
  GET/HEAD only, about 500 lines. MAPS_OFFLINE.md, ARCHITECTURE.md and SESSION_PROMPTS.md
  are corrected.
- `TileSource` is the seam: the server reads packs through it, and `MbtilesReader` implements
  it. Server tests use an in-memory source and real loopback sockets, so they run on the JVM
  with no Android SDK.
- **Running `core/maps` tests locally:** kotlinc 2.2.10 plus JUnit, Truth, Guava,
  hamcrest and kotlinx-serialization-json/core-jvm 1.9.0 from Maven Central (two 429s; retry
  with a backoff). Compile the tests with `-Xfriend-paths=<main classes dir>`, or every
  `internal` symbol is invisible. 32 tests ran this way. `MbtilesReaderTest` needs
  Robolectric and runs in CI only.
- Two stacked PRs: 6b branched from 6a's branch. After #31's squash, `git merge origin/main`
  conflicted on the two files 6b had changed. 6b's side was a strict superset, so
  `checkout --ours` was right. Diff against main afterwards to confirm.
- Response headers: every response sends `Cache-Control: no-store`, because the port changes
  per start. 6e must check that MapLibre's ambient cache honours it.
- Debug APK after #31: **13,332 KiB** (unchanged; nothing in the app uses `:core:maps` yet).

## 2026-10-21 — Run 7 (scheduled, cloud)

| Field | Content |
|---|---|
| Done | Item 11: `.github/dependabot.yml` for Gradle and GitHub Actions. It runs monthly, takes minor and patch versions only, and opens one grouped PR per ecosystem, so at most 2 are open ([#27](https://github.com/Alphaguyasa/Meret/pull/27)). Item 12: the Phase 0 remainder ([#28](https://github.com/Alphaguyasa/Meret/pull/28)), made of `tools/style/meret-style.json` (OpenMapTiles layers, `asset://` glyphs and sprite, Noto Sans + Ethiopic stack, `name:am` first), `build-glyphs.sh` (fontnik, including the merged stack directory MapLibre actually requests), `build-sprite.sh` (Maki packed with sharp), `preview/` (Python stdlib server + MapLibre GL JS page) and `tools/README.md`. `MeretStyleTest` (9 cases) guards the style in CI. **Phase 0 tooling is complete.** |
| Merged | #27 yes, `30bc255` · #28 yes, `a5fe889`. Both passed CI on the first attempt (about 1.5 min each). |
| Failed or blocked | Nothing. |
| Roadmap | 6 open items: 13–17, plus new item 18 (Dependabot for `tools/` npm). No replan needed. |
| Model | Claude Opus 5.5 (`claude-opus-5-5`). Checked Anthropic's models docs: no newer Opus. No action. |
| Needs approval | **A10 new:** turn on Dependabot alerts and security updates (free, in repo settings), and decide who merges Dependabot PRs, because their squash commit is authored by `dependabot[bot]`. A2, A3, A5, A6, A7, A9 unchanged. New "Needs a device" row: glyphs and sprite through `asset://` in aeroplane mode (after Phase 6d). |
| Next | Item 13 (Phase 6). Split it first; 6a `MbtilesReader` with a tiny fixture `.mbtiles` is the first PR. |

Notes for the next run:
- **Doc corrections made in #28.** `@maplibre/font-maker` is not on npm. spritezero needs
  mapnik and fails to build on Node 22. Maki is CC0, not Apache-2.0. MapLibre requests glyphs
  per font *stack* (`Noto Sans Regular,Noto Sans Ethiopic Regular/…`, comma, no space), so the
  merged directory must exist offline. `build-glyphs.sh` writes it with `fontnik.composite`.
- **What reached the network from the container this run:** Noto TTFs from
  `raw.githubusercontent.com` (jsDelivr returned 403), and npm, Maven Central (one 429, retry)
  and the GitHub release downloads (ktlint, detekt, kotlinc). The `api.github.com` releases API
  returned 403.
- **Rendering works in the container.** Headless Chromium (`/opt/pw-browsers/chromium-1194`,
  `--use-gl=swiftshader`) ran MapLibre GL JS 5.6.0 from npm against a synthetic `.mbtiles`
  built with `geojson-vt` + `vt-pbf`. Amharic labels, the sprite icon and the language toggle
  all showed. The same harness can check the Phase 6 style in a browser.
- For Phase 6d, bundle only the stack directory from `tools/out/map/fonts/` (2.4 MB; the
  single-font dirs are never requested) plus the sprite (20 KB). Do not commit
  `tools/out/` (gitignored); decide in 6d whether assets are generated in CI or committed.
  Committing them would add about 2.4 MB to the repo and APK.
- Debug APK after #28: **13,332 KiB** (CI log; `tools/` is not in the APK).
- `MeretStyleTest` reads `tools/style/` via the `meret.styleDir` system property, which is set in
  `core/maps/build.gradle.kts` and declared as a task input, so style edits re-run it.
- Truth's `containsAtLeastElementsIn` counts duplicates. Use a set when the order and count
  of "used" items do not matter.

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
