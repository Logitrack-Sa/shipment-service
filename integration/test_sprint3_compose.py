"""Verifica los limites de red, credenciales y dependencias sin iniciar contenedores."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from prepare_sprint3 import create


class Sprint3ComposeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='logitrack-sprint3-')
        cls.root = Path(cls.temporary.name).resolve()
        create(cls.root, 'a' * 40)
        cls.env = cls.root/'runtime.env'
        result = subprocess.run(['docker', 'compose', '--env-file', str(cls.env), '-f',
                                 'deploy/docker-compose.yml', 'config', '--format', 'json'],
                                capture_output=True, text=True, check=True)
        cls.config = json.loads(result.stdout)

    @classmethod
    def tearDownClass(cls):
        for name in ('private.pem', 'public.pem'):
            (cls.root/name).chmod(0o600)
        # Solo se elimina la carpeta descartable validada de esta prueba.
        assert cls.root.name.startswith('logitrack-sprint3-')
        cls.temporary.cleanup()

    def test_nine_services_and_three_isolated_databases(self):
        services = self.config['services']
        self.assertEqual(9, len(services))
        self.assertEqual(3, len(self.config['volumes']))
        for name, service in services.items():
            self.assertNotIn('build', service)
            self.assertTrue(service['healthcheck']['test'])
            if name.endswith('-db'):
                self.assertNotIn('ports', service)
                self.assertTrue(self.config['networks'][next(iter(service['networks']))]['internal'])

    def test_external_business_access_is_only_through_gateway(self):
        services = self.config['services']
        for name in ('shipment-service', 'courier-service', 'security-service'):
            self.assertNotIn('ports', services[name])
        self.assertEqual(8080, services['api-gateway']['ports'][0]['target'])
        for service in services.values():
            for port in service.get('ports', []):
                self.assertEqual('127.0.0.1', port['host_ip'])

    def test_startup_and_discovery_do_not_use_a_fixed_courier_url(self):
        services = self.config['services']
        self.assertEqual('service_healthy', services['eureka-server']['depends_on']['config-server']['condition'])
        for name in ('shipment-service', 'courier-service', 'security-service', 'api-gateway'):
            config = services[name]
            self.assertEqual('service_healthy', config['depends_on']['eureka-server']['condition'])
            self.assertEqual(name, config['environment']['SERVICE_HOST'])
            self.assertEqual('http://eureka-server:8065/eureka/', config['environment']['EUREKA_SERVER_URL'])
            self.assertEqual('prod,sprint3', config['environment']['SPRING_PROFILES_ACTIVE'])
        self.assertEqual('', services['shipment-service']['environment']['COURIER_SERVICE_URL'])
        for name in ('shipment-service', 'courier-service', 'security-service'):
            self.assertEqual('service_healthy', services['api-gateway']['depends_on'][name]['condition'])

    def test_private_signing_key_is_mounted_only_in_security(self):
        for name, config in self.config['services'].items():
            secrets = {entry['source'] for entry in config.get('secrets', [])}
            self.assertEqual(name == 'security-service', 'jwt-private' in secrets)
        with self.assertRaises(ValueError):
            create(self.root, 'a' * 40)


if __name__ == '__main__':
    unittest.main()
