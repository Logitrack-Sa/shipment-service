"""Verifica la aplicación en una base de pruebas descartable, sin dependencias extra."""

import base64
import subprocess
from pathlib import Path
import json
import os
import time
import uuid
from decimal import Decimal
from urllib.error import HTTPError
from urllib.request import Request, urlopen

TOKEN = None
BASE_URL = os.environ.get("SMOKE_BASE_URL", "http://localhost:8080").rstrip("/")


def request(path, method="GET", data=None, status=200):
    body = None if data is None else json.dumps(data).encode()
    headers = {"Content-Type": "application/json"}
    if TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    req = Request(BASE_URL + path, data=body, method=method, headers=headers)
    try:
        response = urlopen(req, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        text = response.read().decode()
        assert response.status == status, f"{method} {path}: {response.status}, {text}"
        media_type = response.headers.get("Content-Type", "").split(";")[0]
        if media_type == "application/json" or media_type.endswith("+json"):
            return json.loads(text)
        return text


def wait_for_application():
    for _ in range(60):
        try:
            if request("/actuator/health")["status"] == "UP":
                return
        except (OSError, AssertionError):
            pass
        time.sleep(2)
    raise RuntimeError("La aplicación no quedó disponible en /actuator/health")


def main():
    global TOKEN
    wait_for_application()
    request("/api/v1/shipments", status=401)
    encode = lambda data: base64.urlsafe_b64encode(json.dumps(data).encode()).rstrip(b"=")
    message = encode({"alg": "RS256", "kid": "ci-ephemeral"}) + b"." + encode({
        "sub": str(uuid.uuid4()), "roles": ["ADMIN"], "iss": "https://issuer.test",
        "aud": ["shipment-service", "courier-service"], "iat": int(time.time()), "exp": int(time.time()) + 600})
    key = Path(os.environ["SMOKE_FIXTURE_DIR"]) / "private.pem"
    signature = subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(key)], input=message,
                               capture_output=True, check=True).stdout
    TOKEN = (message + b"." + base64.urlsafe_b64encode(signature).rstrip(b"=")).decode()
    paths = request("/v3/api-docs")["paths"]
    assert all(path in paths for path in (
        "/api/v1/customers", "/api/v1/shipments",
        "/api/v1/shipments/{id}/courier"))
    assert "LogiTrack Docs" in request("/swagger-ui.html")
    assert "LogiTrack Docs" in request("/docs/")
    assert "swagger-ui-bundle.js" in request("/docs/explorer.html")
    request("/docs/app.js")
    request("/docs/styles.css")

    customer_data = {"name": "Cliente CI", "email": f"ci-{uuid.uuid4()}@example.com",
                     "phone": "+5493881234567"}
    customer = request("/api/v1/customers", "POST", customer_data, 201)
    assert request(f"/api/v1/customers/{customer['id']}")["email"] == customer_data["email"]
    assert request("/api/v1/customers?size=10")["content"]
    assert request("/api/v1/customers", "POST", customer_data, 409)["code"] == "DUPLICATE_RESOURCE"
    request("/api/v1/customers", "POST", {}, 400)

    assert not any(path.startswith("/api/v1/couriers") for path in paths)
    courier = {"id": "20000000-0000-0000-0000-000000000001"}

    address = {"street": "Bolivia", "number": "1239", "city": "Jujuy", "postalCode": "4600"}
    shipment = request("/api/v1/shipments", "POST", {
        "customerId": customer["id"], "origin": address, "destination": address,
        "weightKg": 2.5, "widthCm": 30, "heightCm": 20, "lengthCm": 40}, 201)
    shipment_path = f"/api/v1/shipments/{shipment['id']}"
    assert shipment["status"] == "PENDIENTE"
    assert Decimal(str(shipment["fee"])) == Decimal("2200.00")
    assert request(shipment_path)["id"] == shipment["id"]
    assert request(f"/api/v1/shipments/tracking/{shipment['trackingCode']}")["id"] == shipment["id"]
    assert any(item["id"] == shipment["id"] for item in
               request("/api/v1/shipments?status=PENDIENTE&size=100")["content"])
    assigned = request(shipment_path + "/courier", "PUT", {"courierId": courier["id"]})
    assert assigned["courierId"] == courier["id"]
    assert request(shipment_path)["courierId"] == courier["id"]

    error = request(shipment_path + "/courier", "PUT",
                    {"courierId": "20000000-0000-0000-0000-000000000002"}, 422)
    assert error["code"] == "COURIER_NOT_ACTIVE"
    assert request(f"/api/v1/shipments/{uuid.uuid4()}", status=404)["code"] == "RESOURCE_NOT_FOUND"
    request("/api/v1/shipments?size=0", status=400)
    request(shipment_path + "/status", "PATCH", {"status": "EN_RUTA"})
    request(shipment_path + "/status", "PATCH", {"status": "ENTREGADO"})
    assert len(request(shipment_path + "/history")) == 3
    assert request(shipment_path)["courierId"] == courier["id"]
    print("OK: health, OpenAPI, Swagger, clientes, courier REST simulado, JWT real, configuración remota, tarifa, estados e historial")


if __name__ == "__main__":
    main()
