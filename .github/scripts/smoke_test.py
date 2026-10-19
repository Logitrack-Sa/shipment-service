"""Verifica la aplicación en una base de pruebas descartable, sin dependencias extra."""

import json
import os
import time
import uuid
from decimal import Decimal
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE_URL = os.environ.get("SMOKE_BASE_URL", "http://localhost:8080").rstrip("/")


def request(path, method="GET", data=None, status=200):
    body = None if data is None else json.dumps(data).encode()
    req = Request(BASE_URL + path, data=body, method=method,
                  headers={"Content-Type": "application/json"})
    try:
        response = urlopen(req, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        text = response.read().decode()
        assert response.status == status, f"{method} {path}: {response.status}, {text}"
        if "application/json" in response.headers.get("Content-Type", ""):
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
    wait_for_application()
    paths = request("/v3/api-docs")["paths"]
    assert all(path in paths for path in (
        "/api/v1/customers", "/api/v1/couriers", "/api/v1/shipments",
        "/api/v1/shipments/{id}/courier"))
    assert "html" in request("/swagger-ui.html").lower()

    customer_data = {"name": "Cliente CI", "email": f"ci-{uuid.uuid4()}@example.com",
                     "phone": "+5493881234567"}
    customer = request("/api/v1/customers", "POST", customer_data, 201)
    assert request(f"/api/v1/customers/{customer['id']}")["email"] == customer_data["email"]
    assert request("/api/v1/customers?size=10")["content"]
    assert request("/api/v1/customers", "POST", customer_data, 409)["code"] == "DUPLICATE_RESOURCE"
    request("/api/v1/customers", "POST", {}, 400)

    courier = request("/api/v1/couriers", "POST",
                      {"name": "Repartidor CI", "vehicleType": "MOTO"}, 201)
    courier_path = f"/api/v1/couriers/{courier['id']}"
    assert request(courier_path)["status"] == "ACTIVO"
    assert request("/api/v1/couriers?status=ACTIVO")["content"]

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

    request(courier_path + "/status", "PATCH", {"status": "INACTIVO"})
    error = request(shipment_path + "/courier", "PUT", {"courierId": courier["id"]}, 422)
    assert error["code"] == "COURIER_NOT_ACTIVE"
    assert request(f"/api/v1/shipments/{uuid.uuid4()}", status=404)["code"] == "RESOURCE_NOT_FOUND"
    request("/api/v1/shipments?size=0", status=400)
    print("OK: health, OpenAPI, Swagger, clientes, repartidores, tarifa, envíos, asignación y errores")


if __name__ == "__main__":
    main()
