"""Pruebas del harness con dobles; no representan integración distribuida real."""
import base64
import json
import os
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from types import SimpleNamespace
from unittest.mock import patch
from http_api import Api, PrerequisiteError, claims
from flow import Flow
from compose_env import Compose, APPLICATIONS


class ApiTest(unittest.TestCase):
    def setUp(self):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(403 if self.path == "/forbidden" else 200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"message":"sensitive-response-do-not-log"}')
            def log_message(self, *_):
                pass
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.env = patch.dict(os.environ, {"TEST_URL": f"http://127.0.0.1:{self.server.server_port}"})
        self.env.start()
        self.api = Api({"urls": {"shipment": "TEST_URL"}})

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(); self.env.stop()

    def test_expected_denial_is_checked_without_leaking_response(self):
        self.api.call("shipment", "/forbidden", expected=403)
        with self.assertRaises(AssertionError) as captured:
            self.api.call("shipment", "/forbidden", expected=200)
        self.assertNotIn("sensitive-response", str(captured.exception))

    def test_invalid_url_and_missing_environment_are_prerequisites(self):
        with patch.dict(os.environ, {"TEST_URL": "https://user:secret@example.test"}):
            with self.assertRaises(PrerequisiteError): Api({"urls": {"shipment": "TEST_URL"}})
        with self.assertRaises(PrerequisiteError): Api({"urls": {"shipment": "NOT_CONFIGURED_TEST_URL"}})

    def test_identity_parser_rejects_invalid_jwt(self):
        with self.assertRaises(AssertionError): claims("invalid.jwt")
        payload = {"sub": "00000000-0000-0000-0000-000000000001"}
        jwt = "header." + base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=") + ".signature"
        self.assertEqual(claims(jwt), payload)


class FlowTest(unittest.TestCase):
    def api(self, leaked=False):
        sessions = {"clientA": {"customerId": "customer-a"}, "courierA": {"courierId": "courier-a"},
                    "clientB": {"customerId": "customer-b"}, "courierB": {"courierId": "courier-b"}}
        def call(service, path, method="GET", data=None, actor=None, expected=200, **kwargs):
            if "?size" in path:
                return {"content": [{"customerId": "foreign" if leaked else "customer-a", "courierId": "courier-a"}]}
            if path.endswith("/history"):
                return [{"id": "history-1"}]
            return {"id": "shipment-a"}
        return SimpleNamespace(sessions=sessions, call=call)

    def test_ownership_scenario_accepts_isolated_rows(self):
        flow = Flow(self.api())
        flow.shipment = {"id": "shipment-a", "trackingCode": "LT-0123456789"}
        flow.ownership()

    def test_ownership_scenario_fails_on_list_leak(self):
        flow = Flow(self.api(leaked=True))
        flow.shipment = {"id": "shipment-a", "trackingCode": "LT-0123456789"}
        with self.assertRaisesRegex(AssertionError, "Fuga entre propietarios"):
            flow.ownership()


class ComposeTest(unittest.TestCase):
    def config(self):
        services = {name: {"image": "example/" + name + ":0.2.0"} for name in APPLICATIONS}
        services.update({"mysql-" + str(i): {"image": "mysql:8.4"} for i in range(3)})
        return {"services": services}

    def test_rejects_non_isolated_project(self):
        with self.assertRaises(PrerequisiteError): Compose("compose.yaml", "production")

    def test_requires_published_digest_not_just_local_tag(self):
        compose = Compose("compose.yaml", "logitrack-e2e-test")
        with patch.object(compose, "execute", return_value=self.config()), patch("compose_env.subprocess.run", return_value=SimpleNamespace(stdout="[]")):
            with self.assertRaisesRegex(PrerequisiteError, "digest de registro"):
                compose.verify_images()

    def test_accepts_four_versioned_images_and_three_databases(self):
        compose = Compose("compose.yaml", "logitrack-e2e-test")
        with patch.object(compose, "execute", return_value=self.config()), patch("compose_env.subprocess.run", return_value=SimpleNamespace(stdout='["example/image@sha256:abc"]')):
            self.assertEqual(len(compose.verify_images()), 4)

    def test_rejects_build_and_mutable_latest(self):
        for key, value in [("build", "."), ("image", "example/shipment:latest")]:
            config = self.config(); config["services"]["shipment-service"][key] = value
            compose = Compose("compose.yaml", "logitrack-e2e-test")
            with patch.object(compose, "execute", return_value=config):
                with self.assertRaises(PrerequisiteError): compose.verify_images()


if __name__ == "__main__":
    unittest.main()
