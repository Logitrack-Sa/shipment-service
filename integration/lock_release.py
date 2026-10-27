"""Registra digests reales de Docker Hub y fija el entorno a esas imágenes."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
from release_env import load

IMAGES={'shipment-service':'SHIPMENT_IMAGE','courier-service':'COURIER_IMAGE',
        'security-service':'SECURITY_IMAGE','config-server':'CONFIG_SERVER_IMAGE'}

def lock(env_file):
    load(env_file)
    namespace,version=os.environ['DOCKERHUB_NAMESPACE'],os.environ['RELEASE_VERSION']
    manifest={'schemaVersion':1,'version':version,'namespace':namespace,
              'configurationRevision':os.environ['CONFIG_GIT_LABEL'],'images':{}}
    variables={}
    for service,variable in IMAGES.items():
        repository=f'{namespace}/logitrack-{service}'
        tag=f'{repository}:{version}'
        result=subprocess.run(['docker','image','inspect',tag],capture_output=True,text=True,check=True,timeout=30)
        inspected=json.loads(result.stdout)[0]
        digest=next((value for value in inspected.get('RepoDigests',[]) if value.startswith(repository+'@sha256:')),None)
        if not digest: raise ValueError(f'{service} no tiene digest de registro; ejecutar pull de la versión publicada')
        labels=inspected['Config']['Labels']
        if labels.get('org.opencontainers.image.source') != f'https://github.com/Logitrack-Sa/{service}':
            raise ValueError(f'Fuente OCI incorrecta para {service}')
        revision=labels.get('org.opencontainers.image.revision','')
        if not re.fullmatch('[0-9a-f]{40}',revision) or labels.get('org.opencontainers.image.version') != version:
            raise ValueError(f'Metadatos OCI incompletos de {service}')
        variables[variable]=tag+'@'+digest.split('@',1)[1]
        manifest['images'][service]={'image':variables[variable],'digest':digest,'sourceRevision':revision}
    path=Path('docs/sprint2/releases')/(version+'.json')
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists() and json.loads(path.read_text()) != manifest:
        raise ValueError('Los digests cambiaron respecto de la entrega acreditada')
    path.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    source=Path(env_file)
    kept=[line for line in source.read_text().splitlines() if line.split('=',1)[0] not in variables]
    source.write_text('\n'.join(kept+[f'{key}={value}' for key,value in variables.items()])+'\n',encoding='utf-8')
    print(f'Cuatro imágenes fijadas a sus digests de registro; manifiesto: {path}')

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--env-file',default='target/release/runtime.env')
    lock(parser.parse_args().env_file)
