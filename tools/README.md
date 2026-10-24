# tools/ — offline map pack

Builds the four things the map needs without a network (`docs/MAPS_OFFLINE.md`): tiles,
style, glyphs, sprite. None of this is part of the app's Gradle build, and nothing here
costs money. Everything is free and open: OpenStreetMap data (ODbL), Planetiler
(Apache-2.0), Noto fonts (OFL), Maki icons (CC0), fontnik (BSD), sharp (Apache-2.0).

| Path | What |
|---|---|
| `build-tiles.sh` | Planetiler → `data/<area>.mbtiles` (OpenMapTiles schema) |
| `build-glyphs.sh` | Noto TTFs → `out/map/fonts/<font stack>/<range>.pbf` |
| `build-sprite.sh` | Maki SVGs → `out/map/sprite/sprite{,@2x}.{png,json}` |
| `style/meret-style.json` | The MapLibre style. `{{TILE_SERVER}}` is replaced at load time |
| `style/fonts.json` | Font name → TTF file, for `build-glyphs.sh` |
| `style/sprite-icons.txt` | The Maki icons packed into the sprite |
| `preview/` | `serve.py` (Python stdlib) + `index.html` (MapLibre GL JS): check a pack in a browser |

`MeretStyleTest` in `:core:maps` runs in CI and checks the style against `fonts.json` and
`sprite-icons.txt`: no `http` anywhere, glyphs and sprite on `asset://`, every font stack
buildable and ending in Ethiopic, every label `name:am` first, every icon packed.

## From zero to a verified pack

Needs Java 21+, Node 20+, Python 3, and `sqlite3` for the checks. Run from `tools/`.

```bash
cd tools
npm ci                                    # fontnik, sharp, @mapbox/maki (exact versions)

# 1. Tiles. A district first: minutes, not hours.
curl -LO https://github.com/onthegomap/planetiler/releases/latest/download/planetiler.jar
./build-tiles.sh --bbox 38.6,8.8,38.9,9.1        # → data/ethiopia-district.mbtiles
# ./build-tiles.sh                               # all of Ethiopia, ~20 GB scratch space

# 2. Glyphs. The TTFs are not in the repo; download them into tools/fonts/ (gitignored).
mkdir -p fonts
curl -L -o fonts/NotoSans-Regular.ttf \
  https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSans/hinted/ttf/NotoSans-Regular.ttf
curl -L -o fonts/NotoSansEthiopic-Regular.ttf \
  https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSansEthiopic/hinted/ttf/NotoSansEthiopic-Regular.ttf
./build-glyphs.sh                                # → out/map/fonts/  (~2 s)

# 3. Sprite.
./build-sprite.sh                                # → out/map/sprite/

# 4. Look at it, BEFORE touching Android.
./preview/serve.sh data/ethiopia-district.mbtiles
# open http://127.0.0.1:8080/#13/9.0/38.75
```

In the preview, check that Amharic labels show as Ethiopic script and not boxes, that the
«English names first» box switches the labels, and that the status line in the corner
shows no `Error:` or `Missing icon:`. The page fetches MapLibre GL JS from unpkg once;
everything it draws comes from `serve.py` on 127.0.0.1.

### Verification queries

```bash
P=data/ethiopia-district.mbtiles
sqlite3 $P "select name, value from metadata;"     # format=pbf; bounds, center, minzoom, maxzoom present
sqlite3 $P "select zoom_level, count(*) from tiles group by 1 order by 1;"   # every zoom 0..14
sqlite3 $P "select hex(substr(tile_data,1,2)) from tiles limit 1;"           # 1F8B = gzip, served as stored
sqlite3 $P "select json_extract(value,'$.vector_layers[*].id') from metadata where name='json';"
```

## Refreshing the app's copy

The app ships the glyph stack and the sprite as committed assets in `:core:maps`
(NEEDS_APPROVAL A11, default (a)). After a font, `fonts.json` or `sprite-icons.txt`
change, rebuild and copy:

```bash
./build-glyphs.sh && ./build-sprite.sh
A=../core/maps/src/main/assets/map
rm -rf "$A/fonts/Noto Sans Regular,Noto Sans Ethiopic Regular" "$A/sprite"
cp -r "out/map/fonts/Noto Sans Regular,Noto Sans Ethiopic Regular" "$A/fonts/"
cp -r out/map/sprite "$A/sprite"
```

A new font stack in the style needs its directory copied too, and its font's licence in
`$A/fonts/OFL.txt`. `MapAssetsTest` fails if a stack, a range or an icon is missing.

## Things that are easy to get wrong

- **Font stacks, not fonts.** MapLibre requests
  `Noto Sans Regular,Noto Sans Ethiopic Regular/4608-4863.pbf`: the stack's names joined
  by a comma, no space. With no glyph server on the phone, `build-glyphs.sh` writes that
  merged directory itself (fontnik `composite`), plus one directory per single font.
  Only the stack directories are requested by our style. Bundling just those (about 2.4 MB)
  is enough for the app.
- **Ethiopic is U+1200–U+137F**, all in range `4608-4863`. `build-glyphs.sh` fails if
  that file is empty in the stack directory.
- **Amharic names.** The style reads `name:am`, then `name`. Planetiler's OpenMapTiles
  profile writes `name:<lang>` tags; if Amharic labels are missing but `name` shows, check
  that the build kept `am` (Planetiler's `--languages` option).
- **Spritezero no longer builds** (it needs mapnik, which has no binaries for current
  Node), so `sprite/build-sprite.mjs` packs the atlas with sharp. Its output is the same
  format: `{name: {x, y, width, height, pixelRatio}}`.
- **Layer ids are API.** `metadata["meret:first-symbol-layer"]` (`label-water`) is the
  layer the app inserts its own layers below, so tracks never cover a label. Everything
  from there up is a symbol layer; the test checks it.
- **Desk ≠ field.** The preview serves glyphs over HTTP. On a phone they load through
  `asset://`, with a comma and spaces in the path. Check that in aeroplane mode on a device
  before calling the map done (NEEDS_APPROVAL, "Needs a device").
