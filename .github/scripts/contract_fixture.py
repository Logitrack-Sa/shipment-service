"""Dobles HTTP de Config y Courier para CI; no son los servicios del Sprint 2."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        status = 200
        if self.path.startswith("/shipment-service/"):
            body = json.loads(Path("/fixture/config.json").read_text())
        elif match := re.fullmatch(r"/api/v1/(internal/)?couriers/([a-f0-9-]{36})/(availability|status)", self.path):
            if not self.headers.get("Authorization", "").startswith("Bearer "):
                status, body = 401, {"code": "UNAUTHORIZED"}
            else:
                identifier = match[2]
                active = identifier.endswith("0001")
                body = {"courierId": identifier, "status": "ACTIVO" if active else "INACTIVO"}
                if match[3] == "availability":
                    body["available"] = active
        else:
            status, body = 404, {"code": "RESOURCE_NOT_FOUND"}
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *_):
        pass


ThreadingHTTPServer(("0.0.0.0", 8765), Handler).serve_forever()
