# NEEDS_APPROVAL.md

Decisions waiting on Alpha. Answer on the item (edit this file, or reply in the Claude app)
and the next run will pick it up. Resolved items move to "Resolved" with the answer.

---

## Open

### A2. GitHub Actions minutes (no cost, but a hard cap)
The repo is **private** on GitHub Free: 2,000 Actions minutes a month. One CI run is about
10–17 minutes, and a day with 2 PRs uses roughly 4–5 runs. That fits, but only just.
- When the minutes run out, GitHub stops running CI. With the default $0 spending limit it
  does **not** bill you. Please confirm the Actions spending limit is $0 under
  github.com → Settings → Billing and plans → Spending limits.
- Options: (a) keep it private and live with the cap (default; CI was cut to one Gradle
  invocation and Markdown-only PRs skip it); (b) make the repo public, which gives unlimited
  free minutes and branch protection, but publishes the code. **Your call; nothing is
  changed until you answer.**

### A3. No branch protection
GitHub Free does not allow branch protection on private repos (API returned 403). Nothing
stops a direct push to main except the policy in CLAUDE.md, which the agent follows. Making
the repo public (A2 b) would enable protection.

### A5. Device-to-device transfer of survey data (decided safe; confirm)
Roadmap item 1 excludes all app data from Android cloud backup **and** device-to-device
transfer, because records identify people. Consequence: when a worker moves to a new phone,
unsynced records do **not** come with it; they must be synced (or peer-synced) first.
I chose the private option. Tell me if NGO deployments need the other one.

### A6. Encrypt the local database (SQLCipher)
Records are personal and health data. Encrypting the Room database with SQLCipher
(BSD-style license, free) protects a lost or stolen phone. Costs: about +3–4 MB APK,
roughly 5–15% slower writes, and a one-time migration of existing plaintext databases.
Key held in Android Keystore. Alternative: rely on Android's file-based encryption (on by
default from Android 10; weaker on old phones). **Recommend: yes, after Phase 5.** Waiting
for your go-ahead (ROADMAP item 14).

### A7. GitHub access for the cloud runs
The routine clones the repo through the Claude GitHub app. To open and merge PRs and push
release tags it also needs write access (the Claude GitHub app installed on
`Alphaguyasa/Meret` with read/write to contents and pull requests). If the first run
reports it could not push or open a PR, install or widen the app at
<https://claude.ai/code> → GitHub settings. The routine has **no** connectors attached
(no Supabase, Gmail, Stripe, Vercel): the creation API attached all of them by default,
and they were removed so an auto-approved run cannot reach payment, email, or production.
Add one back only if a roadmap item needs it.

### A9. Native review of new Amharic strings (no cost, low priority)
Run 3 added nine tracking-notification strings in `feature/tracking/src/main/res/values-am/`
(for example "መስመር እየተቀዳ ነው" for "Recording track", "ለጊዜው አቁም" for "Pause"). They were
written without a native speaker. The same run found that three older Amharic files had
been garbled (mojibake) and fixed them. If someone who reads Amharic can check those
screens, that would help. Nothing is blocked waiting on this.
**Run 4 added 13 more** in `core/location/src/main/res/values-am/strings.xml`: the
step-by-step battery and autostart instructions for each phone brand. Phone menu names
(«Settings», «Auto-start») stay in English inside the Amharic sentence, because the phone's
own menus may be in English. Please check that this reads naturally.
**Run 5 added 21 more** in `feature/settings/src/main/res/values-am/strings.xml`: the
permission onboarding screens (for example "አካባቢን ይፍቀዱ" for "Allow location", "አሁን
አይደለም" for "Not now"). The prose uses the Amharic app name «መሬት»; the older OEM steps use
"Meret". Please say which one the field teams should see.
**Run 6 added 52 more**: 47 for the diagnostics screen in
`feature/settings/src/main/res/values-am/strings.xml` (for example "ምርመራ" for
"Diagnostics", "ምርመራውን ቅዳ" for "Copy diagnostics") and 5 for the after-restart prompt in
`feature/tracking/src/main/res/values-am/strings.xml` ("መስመሩ አልተጠናቀቀም", "መቅዳት ቀጥል"). They
avoid the app name, so the «መሬት»/"Meret" question above does not apply to them.
**Run 13 added 5 more** in `feature/map/src/main/res/values-am/strings.xml`: "አጉላ" (Zoom in),
"አሳንስ" (Zoom out), "ካርታውን ወደ ሰሜን አዙር" (Turn the map north up), and the scale bar units
"%d ሜ" and "%d ኪ.ሜ". Please check that the abbreviations ሜ and ኪ.ሜ are what field teams read.

### A10. Dependabot: turn on alerts, and who merges its PRs (no cost)
Run 7 added `.github/dependabot.yml` (ROADMAP item 11): monthly, minor and patch versions
only, one grouped PR for Gradle and one for GitHub Actions, so at most 2 open. Two things:
1. **Please turn on** github.com → the repo → Settings → Code security → "Dependabot
   alerts" and "Dependabot security updates". Both are free. The file cannot do this, and
   without alerts nothing tells us about a vulnerable library between monthly runs.
2. **Merging.** A squash merge of a Dependabot PR is authored by `dependabot[bot]`, which
   breaks "commits show Alphaguyasa only". Options: (a) the daily run merges them anyway
   when CI is green; (b) the run copies the version change onto its own `claude/` branch,
   which costs a second CI run per update; (c) you merge them yourself. **Default until
   you answer:** the run leaves them open and lists them in the report.

### A11. Glyphs and sprite: committed or built in CI (no cost, has a default)
Phase 6d-2 puts the Ethiopic + Latin glyph stack directory (~2.4 MB of PBFs) and the sprite
(~20 KB) into the app. Options: (a) **commit them** under `core/maps/src/main/assets/map/`.
Builds stay reproducible and CI needs no font downloads, but the repo grows by ~2.4 MB per
font change. (b) **Build them in CI** with `tools/build-glyphs.sh`. The repo stays small,
but every CI run downloads the Noto fonts and runs npm, which adds minutes (A2) and a network
dependency. The APK grows by ~2.4 MB either way. **Default if you don't answer:** (a), with
the OFL licence text beside the fonts.
**Run 10 applied the default:** #37 committed the stack directory and the sprite under
`core/maps/src/main/assets/map/` with `OFL.txt`. Answer only if you want (b) instead.

---

## Needs a device

CI has no phone. These acceptance criteria stay "untested" until someone runs them on real
hardware. Steps are in the linked docs.

| Phase | Criterion | How |
|---|---|---|
| 3 | 100k-point insert ratio and 20k-point read bar | `./gradlew :core:database:connectedDebugAndroidTest` on the S9+ (passed on 2026-08-07 per BUILD_PLAN; re-run after DB changes) |
| 5 | Track survives 30 min, screen off, app swiped away, on a Tecno/Infinix/Itel or Xiaomi | Roadmap items 3–10 are done; ready to test |
| 5 | The OEM autostart deep links open the right screen (Tecno/Infinix/Itel Phone Master, Xiaomi Security). If none opens, the manual steps appear instead | The onboarding screen's «Open phone settings» button (item 8b, done) on a real Tecno/Xiaomi |
| 5 | Onboarding on Android 8, 10, 11+ and 13+: every step appears in order, «Allow all the time» is reachable, and a skipped step does not come back on relaunch | Fresh install, deny then grant each permission |
| 5 | Diagnostics: tap the GPS chip on the map, check every line in Amharic, «Copy diagnostics», paste into WhatsApp. The paste must hold no coordinates | Any phone, after a few minutes of recording |
| 5 | Reboot mid-track: after unlocking, the «Track not finished» prompt appears; «Resume recording» continues the same track in a new segment; with no open track, no prompt | Needs a record button (Phase 7) or adb to start a track |
| 5 | The notification shows Amharic text correctly, and Pause/Stop work from the lock screen | After item 8 (permissions); needs a record button (Phase 7) or adb to start a track |
| 6 | The loopback tile server on a phone: MapLibre loads `tiles.json` and tiles from `127.0.0.1` in aeroplane mode, with no cleartext error, and the server stops when the map leaves the foreground | After 6c (network security config) and 6e (map wrapper) |
| 6 | The map screen draws a sideloaded pack (adb push to `filesDir/packs/{id}.mbtiles` plus a `tile_pack` row, until 6g) in aeroplane mode. Leaving the screen and coming back redraws it, and the 33.4 MB debug APK installs and starts on a low-RAM phone | After 6f-1 (#41); easier after 6g (pack import) |
| 6 | Map controls (6f-2a, #45): + and − zoom one level; rotating with two fingers shows the compass, which points north, and a tap turns the map north up; the scale bar matches a known distance (for example a 100 m road stretch) | After a pack is installed (6g, or the sideload above) |
| 6 | Glyphs and sprite load through `asset://` in aeroplane mode: Amharic labels draw, no boxes, and no request leaves the phone. The glyph path holds a comma and spaces (`Noto Sans Regular,Noto Sans Ethiopic Regular/`) | After Phase 6d bundles `tools/out/map/` into assets |

---

## Resolved

### A8. Release tags (resolved 2026-10-15)
Tag pushes from the cloud routine return HTTP 403, so #7 and #8 merged untagged. Alpha
chose to drop tags: CLAUDE.md's merge policy now records the squash commit SHA in
DAILY_LOG and ROADMAP instead. Roll back with `git revert <sha>` through a PR.

### A4. Model for the daily task (resolved 2026-10-14)
The routine was first set to Fable 5.1 without asking. Alpha said no: there is no budget
for it. The routine now runs **Claude Opus 5.5**, and CLAUDE.md's model policy now says
Opus line only.

### A1. "Automatically approve" for the daily routine (resolved 2026-10-14)
Routines have no approval setting: the edit screen shows only Name, Instructions, Trigger,
Connectors, Behaviour (just "Auto-fix pull requests") and Notifications. The routine's
tools (Bash, Read, Write, Edit, Glob, Grep, WebFetch, WebSearch, PushNotification) are
pre-allowed in its config. Keep **Auto-fix pull requests off**: it would push fixes outside
the 2-attempt cap and outside DAILY_LOG. Keep **Connectors empty**. Turn **Notifications
on**. If a run ever stalls on an approval prompt, the next run (or Alpha) reopens this item
with the run log.
