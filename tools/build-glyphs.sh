#!/usr/bin/env bash
# build-glyphs.sh — glyph PBFs for the Meret style (Noto Sans + Noto Sans Ethiopic)
#
# Usage (from tools/):
#   ./build-glyphs.sh                         # fonts from ./fonts, output to out/map/fonts
#   FONT_DIR=~/fonts OUT=/tmp/g ./build-glyphs.sh
#
# Needs the two TTFs (SIL Open Font License, free). They are not in the repo; download:
#   https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSans/hinted/ttf/NotoSans-Regular.ttf
#   https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSansEthiopic/hinted/ttf/NotoSansEthiopic-Regular.ttf
#
# Output: one directory per font, and one per font stack in style/meret-style.json
# ("Noto Sans Regular,Noto Sans Ethiopic Regular"), which is what MapLibre requests.

set -euo pipefail
cd "$(dirname "$0")"

FONT_DIR="${FONT_DIR:-fonts}"
OUT="${OUT:-out/map/fonts}"

# ---- preflight -------------------------------------------------------------

command -v node >/dev/null 2>&1 || {
  echo "ERROR: node not found. Install Node.js 20+." >&2; exit 1; }

NODE_MAJOR=$(node -p 'process.versions.node.split(".")[0]')
if [[ "$NODE_MAJOR" -lt 20 ]]; then
  echo "ERROR: Node $NODE_MAJOR found, need 20+." >&2; exit 1
fi

if [[ ! -d node_modules/fontnik ]]; then
  echo "ERROR: fontnik not installed. Run: (cd tools && npm ci)" >&2; exit 1
fi

for f in NotoSans-Regular.ttf NotoSansEthiopic-Regular.ttf; do
  if [[ ! -f "$FONT_DIR/$f" ]]; then
    echo "ERROR: $FONT_DIR/$f not found. See the download links at the top of this script," >&2
    echo "or set FONT_DIR to the directory that holds it." >&2
    exit 1
  fi
done

# ---- build -----------------------------------------------------------------

echo "==> Building glyphs into $OUT"
rm -rf "$OUT"
node glyphs/build-glyphs.mjs "$FONT_DIR" "$OUT"

# ---- verify ----------------------------------------------------------------

# Ethiopic is U+1200–U+137F, all inside the 4608-4863 range. A file of a few bytes means
# the font did not cover it and Amharic labels would render as boxes.
STACK="$OUT/Noto Sans Regular,Noto Sans Ethiopic Regular"
for f in "$STACK/0-255.pbf" "$STACK/4608-4863.pbf"; do
  if [[ ! -s "$f" ]] || [[ $(wc -c <"$f") -lt 1000 ]]; then
    echo "ERROR: $f is missing or empty." >&2; exit 1
  fi
done

echo "==> Done. $(du -sh "$OUT" | cut -f1) in $OUT"
