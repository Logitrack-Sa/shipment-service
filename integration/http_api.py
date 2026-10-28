"""Cliente de pruebas sin dependencias, sin redirecciones ni credenciales en logs."""
import base64
import json
import os
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler
from uuid import UUID

ACTORS = {"admin": "ADMIN", "clientA": "CLIENTE", "clientB": "CLIENTE",
          "courierA": "REPARTIDOR", "courierB": "REPARTIDOR"}


class PrerequisiteError(RuntimeError):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *_):
        return None


def claims(token):
    try:
        if len(token) > 32768:
            raise ValueError()
        payload = token.split(".")[1]
        data = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        UUID(data["sub"])
        return data
    except (ValueError, KeyError, IndexError, TypeError):
        raise AssertionError("El login no devolvió un JWT con identidad UUID válida") from None


class Api:
    def __init__(self, manifest, timeout=10):
        self.manifest, self.timeout = manifest, timeout
        self.urls, self.sessions = {}, {}
        for service, variable in manifest["urls"].items():
            url = os.environ.get(variable, "").rstrip("/")
            parsed = urlsplit(url)
            if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path:
                raise PrerequisiteError(f"Configurar {variable} con URL base HTTP sin credenciales ni ruta")
            self.urls[service] = url
        self.opener = build_opener(NoRedirect())

    def call(self, service, path, method="GET", data=None, actor=None, expected=200, token=None):
        headers = {"Accept": "application/json"}
        if actor:
            token = self.sessions[actor]["token"]
        if token:
            headers["Authorization"] = "Bearer " + token
        if data is not None:
            headers["Content-Type"] = "application/json"
        request = Request(self.urls[service] + path, method=method, headers=headers,
                          data=None if data is None else json.dumps(data).encode())
        try:
            response = self.opener.open(request, timeout=self.timeout)
        except HTTPError as failure:
            response = failure
        except (URLError, TimeoutError, OSError):
            raise AssertionError(f"{service}: no respondió dentro del límite") from None
        with response:
            if response.status != expected:
                raise AssertionError(f"{method} {service}{path}: esperado {expected}, recibido {response.status}")
            raw = response.read()
            try:
                return json.loads(raw) if raw else None
            except ValueError:
                raise AssertionError(f"{service}: respuesta JSON inválida") from None

    def login(self):
        login_field = self.manifest.get("loginField", "email")
        if login_field not in ("email", "username"):
            raise PrerequisiteError("loginField debe coincidir con email o username del contrato auth")
        for actor, role in ACTORS.items():
            account = self.manifest["accounts"][actor]
            values = {key: os.environ.get(account[key], "") for key in ("username", "password")}
            if not all(values.values()):
                raise PrerequisiteError(f"Faltan las variables de la cuenta de prueba {actor}")
            result = self.call("security", "/api/v1/auth/login", "POST",
                               {login_field: values["username"], "password": values["password"]})
            token = result["accessToken"]
            identity = claims(token)
            assert role in identity.get("roles", []), "Rol incorrecto en cuenta de prueba"
            if actor != "admin":
                UUID(identity["customerId" if role == "CLIENTE" else "courierId"])
            self.sessions[actor] = {**identity, "token": token}
            self.call("security", "/api/v1/auth/me", actor=actor)
        assert self.sessions["clientA"]["customerId"] != self.sessions["clientB"]["customerId"], "Clientes A/B deben ser distintos"
        assert self.sessions["courierA"]["courierId"] != self.sessions["courierB"]["courierId"], "Couriers A/B deben ser distintos"
