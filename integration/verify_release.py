"""Ejecuta el harness existente con el entorno local, sin secretos en argumentos."""
import argparse
import subprocess
import sys
from release_env import load

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--env-file',default='target/release/runtime.env')
    parser.add_argument('--project',required=True)
    args=parser.parse_args()
    load(args.env_file)
    raise SystemExit(subprocess.call([sys.executable,'integration/run.py',
         '--manifest','docs/sprint2/examples/integration.json','--compose-file','compose.release.yaml',
         '--project',args.project,'--allow-courier-stop']))
