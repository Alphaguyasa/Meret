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

---

## Needs a device

CI has no phone. These acceptance criteria stay "untested" until someone runs them on real
hardware. Steps are in the linked docs.

| Phase | Criterion | How |
|---|---|---|
| 3 | 100k-point insert ratio and 20k-point read bar | `./gradlew :core:database:connectedDebugAndroidTest` on the S9+ (passed on 2026-08-07 per BUILD_PLAN; re-run after DB changes) |
| 5 | Track survives 30 min, screen off, app swiped away, on a Tecno/Infinix/Itel or Xiaomi | Once roadmap items 3–10 land (3–6 done, 7–10 left) |
| 5 | The notification shows Amharic text correctly, and Pause/Stop work from the lock screen | After item 8 (permissions); needs a record button (Phase 7) or adb to start a track |

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
