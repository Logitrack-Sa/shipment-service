// Fixture HTTP de contrato para probar la colección. No es un microservicio ni valida criptografía.
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';

export function fixture(adminPassword) {
  const users = new Map(), customers = new Map(), couriers = new Map(), shipments = new Map(), tokens = new Map();
  const admin = { id: randomUUID(), username: 'fixture_admin', password: adminPassword, roles: ['ADMIN'] };
  users.set(admin.id, admin);
  const fail = (status, code) => { throw Object.assign(new Error(code), { status }); };
  const find = (map, id) => map.get(id) ?? fail(404, 'RESOURCE_NOT_FOUND');
  const clean = ({ password, ...user }) => user;
  return createServer(async (request, response) => {
    let status = 200;
    let body;
    try {
      const url = new URL(request.url, 'http://fixture');
      const path = url.pathname;
      let raw = '';
      for await (const chunk of request) raw += chunk;
      if (raw.includes('{{') || path.includes('%7B')) fail(400, 'VARIABLE_NOT_RESOLVED');
      const data = raw ? JSON.parse(raw) : {};
      const actor = tokens.get(request.headers.authorization?.replace(/^Bearer /, ''));
      const requireAdmin = () => {
        if (!actor) fail(401, 'UNAUTHORIZED');
        if (!actor.roles.includes('ADMIN')) fail(403, 'ACCESS_DENIED');
      };
      const readable = shipment => {
        if (!actor) fail(401, 'UNAUTHORIZED');
        if (!actor.roles.includes('ADMIN') && shipment.customerId !== actor.customerId && shipment.courierId !== actor.courierId) fail(403, 'ACCESS_DENIED');
        return shipment;
      };
      if (path === '/actuator/health') body = { status: 'UP' };
      else if (path === '/api/v1/auth/register' && request.method === 'POST') {
        if (data.customerId || data.courierId || data.role && data.role !== 'CLIENTE' || data.roles?.some(r => r !== 'CLIENTE')) fail(400, 'PRIVILEGE_ESCALATION');
        if ([...users.values()].some(u => u.username === data.username || u.email === data.email)) fail(409, 'DUPLICATE_RESOURCE');
        const user = { ...data, id: randomUUID(), roles: ['CLIENTE'] };
        users.set(user.id, user); body = clean(user); status = 201;
      } else if (path === '/api/v1/auth/login' && request.method === 'POST') {
        const user = [...users.values()].find(u => data.username && u.username === data.username || data.email && u.email === data.email);
        if (!user || user.password !== data.password) fail(401, 'UNAUTHORIZED');
        const token = ['fixture', Buffer.from(JSON.stringify({ sub: user.id, roles: user.roles })).toString('base64url'), randomUUID()].join('.');
        tokens.set(token, structuredClone(user)); body = { accessToken: token, tokenType: 'Bearer', expiresIn: 600 };
      } else if (path === '/api/v1/auth/me') {
        if (!actor) fail(401, 'UNAUTHORIZED');
        body = clean(find(users, actor.id));
      } else if (path === '/api/v1/users' && request.method === 'POST') {
        requireAdmin(); const user = { ...data, id: randomUUID() }; users.set(user.id, user); body = clean(user); status = 201;
      } else if (/^\/api\/v1\/users\/[^/]+\/customer-link$/.test(path)) {
        requireAdmin(); const user = find(users, path.split('/')[4]); user.customerId = data.customerId; body = clean(user);
      } else if (path.startsWith('/api/v1/customers')) {
        requireAdmin();
        if (request.method === 'POST') {
          body = { ...data, id: randomUUID() }; customers.set(body.id, body); status = 201;
        } else body = path === '/api/v1/customers' ? { content: [...customers.values()] } : find(customers, path.split('/')[4]);
      } else if (path.startsWith('/api/v1/internal/couriers/')) {
        if (!actor) fail(401, 'UNAUTHORIZED');
        const id = path.split('/')[5];
        if (!actor.roles.includes('ADMIN') && !(actor.roles.includes('REPARTIDOR') && actor.courierId === id)) fail(403, 'ACCESS_DENIED');
        body = { courierId: id, status: find(couriers, id).status };
      } else if (path.startsWith('/api/v1/couriers')) {
        requireAdmin();
        const id = path.split('/')[4];
        if (request.method === 'POST') {
          body = { ...data, id: randomUUID(), status: 'ACTIVO' }; couriers.set(body.id, body); status = 201;
        } else if (!id) body = { content: [...couriers.values()] };
        else {
          const courier = find(couriers, id);
          if (request.method === 'PATCH') courier.status = data.status;
          if (request.method === 'PUT') { courier.name = data.name; courier.vehicleType = data.vehicleType; }
          body = path.endsWith('/availability') ? { courierId: id, status: courier.status, available: courier.status === 'ACTIVO' } : courier;
        }
      } else if (path.startsWith('/api/v1/shipments')) {
        if (!actor) fail(401, 'UNAUTHORIZED');
        const admin = actor.roles.includes('ADMIN');
        const segment = path.split('/')[4];
        if (path === '/api/v1/shipments' && request.method === 'POST') {
          if (!admin && !(actor.roles.includes('CLIENTE') && actor.customerId === data.customerId)) fail(403, 'ACCESS_DENIED');
          find(customers, data.customerId);
          body = { ...data, id: randomUUID(), trackingCode: 'LT-' + randomUUID().replaceAll('-', '').slice(0, 10), status: 'PENDIENTE', courierId: null,
            fee: 1000 + Math.max(data.weightKg, data.widthCm * data.heightCm * data.lengthCm / 5000) * 250,
            history: [{ newStatus: 'PENDIENTE' }] };
          shipments.set(body.id, body); status = 201;
        } else if (path === '/api/v1/shipments') {
          const rows = [...shipments.values()].filter(s => admin || s.customerId === actor.customerId || s.courierId === actor.courierId);
          body = { content: rows.map(({ history, ...s }) => s), totalElements: rows.length, totalPages: 1, page: 0, size: 100 };
        } else {
          const shipment = readable(segment === 'tracking' ? [...shipments.values()].find(s => s.trackingCode === path.split('/')[5]) ?? fail(404, 'RESOURCE_NOT_FOUND') : find(shipments, segment));
          if (path.endsWith('/courier')) {
            requireAdmin(); if (request.method !== 'PUT') fail(405, 'METHOD_NOT_ALLOWED');
            if (find(couriers, data.courierId).status !== 'ACTIVO') fail(422, 'COURIER_NOT_ACTIVE');
            if (shipment.status !== 'PENDIENTE') fail(422, 'BUSINESS_RULE_VIOLATION');
            shipment.courierId = data.courierId;
          } else if (path.endsWith('/cancellation')) {
            requireAdmin(); if (shipment.status !== 'PENDIENTE') fail(422, 'SHIPMENT_CANNOT_BE_CANCELLED');
            shipment.status = 'CANCELADO'; shipment.history.push({ newStatus: 'CANCELADO' });
          } else if (path.endsWith('/status') && request.method === 'PATCH') {
            if (!admin && !(actor.roles.includes('REPARTIDOR') && actor.courierId === shipment.courierId)) fail(403, 'ACCESS_DENIED');
            if (!(shipment.status === 'PENDIENTE' && ['EN_RUTA','CANCELADO'].includes(data.status) || shipment.status === 'EN_RUTA' && data.status === 'ENTREGADO')) fail(422, 'INVALID_STATE_TRANSITION');
            if (data.status === 'EN_RUTA' && !shipment.courierId) fail(422, 'COURIER_NOT_ASSIGNED');
            shipment.status = data.status; shipment.history.push({ newStatus: data.status });
          }
          body = path.endsWith('/history') ? shipment.history : shipment;
        }
        if (!Array.isArray(body) && body?.history) { const { history, ...view } = body; body = view; }
      } else fail(404, 'RESOURCE_NOT_FOUND');
    } catch (error) { status = error.status ?? 500; body = { status, code: error.message, message: 'Error de contrato simulado' }; }
    response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(body));
  });
}
