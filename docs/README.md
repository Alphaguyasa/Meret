# Meret — Offline Maps & GPS Logging for Field Teams

**መሬት (meret) = "land" / "ground" in Amharic.**

An Android app for field teams working where there is no network: enumerators, health
extension workers, agricultural extension agents, surveyors, NGO monitoring staff,
utility inspectors.

It works with the aeroplane mode on, all day, on a 4-year-old Tecno.

---

## The problem it solves

Field teams in low-connectivity regions currently run on:

- Paper forms + a separate handheld GPS, transcribed manually at night
- WhatsApp location pins that lose accuracy and context
- Google Maps, which silently degrades to a grey grid off-network
- KoBoToolbox / ODK, which handle forms well but have weak map and track support

None of them answer the question a supervisor actually asks: *"Where did my team
actually go today, what did they record, and which areas have we not covered yet?"*

---

## What it does

**Offline base maps**
- Full vector basemap of Ethiopia (or any region) rendered from a locally stored tile
  pack — no network, no tile server, no API key, no per-map-load billing
- Amharic and Latin label rendering from bundled glyph packs
- Two ways to get tiles onto a device: in-app region download over Wi-Fi, or sideload a
  pre-built `.mbtiles` country pack via USB/SD card for teams that never see good Wi-Fi

**GPS track logging**
- Foreground-service tracking that survives screen-off, Doze, and aggressive OEM task
  killers (HiOS/XOS/MIUI)
- Kalman-filtered, accuracy-gated point stream; raw points preserved, simplified
  geometry derived for display
- Automatic pause detection, manual pause/resume, multi-segment tracks
- Adaptive sampling that trades accuracy for battery based on speed and battery level

**Waypoints and forms**
- Geotagged waypoints with photos, notes, and custom form responses
- Form templates defined as JSON, versioned, distributed with the project
- Photos compressed on-device, queued for upload

**Land area measurement**
- Walk a plot perimeter, get geodesic area in hectares, m², and *timad* (local unit)
- Critical for agricultural extension work; nothing else in this space does it well

**Coverage and assignment**
- Supervisors assign GeoJSON polygon areas to enumerators
- Live coverage overlay: which parts of an assigned area have been walked
- Geofence entry/exit events logged automatically

**Sync**
- Offline-first outbox. Nothing is ever lost because the network was down.
- Syncs to Supabase + PostGIS when a connection appears; Wi-Fi-only mode available
- **Peer sync over Wi-Fi Direct / BLE**: two field devices that meet exchange each
  other's pending data, so a supervisor who returns to town uploads the whole team's day

**Export**
- GPX, KML, GeoJSON, CSV, and a shareable PDF day report

**Supervisor web dashboard** (Next.js, phase 9)
- Map of all team tracks, coverage stats, submission review, CSV export

---

## Why this is a good portfolio project

It demonstrates the things clients actually pay for and that most freelancers cannot do:

| Capability | Why it is rare |
|---|---|
| Reliable background location on hostile OEM Android | Everyone gets this wrong |
| Offline vector map rendering with custom tile pipeline | Requires real GIS knowledge |
| Conflict-free offline-first sync | The hard part of every field app |
| PostGIS spatial queries | Differentiates from CRUD developers |
| Peer-to-peer sync with no infrastructure | Almost nobody ships this |
| Geodesic area computation | Domain depth, not framework depth |

Target rate for this skill set on Upwork: **$40–75/hr**, or **$6–15k** fixed-bid for a
customised deployment.

---

## Tech stack

- **Kotlin**, **Jetpack Compose**, Material 3
- **MapLibre Native Android** (Apache-2.0, no token, no billing)
- **Room** / SQLite, **DataStore**
- **Hilt**, **Coroutines** + **Flow**, **WorkManager**
- **Supabase** (Postgres + PostGIS + Storage + Auth + RLS)
- **Planetiler** for the tile pipeline (offline, one-off build step)
- **Nearby Connections API** for peer sync
- **Next.js 15** + MapLibre GL JS for the supervisor dashboard

---

## Build pack contents

| File | Purpose |
|---|---|
| `CLAUDE.md` | Drop this in the repo root. Claude Code's persistent context. |
| `ARCHITECTURE.md` | Module graph, layering rules, key decisions |
| `DATA_MODEL.md` | Room schema, PostGIS schema, sync protocol |
| `MAPS_OFFLINE.md` | Tile pipeline, style JSON, glyphs, local tile server |
| `LOCATION_ENGINE.md` | Permissions, foreground service, filtering, battery, OEMs |
| `DESIGN_SYSTEM.md` | Tokens, components, field-usability rules |
| `BUILD_PLAN.md` | 14 phases with acceptance criteria |
| `SESSION_PROMPTS.md` | Copy-paste prompts, one per Claude Code session |
| `tools/build-tiles.sh` | Generates the Ethiopia tile pack |
