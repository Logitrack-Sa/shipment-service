"""Regresión del doble HTTP: UUID hex desconocido nunca cierra la conexión."""
import hashlib
import json
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from http.server import ThreadingHTTPServer
import contract_fixture


class ContractFixtureTest(unittest.TestCase):
    def setUp(self):
        self.path_patch = patch.object(contract_fixture, "Path")
        fake_path = self.path_patch.start()
        fake_path.return_value.read_text.return_value = hashlib.sha256(b"expected-token").hexdigest()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), contract_fixture.Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.path_patch.stop()

    def call(self, identifier, token="expected-token"):
        req = Request(f"http://127.0.0.1:{self.server.server_port}/api/v1/couriers/{identifier}/availability",
                      headers={"Authorization": "Bearer " + token})
        try:
            response = urlopen(req, timeout=2)
        except HTTPError as failure:
            response = failure
        with response:
            return response.status, json.load(response)

    def test_unknown_hex_uuid_returns_404_instead_of_disconnect(self):
        status, body = self.call("deadbeef-0000-4000-8000-00000000abcd")
        self.assertEqual(status, 404)
        self.assertIn("code", body)

    def test_known_courier_requires_exact_forwarded_token(self):
        identifier = "20000000-0000-0000-0000-000000000001"
        self.assertEqual(self.call(identifier, "other-token")[0], 401)
        status, body = self.call(identifier)
        self.assertEqual(status, 200)
        self.assertTrue(body["available"])


if __name__ == "__main__":
    unittest.main()
