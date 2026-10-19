const methods = new Set(["get", "post", "put", "patch", "delete", "head", "options"]);

export const groups = [
  { key: "customers", label: "Clientes", description: "Registra y consulta a quienes confían sus envíos a LogiTrack.", icon: "users" },
  { key: "couriers", label: "Repartidores", description: "Gestiona repartidores y su disponibilidad para recibir asignaciones.", icon: "route" },
  { key: "shipments", label: "Envíos", description: "Registra paquetes, calcula tarifas y consulta cada envío por su tracking.", icon: "box" }
];

const labels = {
  "post /api/v1/customers": "Registrar un cliente", "get /api/v1/customers": "Listar clientes",
  "get /api/v1/customers/{id}": "Consultar un cliente", "post /api/v1/couriers": "Registrar un repartidor",
  "get /api/v1/couriers": "Listar repartidores", "get /api/v1/couriers/{id}": "Consultar un repartidor",
  "patch /api/v1/couriers/{id}/status": "Cambiar disponibilidad", "post /api/v1/shipments": "Registrar un envío",
  "get /api/v1/shipments": "Listar envíos", "get /api/v1/shipments/{id}": "Consultar por ID",
  "get /api/v1/shipments/tracking/{trackingCode}": "Consultar por tracking",
  "put /api/v1/shipments/{id}/courier": "Asignar un repartidor"
};

export async function loadSpecification() {
  const response = await fetch("/v3/api-docs", { headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error("No se pudo leer la especificación OpenAPI.");
  const spec = await response.json();
  if (!spec.paths || !spec.components?.schemas) throw new Error("La especificación no contiene rutas o modelos.");
  return spec;
}

export function operations(spec) {
  return Object.entries(spec.paths).flatMap(([path, item]) => Object.entries(item)
    .filter(([method]) => methods.has(method))
    .map(([method, definition]) => ({
      ...definition, method, path,
      id: `${method}-${path.replace(/^\/api\/v1\//, "").replace(/[{}]/g, "").replace(/\//g, "-")}`,
      group: groups.find(group => path.startsWith(`/api/v1/${group.key}`))?.key || "other",
      label: labels[`${method} ${path}`] || definition.summary || path,
      parameters: [...(item.parameters || []), ...(definition.parameters || [])]
    }))).sort((a, b) => a.path.localeCompare(b.path) || (a.method === "post" ? -1 : b.method === "post" ? 1 : 0));
}

export function resolve(spec, schema = {}) {
  if (schema.$ref) {
    const keys = schema.$ref.replace(/^#\//, "").split("/").map(key => key.replace(/~1/g, "/").replace(/~0/g, "~"));
    let value = spec;
    for (const key of keys) value = value && Object.hasOwn(value, key) ? value[key] : undefined;
    return value || {};
  }
  if (schema.allOf) return schema.allOf.reduce((result, part) => {
    const resolved = resolve(spec, part);
    return { ...result, ...resolved, properties: { ...result.properties, ...resolved.properties },
      required: [...(result.required || []), ...(resolved.required || [])] };
  }, {});
  return schema;
}

export function typeName(schema = {}) {
  if (schema.$ref) return schema.$ref.split("/").at(-1);
  if (schema.type === "array") return `${typeName(schema.items)}[]`;
  return schema.format || (Array.isArray(schema.type) ? schema.type.filter(type => type !== "null").join(" | ") : schema.type) || "object";
}

const values = {
  id: "550e8400-e29b-41d4-a716-446655440000", customerId: "550e8400-e29b-41d4-a716-446655440000",
  courierId: "ef2c6c90-8b21-4a31-970e-4d302c642914", name: "María González", email: "maria@example.com",
  phone: "+5493881234567", vehicleType: "MOTO", street: "Av. Bolivia", number: "1239",
  city: "San Salvador de Jujuy", postalCode: "4600", trackingCode: "LT-A1B2C3D4E5", fee: 2200,
  weightKg: 2.5, widthCm: 30, heightCm: 20, lengthCm: 40, page: 0, size: 20,
  totalElements: 1, totalPages: 1, createdBy: "system", lastModifiedBy: "system", version: 0
};

export function example(spec, rawSchema, field = "", depth = 0) {
  const schema = resolve(spec, rawSchema);
  if (depth > 5) return null;
  if (schema.example !== undefined) return schema.example;
  if (schema.default !== undefined) return schema.default;
  if (schema.enum?.length) return schema.enum[0];
  if (schema.type === "array") return [example(spec, schema.items, "", depth + 1)];
  if (schema.properties) return Object.fromEntries(Object.entries(schema.properties)
    .map(([key, value]) => [key, example(spec, value, key, depth + 1)]));
  const numeric = schema.type === "integer" || schema.type === "number";
  if (Object.hasOwn(values, field) && (!numeric || typeof values[field] === "number")) return values[field];
  if (schema.format === "date-time") return "2026-10-19T12:00:00Z";
  if (schema.format === "uuid") return values.id;
  if (numeric) return schema.minimum || 0;
  if (schema.type === "boolean") return false;
  return "string";
}

export function bodySchema(operation) {
  return operation.requestBody?.content?.["application/json"]?.schema;
}

export function responseSchema(operation) {
  const success = Object.entries(operation.responses || {}).find(([status]) => /^2/.test(status));
  const content = success?.[1].content || {};
  return (content["application/json"] || content["*/*"] || Object.values(content)[0])?.schema;
}

export function parameters(spec, operation) {
  const descriptions = {
    page: "Índice de página, comenzando en 0.",
    size: "Cantidad de elementos por página.",
    sort: "Campo y dirección separados por coma. Ejemplo: createdAt,desc."
  };
  return operation.parameters.flatMap(raw => {
    const resolved = resolve(spec, raw);
    const parameter = { ...resolved, description: descriptions[resolved.name] || resolved.description };
    const schema = resolve(spec, parameter.schema);
    if (parameter.in === "query" && schema.properties) {
      return Object.entries(schema.properties).map(([name, child]) => ({ ...parameter, name, schema: child, required: false }));
    }
    return [parameter];
  });
}

export function requestUrl(operation, entries, useExamples = false) {
  let path = operation.path;
  const query = new URLSearchParams();
  for (const parameter of entries) {
    const value = parameter.value?.trim() || (useExamples && parameter.in === "path" ? String(values[parameter.name] || values.id) : "");
    if (parameter.in === "path") {
      if (!value) throw new Error(`Completa el parámetro ${parameter.name} antes de enviar.`);
      path = path.replace(`{${parameter.name}}`, encodeURIComponent(value));
    } else if (parameter.in === "query" && value) {
      query.append(parameter.name, value);
    }
  }
  if (!path.startsWith("/api/v1/") || path.includes("{")) throw new Error("La ruta del endpoint no es válida.");
  return `${location.origin}${path}${query.size ? `?${query}` : ""}`;
}

export function curl(operation, url, body) {
  const quote = value => `'${value.replace(/'/g, `'\\''`)}'`;
  const lines = [`curl --request ${operation.method.toUpperCase()} ${quote(url)}`, "  --header 'Accept: application/json'"];
  if (body !== undefined) lines.push("  --header 'Content-Type: application/json'", `  --data ${quote(JSON.stringify(body, null, 2))}`);
  return lines.join(" \\\n");
}

export async function sendRequest(operation, url, body, signal) {
  const target = new URL(url);
  if (target.origin !== location.origin || !target.pathname.startsWith("/api/v1/")) throw new Error("Solo se pueden probar rutas de esta API.");
  const response = await fetch(target, {
    method: operation.method.toUpperCase(), signal,
    headers: { Accept: "application/json", ...(body !== undefined ? { "Content-Type": "application/json" } : {}) },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {})
  });
  const text = await response.text();
  let data = text;
  try { data = JSON.parse(text); } catch { /* Las respuestas sin JSON también se muestran. */ }
  return { status: response.status, ok: response.ok, data };
}
