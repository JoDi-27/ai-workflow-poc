"""Throwaway OTLP/HTTP JSON receiver for spike 006.

Listens on 127.0.0.1:4318 and appends every POST to <out>/<signal>.jsonl as
{"ts", "path", "auth", "body"}, where auth only says whether an Authorization
header arrived and which scheme it used. Run: python3 receiver.py <out-dir>
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "capture")
OUT.mkdir(parents=True, exist_ok=True)


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        signal = self.path.rstrip("/").rsplit("/", 1)[-1] or "unknown"
        auth = self.headers.get("Authorization")
        try:
            body = json.loads(raw)
        except ValueError:
            body = {"non_json_bytes": len(raw), "content_type": self.headers.get("Content-Type")}
        record = {
            "ts": time.time(),
            "path": self.path,
            "auth": auth.split(" ", 1)[0] if auth else None,
            "body": body,
        }
        with open(OUT / f"{signal}.jsonl", "a") as f:
            f.write(json.dumps(record) + "\n")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b"{}")

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 4318), Handler).serve_forever()
