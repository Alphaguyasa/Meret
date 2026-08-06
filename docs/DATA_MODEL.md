# DATA_MODEL.md

## Local schema (Room)

All ids are `TEXT` UUIDv7. All timestamps are `INTEGER` epoch millis UTC unless noted.

### `project`
| column | type | notes |
|---|---|---|
| id | TEXT PK | |
| org_id | TEXT | |
| name | TEXT | |
| description | TEXT? | |
| default_form_template_id | TEXT? | |
| tile_pack_id | TEXT? | which offline pack this project expects |
| starts_at / ends_at | INTEGER? | |
| updated_at | INTEGER | server clock |
| is_archived | INTEGER | |

### `track`
| column | type | notes |
|---|---|---|
| id | TEXT PK | |
| project_id | TEXT FK | indexed |
| device_id | TEXT | |
| user_id | TEXT | |
| name | TEXT? | user label, defaults to date + project |
| started_at | INTEGER | |
| ended_at | INTEGER? | null while active |
| status | TEXT | `active` `paused` `completed` `discarded` |
| point_count | INTEGER | denormalised, updated on batch write |
| distance_m | REAL | running total, filtered geometry |
| duration_moving_ms | INTEGER | excludes auto-pause |
| bbox_min_lat/min_lon/max_lat/max_lon | REAL | for map fitting and spatial filtering |
| simplified_geojson | TEXT? | Douglas–Peucker output, regenerated on completion |
| purpose | TEXT? | `survey` `transit` `measurement` |
| notes | TEXT? | |
| sync_state | TEXT | `pending` `syncing` `synced` `conflict` |
| updated_at | INTEGER | |

Index: `(project_id, started_at DESC)`, `(sync_state)`.

### `track_point`
Raw and immutable.

| column | type | notes |
|---|---|---|
| id | TEXT PK | |
| track_id | TEXT FK | indexed |
| segment_index | INTEGER | increments on each pause/resume |
| seq | INTEGER | monotonic within track, assigned at write |
| lat / lon | REAL | as reported |
| altitude_m | REAL? | |
| accuracy_m | REAL | horizontal |
| vertical_accuracy_m | REAL? | API 26+ |
| bearing_deg | REAL? | |
| speed_mps | REAL? | |
| speed_accuracy_mps | REAL? | |
| provider | TEXT | `fused` `gps` `network` `replay` |
| satellites_used | INTEGER? | from GnssStatus |
| recorded_at | INTEGER | wall clock, may be wrong |
| elapsed_realtime_nanos | INTEGER | **use this for ordering** |
| is_mock | INTEGER | |
| filtered_lat / filtered_lon | REAL? | Kalman output, nullable |
| filter_variance | REAL? | |
| is_outlier | INTEGER | rejected by gate, kept for audit |

Index: `(track_id, seq)`. This table will hold millions of rows — keep it narrow, do not
add a `sync_state` per point. Points sync as part of a track chunk.

### `waypoint`
| column | type | notes |
|---|---|---|
| id | TEXT PK | |
| project_id / track_id? | TEXT | track_id null if standalone |
| lat / lon / accuracy_m / altitude_m | REAL | |
| recorded_at | INTEGER | |
| category | TEXT? | |
| title / notes | TEXT? | |
| form_template_id / form_version | TEXT? / INTEGER? | |
| form_data | TEXT? | JSON object |
| base_version | INTEGER | for conflict detection |
| sync_state | TEXT | |
| updated_at | INTEGER | |

### `waypoint_photo`
| column | type | notes |
|---|---|---|
| id | TEXT PK | |
| waypoint_id | TEXT FK | |
| local_path | TEXT | app-private storage |
| remote_path | TEXT? | Supabase Storage key |
| width / height / bytes | INTEGER | after compression |
| sha256 | TEXT | dedup + integrity |
| captured_at | INTEGER | |
| lat / lon | REAL? | fix at capture time |
| upload_state | TEXT | `pending` `uploading` `uploaded` `failed` |
| upload_attempts | INTEGER | |

### `measurement`
Area/distance measurement sessions.

| column | type |
|---|---|
| id, project_id, name | TEXT |
| kind | TEXT — `area` `distance` |
| vertices_geojson | TEXT |
| area_m2 / perimeter_m / distance_m | REAL? |
| method | TEXT — `walked` `tapped` |
| recorded_at, updated_at, sync_state | |

### `assignment`
| column | type |
|---|---|
| id, project_id, assigned_user_id | TEXT |
| name | TEXT |
| polygon_geojson | TEXT |
| due_at | INTEGER? |
| status | TEXT — `open` `in_progress` `done` |
| coverage_pct | REAL — computed locally |

### `form_template`
`id`, `project_id`, `version`, `name`, `schema_json`, `updated_at`. Pull-only.

### `tile_pack`
`id`, `name`, `region_code`, `path`, `bytes`, `min_zoom`, `max_zoom`,
`bbox_*`, `source` (`downloaded` | `sideloaded`), `installed_at`, `style_id`.

### `outbox`
| column | type | notes |
|---|---|---|
| id | TEXT PK | |
| entity_type | TEXT | `track` `track_points` `waypoint` `photo` `measurement` |
| entity_id | TEXT | |
| op | TEXT | `upsert` `delete` |
| payload | TEXT | JSON, exactly what goes on the wire |
| payload_sha256 | TEXT | peer-sync digest |
| priority | INTEGER | lower drains first; tracks before waypoints |
| attempts | INTEGER | |
| last_error | TEXT? | |
| next_attempt_at | INTEGER | |
| created_at | INTEGER | |

Index: `(next_attempt_at, priority, created_at)`.

### `sync_conflict`
`id`, `entity_type`, `entity_id`, `local_payload`, `remote_payload`, `detected_at`,
`resolved_at?`, `resolution` (`local` | `remote` | `merged`).

---

## Remote schema (Supabase / PostGIS)

```sql
create extension if not exists postgis;

create table public.tracks (
  id              uuid primary key,
  org_id          uuid not null,
  project_id      uuid not null references projects(id),
  user_id         uuid not null references auth.users(id),
  device_id       text not null,
  name            text,
  started_at      timestamptz not null,
  ended_at        timestamptz,
  status          text not null,
  point_count     int  not null default 0,
  distance_m      double precision not null default 0,
  duration_moving_ms bigint not null default 0,
  purpose         text,
  notes           text,
  geom            geography(LineString, 4326),  -- built server-side
  bbox            geography(Polygon, 4326),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index tracks_geom_idx on tracks using gist (geom);
create index tracks_project_started_idx on tracks (project_id, started_at desc);

create table public.track_points (
  id             uuid primary key,
  track_id       uuid not null references tracks(id) on delete cascade,
  seq            int  not null,
  segment_index  int  not null default 0,
  geom           geography(Point, 4326) not null,
  altitude_m     double precision,
  accuracy_m     double precision,
  bearing_deg    double precision,
  speed_mps      double precision,
  provider       text,
  recorded_at    timestamptz not null,
  unique (track_id, seq)
);
create index track_points_track_idx on track_points (track_id, seq);
create index track_points_geom_idx on track_points using gist (geom);
```

Partition `track_points` by month if a deployment exceeds ~50M rows.

**Bulk insert RPC** — one round trip per chunk, idempotent:

```sql
create or replace function public.ingest_track_points(p_track_id uuid, p_points jsonb)
returns int language plpgsql security definer as $$
declare inserted int;
begin
  if not exists (
    select 1 from tracks t where t.id = p_track_id and t.user_id = auth.uid()
  ) then
    raise exception 'not authorised for track %', p_track_id;
  end if;

  insert into track_points (id, track_id, seq, segment_index, geom, altitude_m,
                            accuracy_m, bearing_deg, speed_mps, provider, recorded_at)
  select (e->>'id')::uuid,
         p_track_id,
         (e->>'seq')::int,
         coalesce((e->>'segment_index')::int, 0),
         ST_SetSRID(ST_MakePoint((e->>'lon')::float8, (e->>'lat')::float8), 4326)::geography,
         (e->>'altitude_m')::float8,
         (e->>'accuracy_m')::float8,
         (e->>'bearing_deg')::float8,
         (e->>'speed_mps')::float8,
         e->>'provider',
         to_timestamp((e->>'recorded_at')::bigint / 1000.0)
  from jsonb_array_elements(p_points) e
  on conflict (id) do nothing;

  get diagnostics inserted = row_count;
  return inserted;
end $$;
```

**Track geometry rebuild** — call after the last chunk of a track:

```sql
create or replace function public.finalise_track(p_track_id uuid)
returns void language sql security definer as $$
  update tracks t set
    geom = sub.line,
    bbox = ST_Envelope(sub.line::geometry)::geography,
    point_count = sub.n,
    distance_m = ST_Length(sub.line),
    updated_at = now()
  from (
    select ST_MakeLine(p.geom::geometry order by p.seq)::geography as line,
           count(*) as n
    from track_points p where p.track_id = p_track_id
  ) sub
  where t.id = p_track_id and sub.n >= 2;
$$;
```

**RLS sketch** — enumerator sees own rows, supervisor sees the org:

```sql
alter table tracks enable row level security;

create policy tracks_own on tracks
  for all using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy tracks_supervisor on tracks
  for select using (
    exists (select 1 from memberships m
            where m.user_id = auth.uid()
              and m.org_id = tracks.org_id
              and m.role in ('supervisor','admin'))
  );
```

Write the equivalent for every table. Test RLS with a second account before shipping —
an RLS policy that is never tested is an RLS policy that does not work.

---

## Sync protocol

### Push
1. `SyncWorker` acquires a mutex, reads up to 200 outbox rows where
   `next_attempt_at <= now()`, ordered by `(priority, created_at)`.
2. Groups by `entity_type`. Track points are chunked at 500 per request.
3. Sends. On `2xx`, delete the outbox row and set `sync_state = 'synced'`.
4. On `409`/version mismatch, write a `sync_conflict` row and mark the entity `conflict`.
5. On transient failure, `attempts++`, `next_attempt_at = now + min(2^attempts * 30s, 6h)`
   with ±20% jitter.
6. After the final chunk of a completed track, call `finalise_track`.

### Pull
Delta by `updated_at` cursor per table, stored in DataStore. Pull-only tables:
`projects`, `form_templates`, `assignments`, `memberships`. Page at 500 rows.

### Photos
Separate `PhotoUploadWorker`, constrained to unmetered network by default. Compress to
max 1600px long edge, JPEG q80, strip EXIF except the GPS block. Upload to
`photos/{org_id}/{project_id}/{waypoint_id}/{photo_id}.jpg`. Retry with the same backoff.

### Peer sync
1. Advertise/discover with `P2P_CLUSTER`, service id scoped to `org_id`.
2. On connect, exchange a digest: `[(entity_type, entity_id, payload_sha256)]` for all
   outbox rows plus recently synced rows within a configurable window.
3. Each side requests entries it lacks, receives the payload, validates the signature and
   hash, inserts into its own Room tables and outbox.
4. Cap a session at 50 MB and 5 minutes; photos only over Wi-Fi Direct, never BLE.

Because every id is client-generated and every server write is idempotent, a row arriving
from three devices results in one server row.
