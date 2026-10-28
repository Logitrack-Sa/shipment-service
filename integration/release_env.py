"""Carga exclusiva del entorno local de prueba; no imprime sus valores."""
from pathlib import Path
import os
import re

def load(path):
    source = Path(path).resolve()
    for line in source.read_text(encoding='utf-8').splitlines():
        if not line.strip() or line.startswith('#'):
            continue
        key, value = line.split('=', 1)
        if not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
            raise ValueError('Variable de entorno inválida')
        os.environ[key] = value
