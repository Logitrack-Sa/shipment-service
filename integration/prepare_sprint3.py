"""Prepara una demo local del Sprint 3 con claves propias y configuracion Git fijada."""
import argparse
from pathlib import Path
from prepare_release import prepare


def create(directory, label):
    prepare(directory, 'alexxe576', 'sprint3-20261102.1', label)
    env = Path(directory).resolve() / 'runtime.env'
    with env.open('a', encoding='utf-8') as output:
        output.write('GATEWAY_PORT=8080\n')
    print('Arranque: docker compose --env-file ' + str(env)
          + ' -f deploy/docker-compose.yml -p logitrack-sprint3 up -d --no-build --wait --wait-timeout 900')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', default='target/sprint3')
    parser.add_argument('--config-label', required=True)
    args = parser.parse_args()
    create(args.directory, args.config_label)
