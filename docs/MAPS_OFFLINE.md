# MAPS_OFFLINE.md

This is the part of the project with the highest ratio of difficulty to visible surface
area. Get it right and everything else is ordinary Android work.

## The four things a map needs offline

A vector map is not one file. It is four, and all four must resolve without a network:

1. **Tiles** — the geometry, as `.pbf` vector tiles
2. **Style JSON** — how to paint the geometry
3. **Glyphs** — rendered font ranges, as `.pbf`, per font per 256-codepoint range
4. **Sprites** — icons, as a PNG atlas plus a JSON index

The classic failure is bundling tiles, forgetting glyphs, testing on Wi-Fi, and shipping
an app whose labels vanish in the field.

---

## Step 1 — Build the tile pack

Use **Planetiler**. It generates an OpenStreetMap vector tile pack for a country in
minutes on a laptop, entirely offline after the initial data download.

```bash
# tools/build-tiles.sh
java -Xmx6g -jar planetiler.jar \
  --download \
  --area=ethiopia \
  --output=data/ethiopia.mbtiles \
  --minzoom=0 --maxzoom=14 \
  --force
```

Notes:
- `--area=ethiopia` pulls the Geofabrik extract automatically.
- Maxzoom 14 with `overzooming` in the renderer gives usable detail to z18 while keeping
  the pack small. Ethiopia at z0–14 lands roughly in the 300–600 MB range; measure yours.
- Output `.mbtiles` (SQLite). `.pmtiles` is also an option — check whether your MapLibre
  Native version supports the `pmtiles://` scheme before choosing it, and fall back to
  `.mbtiles` if not.
- For a district-only pack, pass a bounding box instead of a country to cut size by an
  order of magnitude. Offer both in the tile manager UI.

**Verify the pack before shipping it:**
```bash
sqlite3 data/ethiopia.mbtiles "select name, value from metadata;"
sqlite3 data/ethiopia.mbtiles "select zoom_level, count(*) from tiles group by 1;"
```
Confirm `format=pbf`, and that the `bounds` and `minzoom`/`maxzoom` metadata are present —
the loopback server reads them to serve a TileJSON document.

---

## Step 2 — Build the glyph packs

Ethiopic script is not covered by MapLibre's `LocalIdeographFontFamily`, which only
handles CJK. Amharic labels require generated glyph PBFs.

```bash
cd tools && npm ci && ./build-glyphs.sh   # fontnik; see tools/README.md for the TTFs
```

*(This doc used to say `npm i -g @maplibre/font-maker`. That package is not on npm; corrected
2026-10-21.)*

MapLibre requests glyphs per font **stack**, not per font: for the `text-font` below it
fetches `Noto Sans Regular,Noto Sans Ethiopic Regular/0-255.pbf`, which is the names joined
by a comma with no space. A glyph server merges the fonts on request. The phone has no glyph
server, so `build-glyphs.sh` writes the merged stack directory itself (fontnik `composite`),
plus one directory per single font. The directory name must match the joined stack string
exactly, including spaces.

Ethiopic lives in U+1200–U+137F, so the `4608-4863` range is the one that matters —
but ship all ranges the style might request.

Bundle under `app/src/main/assets/map/fonts/`.

In the style:
```json
"glyphs": "asset://map/fonts/{fontstack}/{range}.pbf"
```

And in each symbol layer:
```json
"text-font": ["Noto Sans Regular", "Noto Sans Ethiopic Regular"]
```

Label field, preferring Amharic where OSM has it:
```json
"text-field": ["coalesce", ["get", "name:am"], ["get", "name"]]
```

Make this a runtime toggle — a setting that switches the expression between
`name:am`-first and `name:en`-first, applied with `setProperties` on the layer.

---

## Step 3 — Sprites

Take a permissively licensed icon set (Maki, CC0-1.0) and build the atlas with
`tools/build-sprite.sh`. *(It was `spritezero`, which needs mapnik and no longer builds on
current Node; the script packs the same format with sharp.)* Output `sprite.png`, `sprite@2x.png`, `sprite.json`, `sprite@2x.json` into
`assets/map/sprite/`.

```json
"sprite": "asset://map/sprite/sprite"
```

MapLibre appends `@2x` and the extension itself. Do not include them in the URL.

---

## Step 4 — The loopback tile server

MapLibre Native does not read `.mbtiles` directly. Serve it locally.

```
:core:maps
  LocalTileServer      NanoHTTPD on 127.0.0.1, ephemeral port
  MbtilesReader        SQLite reads, TMS→XYZ row flip
  StyleProvider        loads style JSON from assets, rewrites the source URL
```

**Endpoints:**
- `GET /{packId}/tiles.json` — TileJSON built from the mbtiles `metadata` table
- `GET /{packId}/{z}/{x}/{y}.pbf` — the tile blob

**The row flip is mandatory.** MBTiles stores rows in TMS order; MapLibre requests XYZ:
```kotlin
val tmsY = (1 shl z) - 1 - y
```
Getting this wrong produces a map that is vertically mirrored per zoom level, which looks
like corrupted data rather than an off-by-one.

**Gzip:** most Planetiler mbtiles store tiles gzipped. Serve with
`Content-Encoding: gzip` rather than decompressing on every request. Detect by sniffing
the first two bytes for `0x1f 0x8b`.

**Lifecycle:** start the server before the `MapView` loads a style, stop it when the last
map screen leaves the foreground. Bind to loopback only, and pick the port at runtime —
never hardcode. Pass the actual port into the style rewrite.

**Style rewrite:** keep `style.json` in assets with a placeholder source URL and
substitute at load time:
```kotlin
val style = assetJson.replace("{{TILE_SERVER}}", "http://127.0.0.1:$port/$packId")
```
Write the result to `cacheDir` and hand MapLibre a `file://` URI. Note that Android 9+
blocks cleartext HTTP by default — add a network security config that permits cleartext
for `127.0.0.1` only. Do not set `cleartextTrafficPermitted="true"` globally.

---

## Step 5 — In-app region download (Path A)

For teams that do have Wi-Fi sometimes. Uses MapLibre's `OfflineManager` against a remote
tile server.

- Define the region from a bounding box the user draws on the map, plus a zoom range.
- **Raise the tile-count ceiling** — the default is a few thousand tiles, far below a
  district at z14. Check the exact setter name in your MapLibre version.
- Show a real progress UI: tiles completed, bytes, and an estimate. Downloads run for
  tens of minutes; make them resumable and survive process death.
- Warn before starting on a metered connection with the estimated size.

---

## Step 6 — Sideloading (Path B)

The path that actually matters in the field.

1. User copies `ethiopia.mbtiles` to `Download/` or an SD card.
2. In the tile manager, they pick the file with the Storage Access Framework.
3. The app validates it: opens the SQLite file, checks `metadata` for `format=pbf`,
   reads bounds and zoom range, counts tiles.
4. It copies (or, on the same volume, moves) it into app storage and writes a `tile_pack`
   row.

For a 500 MB copy: run it in a `TileImportWorker` with a progress notification, verify a
SHA-256 if the pack ships with one, and handle the out-of-space case before starting
rather than half way through.

---

## Rendering the app's own data

Do not use MapLibre's annotation/marker API for tracks — it does not scale past a few
hundred features and it will stutter on a low-end device.

Instead:
- One `GeoJsonSource` for the **active track**, updated with the simplified line every
  ~2 seconds, not on every fix.
- One `GeoJsonSource` for **historical tracks**, loaded lazily for the current viewport.
- One `GeoJsonSource` + `SymbolLayer` for **waypoints**, with clustering enabled at low
  zoom.
- One `FillLayer` + `LineLayer` for **assignment polygons**.
- The **coverage overlay** is a `FillLayer` over a locally computed grid — bucket walked
  points into ~50 m cells, emit cells as a GeoJSON feature collection, recompute on track
  completion rather than continuously.

Layer ordering: insert your layers *below* the basemap's symbol layers so place labels
stay readable on top of track lines. Use `addLayerBelow(layer, "place-label-city")` or
whatever the first symbol layer id in your style is.

## Compose interop

MapLibre has no first-class Compose API. Wrap it:

```
MapLibreMap(modifier, state) →
  AndroidView(factory = { MapView(it) }, update = { ... })
  + DisposableEffect forwarding onStart/onResume/onPause/onStop/onDestroy/onLowMemory
  + rememberSaveable camera position
```

`MapView` lifecycle methods must all be forwarded or you will leak the renderer and crash
on rotation. Hold the `MapLibreMap` instance in a `remember`, never in the ViewModel.

## Performance targets

| Metric | Target |
|---|---|
| Cold start to first painted tile | < 2.0 s on a 4 GB Tecno |
| Pan/zoom frame time | < 16 ms p90 |
| Active-track source update | ≤ 1 per 2 s |
| Memory, map screen | < 250 MB |
| Tile server response, cached | < 15 ms |

Measure with Macrobenchmark and the on-device Frame Timing overlay, not by eye.
