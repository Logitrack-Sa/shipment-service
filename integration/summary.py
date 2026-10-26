import json
import os
from pathlib import Path

path = Path("target/integration/report.json")
if path.is_file():
    report = json.loads(path.read_text(encoding="utf-8"))
    text = "## Integración real: " + report["status"] + "\n"
    text += "Checkout: " + report.get("sourceRevision", "no registrado") + "\n"
    text += "\n".join("- " + case["name"] + ": " + case["status"] for case in report["cases"])
else:
    text = "## Integración real no ejecutada\nFaltan prerrequisitos o no arrancó el entorno. No constituye validación distribuida."
print(text)
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
        output.write(text + "\n")
