"""Empaqueta los JAR ya probados de las seis aplicaciones sin repetir Maven en Docker."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

SERVICES = ('config-server', 'eureka-server', 'security-service', 'courier-service', 'shipment-service', 'api-gateway')


def build(workspace, temporary, services=SERVICES):
    runtime = Path(__file__).resolve().parents[1] / 'deploy/Dockerfile.runtime'
    # El llamador elige una carpeta temporal en el disco con espacio disponible.
    temporary = Path(temporary).resolve()
    temporary.mkdir(parents=True, exist_ok=True)
    for service in services:
        jar = Path(workspace).resolve() / service / 'target' / (service + '-0.0.1-SNAPSHOT.jar')
        if not jar.is_file():
            raise ValueError(f'Falta {jar}; ejecutar ./mvnw verify package en ese proyecto.')
        source = jar.parents[1] / 'src/main'
        if any(file.stat().st_mtime > jar.stat().st_mtime for file in source.rglob('*') if file.is_file()):
            raise ValueError(f'El JAR de {service} es anterior al codigo; volver a empaquetar.')
        with tempfile.TemporaryDirectory(prefix=service+'-', dir=temporary) as directory:
            context = Path(directory).resolve()
            if not context.is_relative_to(temporary):
                raise ValueError('Contexto de compilacion fuera de la carpeta temporal elegida')
            shutil.copy2(jar, context/'app.jar')
            shutil.copy2(runtime, context/'Dockerfile')
            subprocess.run(['docker', 'build', '--tag', f'logitrack-{service}:sprint3-local', str(context)], check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', default='..')
    parser.add_argument('--temporary', default='target/sprint3-build')
    parser.add_argument('--services', nargs='+', choices=SERVICES, default=SERVICES)
    args = parser.parse_args()
    build(args.workspace, args.temporary, args.services)
