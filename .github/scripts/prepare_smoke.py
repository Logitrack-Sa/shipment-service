"""Genera material RSA descartable; la clave privada queda solo en RUNNER_TEMP."""
import base64
import hashlib
import time
import uuid
import json
import os
from pathlib import Path
import subprocess

root = Path(os.environ["SMOKE_FIXTURE_DIR"])
root.mkdir(parents=True, exist_ok=True)
subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                "-out", str(root / "private.pem")], check=True, capture_output=True)
(root / "private.pem").chmod(0o600)
subprocess.run(["openssl", "pkey", "-in", str(root / "private.pem"), "-pubout", "-out", str(root / "public.pem")],
               check=True, capture_output=True)
# El receptor simulado compara el hash del JWT exacto; no recibe ninguna clave privada.
encode = lambda data: base64.urlsafe_b64encode(json.dumps(data).encode()).rstrip(b"=")
message = encode({"alg": "RS256", "kid": "ci-ephemeral"}) + b"." + encode({
    "sub": str(uuid.uuid4()), "roles": ["ADMIN"], "iss": "https://issuer.test",
    "aud": ["shipment-service", "courier-service"], "iat": int(time.time()), "exp": int(time.time()) + 1800})
signature = subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(root / "private.pem")], input=message,
                           capture_output=True, check=True).stdout
token = message + b"." + base64.urlsafe_b64encode(signature).rstrip(b"=")
(root / "token.txt").write_bytes(token)
(root / "token.txt").chmod(0o600)
(root / "token.sha256").write_text(hashlib.sha256(token).hexdigest(), encoding="ascii")
source = {
    "server.port": 8081,
    "spring.datasource.url": "${DB_URL}",
    "spring.datasource.username": "${DB_USERNAME}",
    "spring.datasource.password": "${DB_PASSWORD}",
    "logitrack.fee.base": 1000, "logitrack.fee.price-per-kg": 250, "logitrack.fee.volumetric-divisor": 5000,
    "logitrack.courier.base-url": "http://contract-fixture:8765",
    "logitrack.courier.connect-timeout": "500ms", "logitrack.courier.read-timeout": "1s",
    "logitrack.security.public-key-location": "file:/run/secrets/jwt-public.pem",
    "logitrack.security.key-id": "ci-ephemeral",
    "logitrack.security.issuer": "https://issuer.test",
    "logitrack.security.audience": "shipment-service",
    "logging.level.com.logitrack": "INFO",
}
(root / "config.json").write_text(json.dumps({"name": "shipment-service", "profiles": ["dev"],
    "propertySources": [{"name": "isolated-ci-fixture", "source": source}]}), encoding="utf-8")
