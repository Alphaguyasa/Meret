#!/usr/bin/env bash
# build-tiles.sh — generate an offline vector tile pack for Meret
#
# Usage:
#   ./build-tiles.sh                          # full Ethiopia, z0-14
#   ./build-tiles.sh --area kenya             # another country
#   ./build-tiles.sh --bbox 38.6,8.8,38.9,9.1 # district-sized pack (Addis)
#
# Output: data/<name>.mbtiles

set -euo pipefail

AREA="ethiopia"
BBOX=""
MINZOOM=0
MAXZOOM=14
MEM="6g"
PLANETILER_JAR="${PLANETILER_JAR:-planetiler.jar}"
OUT_DIR="data"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --area)    AREA="$2"; shift 2 ;;
    --bbox)    BBOX="$2"; shift 2 ;;
    --maxzoom) MAXZOOM="$2"; shift 2 ;;
    --mem)     MEM="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done

# ---- preflight -------------------------------------------------------------

command -v java >/dev/null 2>&1 || {
  echo "ERROR: java not found. Planetiler needs Java 21+." >&2; exit 1; }

JAVA_MAJOR=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [[ "$JAVA_MAJOR" -lt 21 ]]; then
  echo "ERROR: Java $JAVA_MAJOR found, Planetiler needs 21+." >&2; exit 1
fi

if [[ ! -f "$PLANETILER_JAR" ]]; then
  echo "ERROR: $PLANETILER_JAR not found." >&2
  echo "Download the latest release from https://github.com/onthegomap/planetiler/releases" >&2
  echo "or set PLANETILER_JAR to its path." >&2
  exit 1
fi

AVAIL_GB=$(df -Pg . 2>/dev/null | awk 'NR==2{print $4}' || df -P . | awk 'NR==2{print int($4/1048576)}')
if [[ -n "${AVAIL_GB:-}" && "$AVAIL_GB" -lt 20 ]]; then
  echo "WARNING: only ${AVAIL_GB}GB free. A country build wants ~20GB of scratch space."
  read -rp "Continue? [y/N] " ok; [[ "$ok" == "y" ]] || exit 1
fi

mkdir -p "$OUT_DIR"

if [[ -n "$BBOX" ]]; then
  NAME="${AREA}-district"
  BBOX_ARG=(--bounds="$BBOX")
else
  NAME="$AREA"
  BBOX_ARG=()
fi

OUT="$OUT_DIR/${NAME}.mbtiles"

# ---- build -----------------------------------------------------------------

echo "==> Building $OUT  (z$MINZOOM-$MAXZOOM, ${MEM} heap)"
START=$(date +%s)

java "-Xmx${MEM}" -jar "$PLANETILER_JAR" \
  --download \
  --area="$AREA" \
  "${BBOX_ARG[@]}" \
  --minzoom="$MINZOOM" \
  --maxzoom="$MAXZOOM" \
  --output="$OUT" \
  --force

ELAPSED=$(( $(date +%s) - START ))

# ---- verify ----------------------------------------------------------------

echo
echo "==> Verifying"

if ! command -v sqlite3 >/dev/null 2>&1; then
  echo "sqlite3 not installed — skipping verification. Install it and run:"
  echo "  sqlite3 $OUT \"select name,value from metadata;\""
else
  FORMAT=$(sqlite3 "$OUT" "select value from metadata where name='format';")
  if [[ "$FORMAT" != "pbf" ]]; then
    echo "ERROR: format is '$FORMAT', expected 'pbf'." >&2; exit 1
  fi

  echo "--- metadata ---"
  sqlite3 -column -header "$OUT" \
    "select name, substr(value,1,60) as value from metadata
     where name in ('name','format','bounds','center','minzoom','maxzoom');"

  echo
  echo "--- tiles per zoom ---"
  sqlite3 -column -header "$OUT" \
    "select zoom_level, count(*) as tiles from tiles group by 1 order by 1;"
fi

SIZE=$(du -h "$OUT" | cut -f1)
echo
echo "==> Done in ${ELAPSED}s.  $OUT  ($SIZE)"
echo
echo "Next:"
echo "  1. ./build-glyphs.sh     # Noto Sans + Noto Sans Ethiopic glyph PBFs"
echo "  2. ./build-sprite.sh     # Maki icon atlas"
echo "  3. cd preview && ./serve.sh $OUT   # verify in a browser BEFORE touching Android"
