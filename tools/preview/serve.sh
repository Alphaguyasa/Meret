#!/usr/bin/env bash
# serve.sh — preview a tile pack in a browser (see serve.py)
#
# Usage (from tools/):  ./preview/serve.sh data/ethiopia.mbtiles [--port 8080]
set -euo pipefail
[[ $# -ge 1 ]] || { echo "usage: $0 <pack.mbtiles> [--port N]" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "ERROR: python3 not found." >&2; exit 1; }
PACK="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
shift
exec python3 "$(dirname "$0")/serve.py" "$PACK" "$@"
