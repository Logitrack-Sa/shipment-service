"""Prepara un entorno privado de Postman desde el runtime local, sin mostrar secretos."""
import argparse
import json
from pathlib import Path
import secrets


def export(directory):
    root = Path(directory).resolve()
    values = dict(line.split('=', 1) for line in (root/'runtime.env').read_text(encoding='utf-8').splitlines()
                  if line.strip() and not line.lstrip().startswith('#'))
    port = int(values.get('GATEWAY_PORT', '8066'))
    if not 1 <= port <= 65535:
        raise ValueError('Puerto de Gateway invalido')
    variables = {'gatewayUrl': f'http://127.0.0.1:{port}',
                 'adminEmail': values['SECURITY_ADMIN_EMAIL'],
                 'adminPassword': values['SECURITY_ADMIN_PASSWORD'],
                 'demoUserPassword': secrets.token_urlsafe(24), 'corsOrigin': 'http://localhost:3000'}
    environment = {'name': 'LogiTrack plataforma - entorno privado local',
                   '_postman_variable_scope': 'environment',
                   'values': [{'key': key, 'value': value, 'enabled': True,
                               'type': 'secret' if 'Password' in key else 'default'}
                              for key, value in variables.items()]}
    destination = root/'postman.environment.json'
    with destination.open('x', encoding='utf-8') as output:
        json.dump(environment, output, ensure_ascii=False, indent=2)
    destination.chmod(0o600)
    print(f'Entorno privado creado en {destination}; no compartir ni versionar este archivo.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', default='target/stack')
    export(parser.parse_args().directory)
