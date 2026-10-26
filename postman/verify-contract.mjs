import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { fixture } from './contract-fixture.mjs';

const require = createRequire(import.meta.url);
const newman = require(process.env.NEWMAN_MODULE ?? 'newman');
const collection = JSON.parse(await readFile(new URL('./LogiTrack_Sprint2.postman_collection.json', import.meta.url)));
const environment = JSON.parse(await readFile(new URL('./LogiTrack_Sprint2_Local.postman_environment.json', import.meta.url)));
for (const variable of environment.values) {
  if (variable.key.endsWith('_password') || variable.key.endsWith('_token')) {
    if (variable.value) throw new Error('El entorno exportado debe excluir credenciales y tokens');
  }
}
const password = randomUUID();
const server = fixture(password);
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
try {
  for (const value of environment.values) {
    if (value.key.endsWith('_service_url') || value.key === 'config_server_url') value.value = `http://127.0.0.1:${server.address().port}`;
    if (value.key === 'admin_username') value.value = 'fixture_admin';
    if (value.key === 'admin_password') value.value = password;
  }
  const summary = await new Promise((resolve, reject) => newman.run({ collection, environment, iterationCount: 2, reporters: [], timeoutRequest: 5000 }, (error, result) => error ? reject(error) : resolve(result)));
  console.log(JSON.stringify({ mode: 'http-contract-fixtures', iterations: 2, requests: summary.run.stats.requests.total, assertions: summary.run.stats.assertions.total, failed: summary.run.failures.length }));
  for (const failure of summary.run.failures) console.log(`${failure.source?.name}: ${failure.error?.name} / ${failure.error?.test ?? 'petición'}`);
  if (summary.run.failures.length) process.exitCode = 1;
} finally {
  await new Promise(resolve => server.close(resolve));
}
