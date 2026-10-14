# ARCHITECTURE.md

## Layering

```
┌──────────────────────────────────────────────────────┐
│  feature:*   Compose UI + ViewModel + UiState         │
├──────────────────────────────────────────────────────┤
│  domain      Use cases. Pure Kotlin. No Android.      │
├──────────────────────────────────────────────────────┤
│  data        Repositories. Own the Room ⇄ Remote      │
│              decision and the outbox write.           │
├──────────────────────────────────────────────────────┤
│  core:database   core:network   core:location         │
│  core:maps       core:datastore                       │
└──────────────────────────────────────────────────────┘
```

Data flows one way. A ViewModel never touches a DAO. A repository never returns a Room
entity. The remote layer never writes to Room directly — the repository does.

## The single source of truth rule

Room is the only source of truth for the UI. The sync layer writes into Room; the UI
observes Room. This means:

- A newly created waypoint appears instantly, marked `pending`.
- Sync success flips a column, and the UI updates because it is observing a Flow.
- There is no "loading" state for user data. Ever. The data is local.
- A failed sync is a badge, not an error dialog.

## Key decisions

### ADR-001 — Native Android, not a PWA

Alpha's Suq project is a PWA and that is correct for a POS. This is not.

Reliable multi-hour background location with the screen off is not achievable in a web
app on Android. The Geolocation API is suspended when the page is backgrounded, and
there is no equivalent of a foreground service. Additionally we need 300–800 MB of tile
storage, camera access with EXIF control, and Wi-Fi Direct peer transfer.

*Consequence:* iOS is out of scope. That is acceptable — the target market is ~99%
Android.

### ADR-002 — MapLibre Native, not Google Maps or Mapbox

- Google Maps offline is device-scoped, expires after ~30 days, cannot be sideloaded,
  and its SDK cannot render an arbitrary offline region as a first-class map.
- Mapbox requires a token and bills per map load and per offline tile; a client with 200
  field devices would face a real monthly bill.
- MapLibre Native is Apache-2.0, needs no token, renders vector tiles from any source,
  and supports fully local styles.

*Consequence:* we own the tile pipeline. That is more work up front and it is also the
part of this project that is hardest to copy.

### ADR-003 — Two offline tile delivery paths

**Path A — in-app region download.** `OfflineManager` downloads a bounding box at
selected zoom levels from a tile server into MapLibre's own store. Good for a district-
sized area over Wi-Fi. Verify and raise the default tile-count ceiling before use.

**Path B — sideloaded `.mbtiles` pack.** A pre-built country pack is copied to the device
over USB or on an SD card. The app runs a loopback HTTP server (NanoHTTPD) on
`127.0.0.1` that serves tiles out of the `.mbtiles` SQLite file, and the style points at
it. This is the path that matters for teams that never see good Wi-Fi.

Both paths must work. Path B is the differentiator.

### ADR-004 — Raw points are immutable

`track_point` stores exactly what the platform gave us. Filtering, smoothing, and
simplification write to separate columns or separate tables. Reasons: filter parameters
will change, clients will ask "is this real or did your software invent it", and a
survey deliverable may need to be defended.

### ADR-005 — Outbox pattern for sync

A single `outbox` table holds operations, not entities. Each row is
`(id, entity_type, entity_id, op, payload, attempts, next_attempt_at, created_at)`.
`SyncWorker` drains it in `created_at` order with per-entity-type batching.

Why not just a `synced` boolean on each table? Because deletes need to sync too, because
ordering across entity types matters (a waypoint's track must exist first), and because
retry/backoff state has to live somewhere.

### ADR-006 — UUIDv7 primary keys

Time-ordered UUIDs give us: client-side ID generation (no round trip), idempotent
inserts, natural insertion order for B-tree indexes, and sortable IDs for pagination.
Implement a small generator in `:core:common`; do not pull a dependency for 40 lines.

### ADR-007 — Conflict resolution: last-writer-wins, per field group

Field data is append-mostly. True conflicts are rare and come from a supervisor editing a
submission while the enumerator also edits it. Rules:

- **Tracks and track points:** append-only. No conflicts possible. `ON CONFLICT DO NOTHING`.
- **Waypoints and submissions:** last-writer-wins on `updated_at`, where `updated_at` is
  the *server's* clock at the time of the accepted write, and the client sends its
  `base_version`. If `base_version` does not match, the server returns the current row
  and the client stores it as a conflict for the user to resolve. Do not silently discard.
- **Assignments and templates:** server-authoritative, pull-only.

### ADR-008 — Peer sync over Nearby Connections

`P2P_CLUSTER` strategy, reusing the approach from the mesh chat project. Two devices
exchange outbox digests (entity id + hash), then request only what the other lacks.
Payloads are the same JSON the server would receive, so there is one serialisation format.

A device that receives a peer's rows stores them with `origin_device_id` preserved and
adds them to its own outbox. The server deduplicates by primary key. This is why UUIDv7
and idempotency matter — the same row may arrive via three different devices.

*Security:* payloads are signed with a project-scoped key so a device cannot inject data
into a project it does not belong to.

### ADR-009 — Foreground service owns tracking, not the ViewModel

`TrackingService` is the only thing that talks to the `LocationEngine`. It writes to Room.
The UI observes Room and sends commands to the service via intents or a bound interface.
The service can therefore run with no UI alive at all, which is the normal case.

## Concurrency model

- Location fixes arrive on a callback → posted to a `Channel` → consumed by a single
  coroutine that runs the filter chain and writes batched inserts every N points or T
  seconds, whichever comes first. Never one transaction per fix.
- Batch size 20 points / 10 s. On service stop, flush immediately.
- `SyncWorker` uses a mutex so two workers cannot drain the outbox concurrently.

## Error handling

`AppResult<T>` (not `Result<T>`, which collides with `kotlin.Result` — see its KDoc) from `:core:common` with a sealed `AppError` hierarchy:
`AppError.Network`, `AppError.Storage`, `AppError.Permission`, `AppError.Gps`,
`AppError.Conflict`, `AppError.Unexpected`. Map to user-facing strings at the UI layer
only — never build a user message in the data layer.

Crash and non-fatal reporting: a thin `Telemetry` interface in `:core:common` with a
no-op default, so the app can ship without a reporting SDK and clients can bring their
own.
