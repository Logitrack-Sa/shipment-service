"""Verificación opt-in del conjunto real. Salida 2 = prerrequisitos pendientes."""
import argparse
import json
import os
import subprocess
from pathlib import Path
from datetime import datetime, timezone
from http_api import Api, PrerequisiteError
from flow import Flow
from compose_env import Compose


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--compose-file", type=Path, required=True)
    parser.add_argument("--project", default="logitrack-e2e-local")
    parser.add_argument("--output", type=Path, default=Path("target/integration/report.json"))
    parser.add_argument("--allow-courier-stop", action="store_true")
    args = parser.parse_args()
    report = {"mode": "real-distributed", "status": "blocked", "checkedAt": datetime.now(timezone.utc).isoformat(), "cases": []}
    revision = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True, check=True, timeout=10)
    report["sourceRevision"] = revision.stdout.strip()
    report["scope"] = "Entrega del checkout indicado y digests de imágenes; no aprobación de un PR"
    compose = None
    try:
        if not args.allow_courier_stop:
            raise PrerequisiteError("La suite detiene courier en un entorno descartable; habilitar --allow-courier-stop")
        if not args.compose_file.is_file():
            raise PrerequisiteError("Falta el Compose image-only de #64/#65; no hay evidencia distribuida")
        if not args.compose_file.resolve().is_relative_to(Path.cwd().resolve()):
            raise PrerequisiteError("El Compose debe estar dentro de este checkout de integración")
        compose = Compose(args.compose_file, args.project)
        report["images"] = compose.verify_images()
        api = Api(json.loads(args.manifest.read_text(encoding="utf-8")))
        flow = Flow(api)
        for name, action in [("Health, configuración y login", flow.health_and_login),
                ("Clientes, couriers, alta y asignación", flow.create_resources),
                ("JWT y aislamiento A/B", flow.ownership),
                ("Entrega, terminales, cancelación y auditoría", flow.delivery_and_cancellation),
                ("Courier caído sin escritura parcial", lambda: flow.courier_outage(compose))]:
            try:
                action()
                report["cases"].append({"name": name, "status": "passed"})
            except Exception:
                report["cases"].append({"name": name, "status": "failed"})
                raise
        report["status"] = "passed"
        code = 0
    except PrerequisiteError as failure:
        report["reason"] = str(failure)
        code = 2
    except Exception:
        report["status"] = "failed"
        report["reason"] = "Falló una aserción/contrato del escenario; revisar el caso marcado, sin publicar credenciales"
        code = 1
    finally:
        if compose and report.get("images"):
            try:
                logs = "\n".join(compose.relevant_logs())
                for key, value in os.environ.items():
                    if any(word in key.upper() for word in ("PASSWORD", "TOKEN", "SECRET")) and len(value) >= 4:
                        logs = logs.replace(value, "[REDACTADO]")
                report["logs"] = logs.splitlines()
            except PrerequisiteError:
                report["logs"] = ["Logs no disponibles"]
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"Integración real: {report['status']}; evidencia: {args.output}")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
