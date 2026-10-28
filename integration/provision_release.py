"""Provisiona actores A/B mediante las APIs reales y la cuenta ADMIN del entorno demo."""
import argparse
import os
from http_api import Api, claims
from release_env import load
import json
from pathlib import Path

def provision():
    api = Api(json.loads(Path('docs/sprint2/examples/integration.json').read_text()))
    login = api.call('security','/api/v1/auth/login','POST',
                     {'email':os.environ['E2E_ADMIN_USERNAME'],'password':os.environ['E2E_ADMIN_PASSWORD']})
    api.sessions['admin'] = {**claims(login['accessToken']),'token':login['accessToken']}
    for actor,role in (('CLIENT_A','CLIENTE'),('CLIENT_B','CLIENTE'),('COURIER_A','REPARTIDOR'),('COURIER_B','REPARTIDOR')):
        if role == 'CLIENTE':
            domain = api.call('shipment','/api/v1/customers','POST',
                              {'name':actor,'email':os.environ[f'E2E_{actor}_USERNAME'],'phone':'123456'},'admin',201)
            link = 'customerId'
        else:
            domain = api.call('courier','/api/v1/couriers','POST',{'name':actor,'vehicleType':'MOTO'},'admin',201)
            link = 'courierId'
        api.call('security','/api/v1/users','POST',{'email':os.environ[f'E2E_{actor}_USERNAME'],
                 'username':os.environ[f'E2E_{actor}_USERNAME'].split('@')[0], 'name':actor,
                 'password':os.environ[f'E2E_{actor}_PASSWORD'],'roles':[role],link:domain['id']},'admin',201)
    print('Actores ADMIN, CLIENTE A/B y REPARTIDOR A/B provisionados mediante APIs reales')

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--env-file',default='target/release/runtime.env')
    args=parser.parse_args()
    load(args.env_file)
    provision()
