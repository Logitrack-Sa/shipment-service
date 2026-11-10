"""Comprueba o descarga las revisiones compatibles de los repositorios del plataforma."""
import argparse
import json
from pathlib import Path
import re
import subprocess

REPOSITORIES = {'config-repo', 'config-server', 'eureka-server', 'security-service',
                'courier-service', 'api-gateway'}


def synchronize(workspace, clone=False):
    manifest = Path(__file__).resolve().parents[1]/'deploy/stack-sources.json'
    sources = json.loads(manifest.read_text(encoding='utf-8'))
    if set(sources) != REPOSITORIES or not all(re.fullmatch('[0-9a-f]{40}', ref) for ref in sources.values()):
        raise ValueError('Manifiesto de fuentes incompleto o con revisiones invalidas')
    workspace = Path(workspace).resolve()
    for repository, revision in sources.items():
        destination = workspace/repository
        if clone:
            if destination.exists():
                raise ValueError(f'No se sobrescribe la carpeta existente {destination}')
            subprocess.run(['git', 'clone', '--no-checkout', '--filter=blob:none',
                            f'https://github.com/Logitrack-Sa/{repository}.git', str(destination)], check=True)
            subprocess.run(['git', '-C', str(destination), 'fetch', '--depth=1', 'origin', revision], check=True)
            subprocess.run(['git', '-C', str(destination), 'checkout', '--detach', revision], check=True)
        def git(*args):
            return subprocess.check_output(['git', '-C', str(destination), *args], text=True).strip()
        if git('status', '--porcelain') or git('rev-parse', 'HEAD^{tree}') != git('rev-parse', revision+'^{tree}'):
            raise ValueError(f'{repository} no coincide con la revision compatible {revision}')
        print(repository + ': ' + revision)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', default='..')
    parser.add_argument('--clone', action='store_true', help='Solo para un workspace vacio de CI')
    args = parser.parse_args()
    synchronize(args.workspace, args.clone)
