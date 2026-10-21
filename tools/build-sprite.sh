#!/usr/bin/env bash
# build-sprite.sh — Maki sprite atlas at 1x and 2x for the Meret style
#
# Usage (from tools/):
#   ./build-sprite.sh                 # output to out/map/sprite
#   OUT=/tmp/s ./build-sprite.sh
#
# Icons come from @mapbox/maki (CC0-1.0, installed by npm ci). Which icons go in the atlas
# is listed in style/sprite-icons.txt.

set -euo pipefail
cd "$(dirname "$0")"

OUT="${OUT:-out/map/sprite}"

command -v node >/dev/null 2>&1 || {
  echo "ERROR: node not found. Install Node.js 20+." >&2; exit 1; }

if [[ ! -d node_modules/sharp || ! -d node_modules/@mapbox/maki ]]; then
  echo "ERROR: sharp or @mapbox/maki not installed. Run: (cd tools && npm ci)" >&2; exit 1
fi

echo "==> Building sprite into $OUT"
rm -rf "$OUT"
node sprite/build-sprite.mjs "$OUT"

for f in sprite.png sprite.json sprite@2x.png sprite@2x.json; do
  [[ -s "$OUT/$f" ]] || { echo "ERROR: $OUT/$f was not written." >&2; exit 1; }
done

echo "==> Done. $(du -sh "$OUT" | cut -f1) in $OUT"
