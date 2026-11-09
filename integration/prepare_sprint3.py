"""Prepara una demo local del Sprint 3 con claves propias y configuracion Git fijada."""
import argparse
import json
from pathlib import Path
from prepare_release import prepare


def create(directory, label, gateway_port=8066):
    if not 1 <= gateway_port <= 65535:
        raise ValueError('Puerto de Gateway fuera del rango TCP')
    prepare(directory, 'alexxe576', 'sprint3-20261102.1', label)
    env = Path(directory).resolve() / 'runtime.env'
    with env.open('a', encoding='utf-8') as output:
        output.write(f'GATEWAY_PORT={gateway_port}\n')
    print('Arranque: docker compose --env-file ' + str(env)
          + ' -f deploy/docker-compose.yml -p logitrack-sprint3 up -d --no-build --wait --wait-timeout 900')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', default='target/sprint3')
    parser.add_argument('--config-label', help='Por defecto utiliza deploy/sprint3-sources.json')
    parser.add_argument('--gateway-port', type=int, default=8066)
    args = parser.parse_args()
    manifest = Path(__file__).resolve().parents[1]/'deploy/sprint3-sources.json'
    label = args.config_label or json.loads(manifest.read_text(encoding='utf-8'))['config-repo']
    create(args.directory, label, args.gateway_port)
