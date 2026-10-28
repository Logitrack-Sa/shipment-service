"""No arranca ni publica nada cuando faltan entregas o cuentas de prueba."""
import json
import os
from pathlib import Path
import subprocess
import sys


def validate(file):
    path = Path(file).resolve()
    if not path.is_relative_to(Path.cwd().resolve()) or not path.is_file():
        raise ValueError("Pendiente #64/#65: falta Compose image-only versionado en main")
    manifest = json.loads(Path("docs/sprint2/examples/integration.json").read_text())
    required = [value for account in manifest["accounts"].values() for value in account.values()]
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise ValueError("Configurar cuentas de prueba: " + ", ".join(missing))
    result = subprocess.run(["docker", "compose", "--file", str(path), "config", "--format", "json"],
                            capture_output=True, text=True, check=True, timeout=30)
    if any("build" in service for service in json.loads(result.stdout)["services"].values()):
        raise ValueError("No se admite build local en la integración de entrega")


if __name__ == "__main__":
    try:
        validate(sys.argv[1])
    except (ValueError, OSError, subprocess.SubprocessError) as failure:
        print(str(failure) if isinstance(failure, ValueError) else "No se pudo validar el Compose de entrega")
        raise SystemExit(2)
