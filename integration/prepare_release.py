"""Genera credenciales y RSA descartables para la demo, sin sobrescribir entornos."""
import argparse
import json
from pathlib import Path
import re
import secrets
import shutil
import subprocess

def prepare(directory, namespace, version, config_label):
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]{2,}', namespace):
        raise ValueError('Namespace Docker Hub inválido')
    if not re.fullmatch(r'sprint2-\d{8}\.\d+', version):
        raise ValueError('Usar versión sprint2-AAAAMMDD.N')
    if not re.fullmatch(r'[0-9a-f]{40}', config_label):
        raise ValueError('Config Repo requiere un SHA Git explícito')
    root = Path(directory).resolve()
    root.mkdir(parents=True, exist_ok=True)
    root.chmod(0o700)
    env_file = root/'runtime.env'
    if env_file.exists():
        raise ValueError('El entorno ya existe; usar otra carpeta para conservar credenciales y volúmenes')
    openssl = shutil.which('openssl') or r'C:\Program Files\Git\usr\bin\openssl.exe'
    private, public = root/'private.pem', root/'public.pem'
    subprocess.run([openssl,'genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',str(private)],check=True,capture_output=True)
    subprocess.run([openssl,'pkey','-in',str(private),'-pubout','-out',str(public)],check=True,capture_output=True)
    # Material descartable: carpeta host 0700 y archivos montados solo donde se requieren.
    private.chmod(0o444)
    public.chmod(0o444)
    values = {'DOCKERHUB_NAMESPACE':namespace,'RELEASE_VERSION':version,'CONFIG_GIT_LABEL':config_label,
              'JWT_PRIVATE_KEY_FILE':private.as_posix(),'JWT_PUBLIC_KEY_FILE':public.as_posix(),
              'JWT_KEY_ID':'demo-'+secrets.token_hex(6),'JWT_ISSUER':'https://security-service',
              'JWT_TTL':'PT30M','SECURITY_ADMIN_USERNAME':'demo-admin'}
    for service in ('SHIPMENT','COURIER','SECURITY'):
        for suffix in ('DB_PASSWORD','DB_ROOT_PASSWORD'):
            values[f'{service}_{suffix}'] = secrets.token_hex(24)
    run = secrets.token_hex(6)
    for actor in ('ADMIN','CLIENT_A','CLIENT_B','COURIER_A','COURIER_B'):
        values[f'E2E_{actor}_USERNAME'] = f'{actor.lower()}-{run}@example.test'
        values[f'E2E_{actor}_PASSWORD'] = secrets.token_hex(20)
    values['SECURITY_ADMIN_EMAIL'] = values['E2E_ADMIN_USERNAME']
    values['SECURITY_ADMIN_PASSWORD'] = values['E2E_ADMIN_PASSWORD']
    manifest=Path('docs/sprint2/releases')/(version+'.json')
    if manifest.exists():
        locked=json.loads(manifest.read_text())
        if locked['namespace'] != namespace or locked['configurationRevision'] != config_label:
            raise ValueError('La configuración solicitada no coincide con la entrega publicada')
        for service,variable in {'shipment-service':'SHIPMENT_IMAGE','courier-service':'COURIER_IMAGE',
                                 'security-service':'SECURITY_IMAGE','config-server':'CONFIG_SERVER_IMAGE'}.items():
            image=locked['images'][service]['image']
            if not re.fullmatch(re.escape(f'{namespace}/logitrack-{service}:{version}@sha256:')+'[0-9a-f]{64}',image):
                raise ValueError('Referencia de imagen inválida en el manifiesto publicado')
            values[variable]=image
    for service,port in (('SHIPMENT',8081),('COURIER',8082),('SECURITY',8083),('CONFIG',8888)):
        values[f'E2E_{service}_URL'] = f'http://127.0.0.1:{port}'
    with env_file.open('x',encoding='utf-8') as output:
        output.write('\n'.join(f'{key}={value}' for key,value in values.items())+'\n')
    env_file.chmod(0o600)
    print(f'Entorno descartable preparado en {root}; no compartir runtime.env ni private.pem')

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--directory',default='target/release')
    parser.add_argument('--namespace',required=True)
    parser.add_argument('--version',required=True)
    parser.add_argument('--config-label',required=True)
    args=parser.parse_args()
    prepare(args.directory,args.namespace,args.version,args.config_label)
