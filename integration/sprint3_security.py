"""Escenarios de roles, pertenencia y CORS a traves del Gateway real."""
import secrets
from urllib.error import HTTPError
from urllib.request import Request, build_opener
from uuid import uuid4
from http_api import NoRedirect


def cors(gateway, origin):
    for candidate, expected in ((origin, 200), ('https://untrusted.example.test', 403)):
        query = Request(gateway + '/api/v1/shipments', method='OPTIONS', headers={
            'Origin': candidate, 'Access-Control-Request-Method': 'GET',
            'Access-Control-Request-Headers': 'authorization'})
        try:
            response = build_opener(NoRedirect()).open(query, timeout=15)
        except HTTPError as error:
            response = error
        with response:
            assert response.code == expected, f'Preflight CORS: esperado {expected}, recibido {response.code}'
            allowed = response.headers.get('Access-Control-Allow-Origin')
            assert allowed == origin if expected == 200 else allowed is None, 'Origen CORS incorrecto'


def roles(call, admin, customer, courier, shipment):
    tokens = {}
    identities = {
        'client': ('CLIENTE', 'customerId', customer['id']),
        'otherClient': ('CLIENTE', 'customerId', str(uuid4())),
        'courier': ('REPARTIDOR', 'courierId', courier['id']),
        'otherCourier': ('REPARTIDOR', 'courierId', str(uuid4()))}
    for actor, (role, link, identity) in identities.items():
        email, password = uuid4().hex+'@example.test', secrets.token_urlsafe(24)
        call('security-service', '/api/v1/users', 'POST',
             {'email': email, 'password': password, 'roles': [role], link: identity}, admin, 201)
        tokens[actor] = call('security-service', '/api/v1/auth/login', 'POST',
                             {'email': email, 'password': password})['accessToken']
        call('security-service', '/api/v1/auth/me', token=tokens[actor])
    path = '/api/v1/shipments/' + shipment['id']
    for suffix in (path, path+'/history', '/api/v1/shipments/tracking/'+shipment['trackingCode']):
        for actor in ('client', 'courier'):
            call('shipment-service', suffix, token=tokens[actor])
        for actor in ('otherClient', 'otherCourier'):
            call('shipment-service', suffix, token=tokens[actor], expected=403)
    for actor in ('client', 'courier'):
        call('security-service', '/api/v1/users', token=tokens[actor], expected=403)
        call('courier-service', '/api/v1/couriers', token=tokens[actor], expected=403)
        call('shipment-service', path+'/courier', 'PUT', {'courierId': courier['id']}, tokens[actor], 403)
    call('shipment-service', path+'/status', 'PATCH', {'status': 'EN_RUTA'}, tokens['client'], 403)
    call('shipment-service', path+'/status', 'PATCH', {'status': 'EN_RUTA'}, tokens['otherCourier'], 403)
    internal = '/api/v1/internal/couriers/'+courier['id']+'/status'
    call('courier-service', internal, token=tokens['courier'])
    call('courier-service', internal, token=tokens['otherCourier'], expected=403)
    return tokens


def delivery(call, admin, courier_token, shipment):
    path = '/api/v1/shipments/' + shipment['id']
    for state in ('EN_RUTA', 'ENTREGADO'):
        result = call('shipment-service', path+'/status', 'PATCH', {'status': state}, courier_token)
        assert result['status'] == state, 'No se aplico la transicion del envio'
    history = call('shipment-service', path+'/history', token=admin)
    assert [event['newStatus'] for event in history] == ['PENDIENTE', 'EN_RUTA', 'ENTREGADO']
    call('shipment-service', path+'/status', 'PATCH', {'status': 'EN_RUTA'}, admin, 422)
    assert call('shipment-service', path+'/history', token=admin) == history, 'Se altero un estado terminal'
