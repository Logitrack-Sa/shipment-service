"""Comprueba registro y flujo real via Gateway; no confunde un smoke con la entrega completa."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, build_opener
from uuid import uuid4
from http_api import NoRedirect
from stack_security import cors, roles, delivery

EXPECTED = {'SHIPMENT-SERVICE': 8081, 'COURIER-SERVICE': 8082,
            'SECURITY-SERVICE': 8083, 'API-GATEWAY': 8080}


def request(url, method='GET', body=None, token=None):
    headers = {'Accept': 'application/json'}
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    query = Request(url, data=json.dumps(body).encode() if body is not None else None,
                    headers=headers, method=method)
    try:
        response = build_opener(NoRedirect()).open(query, timeout=15)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read()
        try:
            value = json.loads(raw) if raw else {}
        except ValueError:
            value = {}
        return response.code, value


def registered(eureka, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            status, payload = request(eureka + '/eureka/apps')
            apps = payload.get('applications', {}).get('application', [])
            if isinstance(apps, dict):
                apps = [apps]
            found = {}
            for app in apps:
                instances = app.get('instance', [])
                if isinstance(instances, dict):
                    instances = [instances]
                healthy = [i for i in instances if i.get('status') == 'UP']
                if healthy:
                    found[app['name']] = healthy[0]
            if status == 200 and EXPECTED.keys() <= found.keys():
                for name, port in EXPECTED.items():
                    instance = found[name]
                    assert instance['port']['$'] == port, f'Puerto anunciado incorrecto: {name}'
                    assert instance['hostName'] == name.lower(), f'Host no alcanzable por DNS Docker: {name}'
                return sorted(EXPECTED)
        except URLError:
            pass
        time.sleep(3)
    raise AssertionError('No aparecieron los cuatro clientes UP en Eureka dentro del plazo')


def wait_for_gateway(gateway, timeout=180, stable_seconds=35, poll_seconds=2):
    deadline = time.monotonic() + timeout
    healthy_since = None
    while time.monotonic() < deadline:
        status, payload = request(gateway.rstrip('/') + '/actuator/health')
        if status == 200 and payload.get('status') == 'UP':
            healthy_since = healthy_since if healthy_since is not None else time.monotonic()
            if time.monotonic() - healthy_since >= stable_seconds:
                return
        else:
            healthy_since = None
        assert status in (200, 503), f'Salud inesperada del Gateway: HTTP {status}'
        time.sleep(poll_seconds)
    raise AssertionError('Gateway no recupero su catalogo de Eureka dentro del plazo')


def verify(args):
    values = dict(line.split('=', 1) for line in Path(args.env_file).read_text(encoding='utf-8').splitlines()
                  if line.strip() and not line.startswith('#'))
    gateway = args.gateway or 'http://127.0.0.1:' + values.get('GATEWAY_PORT', '8066')
    report = {'time': datetime.now(timezone.utc).isoformat(), 'checks': [],
              'routeMode': 'discovery-smoke' if args.discovery_routes else 'public-api',
              'pending': ['#81 rutas definitivas', '#82 JWT en Gateway', '#83 CORS'] if args.discovery_routes else []}

    def call(service, path, method='GET', body=None, token=None, expected=200):
        prefix = '/' + service if args.discovery_routes else ''
        status, payload = request(gateway.rstrip('/') + prefix + path, method, body, token)
        assert status == expected, f'{method} {prefix}{path}: esperado {expected}, recibido {status}'
        if expected >= 400:
            assert isinstance(payload, dict) and payload.get('code') and payload.get('message'), 'Error sin contrato estructurado'
        return payload

    try:
        report['registered'] = registered(args.eureka.rstrip('/'))
        report['checks'].append('Cuatro clientes UP con DNS y puertos correctos')
        token = call('security-service', '/api/v1/auth/login', 'POST',
                     {'email': values['SECURITY_ADMIN_EMAIL'], 'password': values['SECURITY_ADMIN_PASSWORD']})['accessToken']
        report['checks'].append('Login ADMIN mediante Gateway')
        call('shipment-service', '/api/v1/shipments', expected=401)
        call('shipment-service', '/api/v1/shipments', token='invalid.jwt.token', expected=401)
        call('shipment-service', '/api/v1/shipments', token=token)
        report['checks'].append('Bearer propagado y rechazo de peticion anonima por el sistema')
        unique = uuid4().hex[:12]
        customer = call('shipment-service', '/api/v1/customers', 'POST',
                        {'name': 'Stack '+unique, 'email': unique+'@example.test', 'phone': '123456'}, token, 201)
        courier = call('courier-service', '/api/v1/couriers', 'POST',
                       {'name': 'Stack '+unique, 'dni': str(10000000 + int(unique, 16) % 90000000),
                        'vehicleType': 'MOTO'}, token, 201)
        address = {'street': 'Bolivia', 'number': '1239', 'city': 'Jujuy', 'postalCode': '4600'}
        shipment = call('shipment-service', '/api/v1/shipments', 'POST',
                        {'customerId': customer['id'], 'origin': address, 'destination': address,
                         'weightKg': 2.5, 'widthCm': 30, 'heightCm': 20, 'lengthCm': 40}, token, 201)
        path = '/api/v1/shipments/' + shipment['id']
        assigned = call('shipment-service', path+'/courier', 'PUT', {'courierId': courier['id']}, token)
        assert assigned['courierId'] == courier['id']
        report['checks'].append('Asignacion con Feign, Eureka y courier reales')
        call('shipment-service', path+'/courier', 'PUT', {'courierId': str(uuid4())}, token, 404)
        report['checks'].append('Repartidor inexistente devuelve 404 controlado')
        actors = None
        if not args.discovery_routes:
            cors(gateway.rstrip('/'), args.cors_origin)
            actors = roles(call, token, customer, courier, shipment)
            report['checks'].append('JWT, roles ADMIN/CLIENTE/REPARTIDOR, pertenencia A/B y CORS permitidos/denegados')
        if args.allow_courier_stop:
            compose = ['docker', 'compose', '--env-file', str(Path(args.env_file).resolve()),
                       '-f', 'deploy/docker-compose.yml', '-p', args.project]
            before = call('shipment-service', path, token=token)
            history = call('shipment-service', path+'/history', token=token)
            try:
                subprocess.run(compose+['stop', 'courier-service'], check=True, capture_output=True)
                call('shipment-service', path+'/courier', 'PUT', {'courierId': courier['id']}, token, 503)
                after = call('shipment-service', path, token=token)
                assert after == before, 'Se modifico el envio pese al fallo remoto'
                assert call('shipment-service', path+'/history', token=token) == history, 'Fallo remoto genero auditoria'
                report['checks'].append('Courier caido: 503 estructurado sin modificar el envio')
            finally:
                subprocess.run(compose+['up', '-d', '--no-build', '--wait', '--wait-timeout', '300',
                                        'courier-service'], check=True, capture_output=True)
            wait_for_gateway(gateway)
            report['checks'].append('Courier restaurado y Gateway UP tras renovar Eureka')
        if actors:
            delivery(call, token, actors['courier'], shipment)
            report['checks'].append('Entrega por repartidor asignado, historial y rechazo de transicion terminal')
        report['result'] = 'passed'
        report['fullStackAcceptance'] = bool(actors and args.allow_courier_stop)
        report['scope'] = 'Integracion real #88' if report['fullStackAcceptance'] else 'Comprobacion parcial de infraestructura'
    except (AssertionError, OSError, KeyError, subprocess.SubprocessError) as error:
        report['result'] = 'failed'
        report['error'] = str(error)
    destination = Path(args.report)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['result'] == 'passed' else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', default='target/stack/runtime.env')
    parser.add_argument('--project', default='logitrack-stack')
    parser.add_argument('--gateway', help='Por defecto utiliza GATEWAY_PORT del entorno local')
    parser.add_argument('--eureka', default='http://127.0.0.1:8065')
    parser.add_argument('--discovery-routes', action='store_true', help='Diagnostico explicito del Gateway en desarrollo')
    parser.add_argument('--allow-courier-stop', action='store_true')
    parser.add_argument('--cors-origin', default='http://localhost:3000')
    parser.add_argument('--report', default='target/stack/report.json')
    raise SystemExit(verify(parser.parse_args()))
