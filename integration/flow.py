"""Escenarios reales: las identidades A/B se provisionan previamente por ADMIN."""
from decimal import Decimal
from uuid import UUID, uuid4


def shipment_request(customer_id):
    address = {"street": "Prueba", "number": "123", "city": "Jujuy", "postalCode": "4600"}
    return {"customerId": customer_id, "origin": address, "destination": address,
            "weightKg": 2.5, "widthCm": 30, "heightCm": 20, "lengthCm": 40}


class Flow:
    def __init__(self, api):
        self.api, self.shipment = api, None

    def health_and_login(self):
        for service in self.api.urls:
            assert self.api.call(service, "/actuator/health")["status"] == "UP", "Servicio sin health UP"
        profile = self.api.manifest.get("profile", "dev")
        if profile not in ("dev", "prod"):
            raise AssertionError("Perfil de integración debe ser dev/prod")
        assert self.api.call("config", f"/shipment-service/{profile}")["propertySources"], "Config sin propiedades"
        self.api.login()

    def create_resources(self):
        call = self.api.call
        customer = call("shipment", "/api/v1/customers", "POST", {"name": "Integración",
                        "email": f"integration-{uuid4()}@example.test", "phone": "123456"}, "admin", 201)
        UUID(customer["id"])
        courier = call("courier", "/api/v1/couriers", "POST", {"name": "Integración", "vehicleType": "MOTO"}, "admin", 201)
        UUID(courier["id"])
        for actor in ("clientA", "clientB"):
            customer_id = self.api.sessions[actor]["customerId"]
            call("shipment", f"/api/v1/customers/{customer_id}", actor="admin")
        for actor in ("courierA", "courierB"):
            courier_id = self.api.sessions[actor]["courierId"]
            assert call("courier", f"/api/v1/couriers/{courier_id}/availability", actor="admin")["available"], "Courier de fixture inactivo"
        self.shipment = call("shipment", "/api/v1/shipments", "POST",
                             shipment_request(self.api.sessions["clientA"]["customerId"]), "clientA", 201)
        assert Decimal(str(self.shipment["fee"])) == Decimal("2200.00"), "Tarifa volumétrica incorrecta"
        assert self.shipment["status"] == "PENDIENTE", "Alta sin estado PENDIENTE"
        call("shipment", self.path + "/courier", "PUT",
             {"courierId": self.api.sessions["courierA"]["courierId"]}, "admin")

    @property
    def path(self):
        return "/api/v1/shipments/" + self.shipment["id"]

    def ownership(self):
        call = self.api.call
        call("shipment", self.path, expected=401)
        call("shipment", self.path, token="invalid.jwt.token", expected=401)
        for suffix in (self.path, "/api/v1/shipments/tracking/" + self.shipment["trackingCode"], self.path + "/history"):
            for actor in ("clientA", "courierA"):
                call("shipment", suffix, actor=actor)
            for actor in ("clientB", "courierB"):
                call("shipment", suffix, actor=actor, expected=403)
        for actor, field in (("clientA", "customerId"), ("courierA", "courierId")):
            page = call("shipment", "/api/v1/shipments?size=100", actor=actor)
            assert page["content"], "Lista propia vacía"
            assert all(item[field] == self.api.sessions[actor][field] for item in page["content"]), "Fuga entre propietarios en listado"
        call("shipment", "/api/v1/shipments", "POST",
             shipment_request(self.api.sessions["clientB"]["customerId"]), "clientA", 403)
        call("shipment", self.path + "/status", "PATCH", {"status": "EN_RUTA"}, "courierB", 403)
        assert len(call("shipment", self.path + "/history", actor="admin")) == 1, "Denegación generó historial"

    def delivery_and_cancellation(self):
        call = self.api.call
        for state in ("EN_RUTA", "ENTREGADO"):
            result = call("shipment", self.path + "/status", "PATCH", {"status": state}, "courierA")
            assert result["status"] == state, "Transición no aplicada"
        events = call("shipment", self.path + "/history", actor="admin")
        assert [event["newStatus"] for event in events] == ["PENDIENTE", "EN_RUTA", "ENTREGADO"], "Historial incompleto"
        call("shipment", self.path + "/status", "PATCH", {"status": "EN_RUTA"}, "admin", 422)
        assert call("shipment", self.path + "/history", actor="admin") == events, "Estado terminal generó historial"
        pending = call("shipment", "/api/v1/shipments", "POST",
                       shipment_request(self.api.sessions["clientA"]["customerId"]), "clientA", 201)
        path = "/api/v1/shipments/" + pending["id"]
        assert call("shipment", path + "/cancellation", "POST", actor="admin")["status"] == "CANCELADO", "Cancelación inválida"
        assert len(call("shipment", path + "/history", actor="admin")) == 2, "Auditoría de cancelación inválida"

    def courier_outage(self, compose):
        call = self.api.call
        pending = call("shipment", "/api/v1/shipments", "POST",
                       shipment_request(self.api.sessions["clientA"]["customerId"]), "clientA", 201)
        path = "/api/v1/shipments/" + pending["id"]
        before = call("shipment", path, actor="admin")
        history = call("shipment", path + "/history", actor="admin")
        try:
            compose.stop_courier()
            call("shipment", path + "/courier", "PUT",
                 {"courierId": self.api.sessions["courierA"]["courierId"]}, "admin", 503)
            assert call("shipment", path, actor="admin") == before, "Fallo remoto modificó envío"
            assert call("shipment", path + "/history", actor="admin") == history, "Fallo remoto generó auditoría"
        finally:
            compose.restore_courier()
