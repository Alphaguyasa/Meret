# NEEDS_APPROVAL.md

Decisions waiting on Alpha. Answer on the item (edit this file, or reply in the Claude app)
and the next run will pick it up. Resolved items move to "Resolved" with the answer.

---

## Open

### A1. ACTION FOR ALPHA: set the daily task to "Automatically approve"
The scheduled run has nobody to approve tool calls. Open the routine in the Claude app
(Code → Routines → "Meret daily lead developer") and set permissions to **Automatically
approve**. Until then each run will stall on its first approval prompt.

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

### A4. Model for the daily task
The models page lists **Claude Fable 5.1** above Opus 5.5. Per the model policy the routine
is set to Fable 5.1. It uses your plan's usage faster than Opus. If runs start hitting usage
limits, say "use opus" and the routine will be switched back. No purchase involved.

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

---

## Needs a device

CI has no phone. These acceptance criteria stay "untested" until someone runs them on real
hardware. Steps are in the linked docs.

| Phase | Criterion | How |
|---|---|---|
| 3 | 100k-point insert ratio and 20k-point read bar | `./gradlew :core:database:connectedDebugAndroidTest` on the S9+ (passed on 2026-08-07 per BUILD_PLAN; re-run after DB changes) |
| 5 | Track survives 30 min, screen off, app swiped away, on a Tecno/Infinix/Itel or Xiaomi | Once roadmap items 3–10 land |

---

## Resolved

(none yet)
