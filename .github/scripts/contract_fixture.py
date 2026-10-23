"""Dobles HTTP de Config y Courier para CI; no son los servicios del Sprint 2."""
import hashlib
import hmac
import time
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
            bearer = self.headers.get("Authorization", "").removeprefix("Bearer ")
            expected = Path("/fixture/token.sha256").read_text().strip()
            if not hmac.compare_digest(hashlib.sha256(bearer.encode()).hexdigest(), expected):
                status, body = 401, {"code": "UNAUTHORIZED"}
            else:
                identifier = match[2]
                active = identifier.endswith("0001")
                body = {"courierId": identifier, "status": "ACTIVO" if active else "INACTIVO"}
                if match[3] == "availability":
                    body["available"] = active
                scenario = int(identifier[-4:])
                if scenario in (3, 4, 5, 6, 9):
                    status = {3: 404, 4: 401, 5: 403, 6: 503, 9: 422}[scenario]
                    body = {"code": "DETERMINISTIC_TEST_FAILURE"}
                elif scenario == 7:
                    body = {} # Payload inválido.
                elif scenario == 8:
                    time.sleep(2) # Supera read-timeout=1s, sin reintento.
        else:
            status, body = 404, {"code": "RESOURCE_NOT_FOUND"}
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        try:
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def log_message(self, *_):
        pass


ThreadingHTTPServer(("0.0.0.0", 8765), Handler).serve_forever()
