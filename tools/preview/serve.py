#!/usr/bin/env python3
"""Desk preview for a Meret tile pack. Python 3 standard library only.

    python3 preview/serve.py data/ethiopia.mbtiles [--port 8080] [--map out/map]

Serves, on 127.0.0.1 only:
  /                         index.html (MapLibre GL JS)
  /tiles.json               TileJSON built from the pack's metadata table
  /tiles/{z}/{x}/{y}.pbf    tiles, TMS row flipped to XYZ, gzip passed through as stored
  /style.json               tools/style/meret-style.json with {{TILE_SERVER}} and
                            asset://map/ rewritten to this server
  /map/...                  the built glyphs and sprite (build-glyphs.sh, build-sprite.sh)

This mirrors what the app's loopback server will do (MAPS_OFFLINE.md step 4), so a pack
that looks right here has the right metadata, tiles, glyphs and sprite. It does not prove
asset:// loading on a phone; that still needs aeroplane mode on a device.
"""

import argparse
import json
import mimetypes
import os
import re
import sqlite3
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlsplit

TOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TILE_PATH = re.compile(r"^/tiles/(\d+)/(\d+)/(\d+)\.pbf$")


def tilejson(db, base):
    meta = dict(db.execute("select name, value from metadata"))
    doc = {
        "tilejson": "3.0.0",
        "name": meta.get("name", "meret"),
        "scheme": "xyz",
        "tiles": [base + "/tiles/{z}/{x}/{y}.pbf"],
        "minzoom": int(meta.get("minzoom", 0)),
        "maxzoom": int(meta.get("maxzoom", 14)),
    }
    if "bounds" in meta:
        doc["bounds"] = [float(v) for v in meta["bounds"].split(",")]
    if "center" in meta:
        doc["center"] = [float(v) for v in meta["center"].split(",")]
    if "attribution" in meta:
        doc["attribution"] = meta["attribution"]
    # Planetiler stores the vector layer list in metadata.json; MapLibre does not need it,
    # but keeping it makes the TileJSON complete.
    if "json" in meta:
        doc.update({k: v for k, v in json.loads(meta["json"]).items() if k == "vector_layers"})
    return doc


def tile(db, z, x, y):
    # MBTiles rows are TMS: row 0 is the south edge. MapLibre asks in XYZ.
    row = (1 << z) - 1 - y
    hit = db.execute(
        "select tile_data from tiles where zoom_level=? and tile_column=? and tile_row=?",
        (z, x, row),
    ).fetchone()
    return hit[0] if hit else None


def make_handler(mbtiles, map_dir):
    style_path = os.path.join(TOOLS, "style", "meret-style.json")
    index_path = os.path.join(TOOLS, "preview", "index.html")
    map_root = os.path.realpath(map_dir)

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            path = unquote(urlsplit(self.path).path)
            base = "http://" + self.headers.get("Host", "127.0.0.1")
            db = sqlite3.connect("file:%s?mode=ro" % mbtiles, uri=True)
            try:
                if path in ("/", "/index.html"):
                    self.send_file(index_path)
                elif path == "/tiles.json":
                    self.send(200, "application/json", json.dumps(tilejson(db, base)).encode())
                elif path == "/style.json":
                    with open(style_path, encoding="utf-8") as f:
                        style = f.read()
                    style = style.replace("{{TILE_SERVER}}", base + "/tiles.json")
                    style = style.replace("asset://map/", base + "/map/")
                    self.send(200, "application/json", style.encode())
                elif TILE_PATH.match(path):
                    z, x, y = (int(v) for v in TILE_PATH.match(path).groups())
                    data = tile(db, z, x, y)
                    if data is None:
                        self.send(204, "application/x-protobuf", b"")
                    else:
                        gzip = data[:2] == b"\x1f\x8b"
                        self.send(200, "application/x-protobuf", data, gzip)
                elif path.startswith("/map/"):
                    target = os.path.realpath(os.path.join(map_root, path[len("/map/"):]))
                    if os.path.commonpath([map_root, target]) != map_root:
                        self.send(403, "text/plain", b"forbidden")
                    else:
                        self.send_file(target)
                else:
                    self.send(404, "text/plain", b"not found")
            finally:
                db.close()

        def send_file(self, path):
            if not os.path.isfile(path):
                self.send(404, "text/plain", b"not found")
                return
            with open(path, "rb") as f:
                data = f.read()
            kind = "application/x-protobuf" if path.endswith(".pbf") else None
            self.send(200, kind or mimetypes.guess_type(path)[0] or "application/octet-stream", data)

        def send(self, status, kind, body, gzip=False):
            self.send_response(status)
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            if gzip:
                self.send_header("Content-Encoding", "gzip")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt, *args):
            if os.environ.get("VERBOSE"):
                super().log_message(fmt, *args)

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("mbtiles")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--map", default=os.path.join(TOOLS, "out", "map"),
                        help="directory holding fonts/ and sprite/ (default: tools/out/map)")
    args = parser.parse_args()

    if not os.path.isfile(args.mbtiles):
        sys.exit("ERROR: %s not found" % args.mbtiles)
    for sub in ("fonts", "sprite"):
        if not os.path.isdir(os.path.join(args.map, sub)):
            print("WARNING: %s/%s missing: labels or icons will not show. "
                  "Run build-glyphs.sh and build-sprite.sh." % (args.map, sub), file=sys.stderr)

    server = ThreadingHTTPServer(("127.0.0.1", args.port), make_handler(args.mbtiles, args.map))
    print("Preview at http://127.0.0.1:%d/  (Ctrl-C to stop)" % args.port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
