"""Comprueba la aplicación de IntelliJ; Config/Courier son dobles y la DB es H2."""
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "integration"))
from http_api import Api
from flow import Flow, shipment_request


def main():
    fixture = json.loads(Path("target/local-demo/session.json").read_text())
    assert fixture["mode"] == "simulated-local", "La sesión no pertenece a la demo aislada"
    os.environ["LOCAL_DEMO_URL"] = fixture["baseUrl"]
    api = Api({"urls": {"shipment": "LOCAL_DEMO_URL"}})
    api.sessions = {
        "admin": {"token": fixture["admin"]},
        "clientA": {"token": fixture["clientA"], "customerId": fixture["customerA"]},
        "clientB": {"token": fixture["clientB"], "customerId": fixture["customerB"]},
        "courierA": {"token": fixture["courierTokenA"], "courierId": fixture["courierA"]},
        "courierB": {"token": fixture["courierTokenB"], "courierId": fixture["courierB"]}}
    assert api.call("shipment", "/actuator/health")["status"] == "UP"
    flow = Flow(api)
    flow.shipment = api.call("shipment", "/api/v1/shipments", "POST",
                             shipment_request(fixture["customerA"]), "clientA", 201)
    api.call("shipment", flow.path + "/courier", "PUT", {"courierId": fixture["courierA"]}, "admin")
    flow.ownership()
    flow.delivery_and_cancellation()
    report = {"mode": "simulated-local", "status": "passed", "database": "H2 temporal",
              "checks": ["health", "creación y asignación", "JWT inválido", "roles y pertenencia A/B",
                         "listados", "tracking", "entrega", "terminales", "cancelación", "historial"]}
    Path("target/local-demo/result.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print("OK: demo local de IntelliJ; JWT real, roles A/B, alta, REST courier simulado, entrega y auditoría")


if __name__ == "__main__":
    main()
