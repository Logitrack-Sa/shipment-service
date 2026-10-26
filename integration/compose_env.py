"""Control exclusivo del Compose descartable de integración; nunca elimina volúmenes."""
import json
import re
import subprocess
from http_api import PrerequisiteError

APPLICATIONS = ("shipment-service", "courier-service", "security-service", "config-server")


class Compose:
    def __init__(self, file, project):
        if not re.fullmatch(r"logitrack-e2e-[a-z0-9-]+", project):
            raise PrerequisiteError("Usar un proyecto aislado logitrack-e2e-<nombre>")
        self.command = ["docker", "compose", "--file", str(file), "--project-name", project]

    def execute(self, *args, json_output=False):
        try:
            result = subprocess.run([*self.command, *args], capture_output=True, text=True, check=True, timeout=180)
        except (subprocess.SubprocessError, OSError):
            raise PrerequisiteError("Docker no completó la operación de integración; revisar el entorno aislado") from None
        return json.loads(result.stdout) if json_output else result.stdout

    def verify_images(self):
        config = self.execute("config", "--format", "json", json_output=True)
        services = config["services"]
        if any("build" in value for value in services.values()):
            raise PrerequisiteError("La entrega debe ser image-only, sin build local")
        evidence = {}
        for name in APPLICATIONS:
            image = services.get(name, {}).get("image", "")
            if not image or ("@sha256:" not in image and (":" not in image.rsplit("/", 1)[-1] or image.endswith(":latest"))):
                raise PrerequisiteError(f"Falta imagen versionada de {name}")
            try:
                inspected = subprocess.run(["docker", "image", "inspect", image, "--format", "{{json .RepoDigests}}"],
                                           capture_output=True, text=True, check=True, timeout=30)
                digests = json.loads(inspected.stdout)
            except (subprocess.SubprocessError, OSError, ValueError):
                raise PrerequisiteError(f"Ejecutar docker compose pull para {name}") from None
            if not digests:
                raise PrerequisiteError(f"{name} no tiene digest de registro: no demuestra imagen publicada")
            evidence[name] = {"image": image, "digests": digests}
        databases = [name for name, data in services.items() if data.get("image", "").split("/")[-1].split("@")[0].split(":")[0] == "mysql"]
        if len(databases) != 3:
            raise PrerequisiteError("El Compose debe incluir tres bases MySQL independientes")
        return evidence

    def stop_courier(self):
        self.execute("stop", "courier-service")

    def restore_courier(self):
        self.execute("up", "--no-build", "--pull", "never", "--no-deps", "--wait", "courier-service")

    def relevant_logs(self):
        raw = self.execute("logs", "--no-color", "--tail", "100")
        lines = [line for line in raw.splitlines() if re.search(r"Started .* in .* seconds|Flyway|Migrat|ERROR|WARN", line)]
        # No exportar cuerpos de request, tokens ni contraseñas en evidencia.
        return [re.sub(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", "[JWT REDACTADO]", line)
                for line in lines[:80]]
