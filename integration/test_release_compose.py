"""Contrato de orquestación: no necesita un daemon ni publica imágenes."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from prepare_release import prepare

class ReleaseComposeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory()
        cls.root=Path(cls.temp.name)
        prepare(cls.root,'fixtureowner','sprint2-20000101.1','a'*40)
        cls.env=cls.root/'runtime.env'
        cls.command=['docker','compose','--env-file',str(cls.env),'-f','compose.release.yaml','config','--format','json']
        cls.config=json.loads(subprocess.run(cls.command,capture_output=True,text=True,check=True).stdout)

    @classmethod
    def tearDownClass(cls):
        # Restaurar permisos solo de las claves descartables creadas por esta prueba.
        for name in ('private.pem','public.pem'):
            (cls.root/name).chmod(0o600)
        cls.temp.cleanup()

    def test_images_health_and_private_key_are_scoped(self):
        services=self.config['services']
        self.assertEqual(7,len(services))
        for name in ('shipment-service','courier-service','security-service','config-server'):
            app=services[name]
            self.assertNotIn('build',app)
            self.assertEqual(f'fixtureowner/logitrack-{name}:sprint2-20000101.1',app['image'])
            self.assertEqual('10001:10001',app['user'])
            self.assertTrue(app['healthcheck']['test'])
            mounts={entry['source'] for entry in app.get('secrets',[])}
            self.assertEqual(name=='security-service','jwt-private' in mounts)

    def test_databases_use_distinct_credentials_storage_and_networks(self):
        services=self.config['services']
        passwords=set()
        for prefix in ('shipment','courier','security'):
            db=services[prefix+'-db']
            app=services[prefix+'-service']
            credentials=db['environment']
            passwords.add(credentials['MYSQL_PASSWORD'])
            passwords.add(credentials['MYSQL_ROOT_PASSWORD'])
            self.assertEqual(credentials['MYSQL_PASSWORD'],app['environment']['DB_PASSWORD'])
            self.assertEqual({prefix+'-storage'},set(db['networks']))
            self.assertTrue(self.config['networks'][prefix+'-storage']['internal'])
            self.assertNotIn('ports',db)
        self.assertEqual(6,len(passwords))
        self.assertEqual(3,len(self.config['volumes']))

    def test_missing_database_secret_and_overwrite_are_rejected(self):
        values=self.env.read_text().splitlines()
        missing=self.root/'missing.env'
        missing.write_text('\n'.join(line for line in values if not line.startswith('SHIPMENT_DB_PASSWORD=')))
        command=self.command.copy()
        command[3]=str(missing)
        env={key:value for key,value in os.environ.items() if key!='SHIPMENT_DB_PASSWORD'}
        self.assertNotEqual(0,subprocess.run(command,capture_output=True,env=env).returncode)
        with self.assertRaises(ValueError):
            prepare(self.root,'fixtureowner','sprint2-20000101.1','a'*40)

if __name__=='__main__': unittest.main()
