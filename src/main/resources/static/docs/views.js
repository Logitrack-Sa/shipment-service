import { groups, resolve, typeName, example, bodySchema, responseSchema, parameters, requestUrl, curl } from "./api.js";
import { icons, errorCodes, requestDescriptions } from "./content.js";

export const escape = value => String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
export const icon = name => `<svg viewBox="0 0 24 24" aria-hidden="true">${icons[name] || icons.book}</svg>`;
export const operationHref = operation => `#api/${encodeURIComponent(operation.id)}`;
export const badge = method => `<span class="method ${escape(method)}">${escape(method.toUpperCase())}</span>`;

function highlightedJson(data) {
  return JSON.stringify(data, null, 2).replace(/"(?:\\.|[^"\\])*"(?:\s*:)?|\b(?:true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g, token => {
    const kind = token.startsWith('"') ? (token.endsWith(":") ? "key" : "string") : /^-?\d/.test(token) ? "number" : "literal";
    return `<span class="json-${kind}">${escape(token)}</span>`;
  });
}

export function codePanel(id, title, data, language = "JSON", footer = "") {
  const code = language === "JSON" ? highlightedJson(data) : escape(data);
  return `<div class="code-panel"><div class="code-toolbar"><span class="code-label">${icon("terminal")}${escape(title)}</span><div class="code-actions"><span class="code-language">${language}</span><button class="copy-button" data-copy="${id}" aria-label="Copiar ${escape(title)}">${icon("copy")} Copiar</button></div></div><pre><code id="${id}">${code}</code></pre>${footer ? `<div class="code-footer">${escape(footer)}</div>` : ""}</div>`;
}

export function hero(title, description, category = "Guía de integración", meta = "") {
  return `<div class="breadcrumbs"><a href="#introduction">Documentación</a><span>/</span><span>${escape(category)}</span></div><header class="hero"><p class="eyebrow">LOGITRACK · API DE ENVÍOS</p><h1 tabindex="-1">${escape(title)}</h1><p class="lead">${escape(description)}</p>${meta}</header>`;
}

const section = (id, title, content) => `<section class="section" id="${id}"><h2>${title}</h2>${content}</section>`;
const callout = (title, text) => `<div class="callout"><span aria-hidden="true">ⓘ</span><p><strong>${title}</strong> ${text}</p></div>`;
const endpointRow = operation => `<a class="endpoint-row" href="${operationHref(operation)}">${badge(operation.method)}<span class="endpoint-name">${escape(operation.label)}</span><code>${escape(operation.path)}</code><span class="arrow" aria-hidden="true">→</span></a>`;
export const footer = (label = "Ver referencia API", href = "#reference") => `<footer class="page-footer"><span>LogiTrack · Documentación de la API</span><a class="next-page" href="${href}">${label}<span aria-hidden="true">→</span></a></footer>`;

function operationFor(operations, method, resource) {
  return operations.find(operation => operation.method === method && operation.path === `/api/v1/${resource}`);
}

export function introduction(spec, operations) {
  const create = operationFor(operations, "post", "customers");
  const sample = example(spec, bodySchema(create));
  return hero("Tu operación de envíos, conectada.", "Integra clientes, repartidores y envíos en un solo lugar. Una API REST clara, con seguimiento único y tarifas calculadas para cada paquete.", "Introducción",
    `<div class="hero-meta"><span class="meta-badge">API v${escape(spec.info.version)}</span><span class="meta-divider"></span><span>${operations.length} endpoints</span><span>·</span><span>JSON sobre HTTP</span></div>`) +
    section("resources", "Una API, todo el recorrido", `<p class="section-description">Tres recursos para conectar cada parte de tu operación.</p><div class="resource-grid">${groups.map(group => {
      const list = operations.filter(operation => operation.group === group.key);
      return `<a class="resource-card" href="${operationHref(list.find(operation => operation.method === "post") || list[0])}"><span class="resource-icon">${icon(group.icon)}</span><h3>${group.label}</h3><p>${group.description}</p><span class="card-link">${list.length} endpoints <span aria-hidden="true">→</span></span></a>`;
    }).join("")}</div>`) +
    section("first-request", "Tu primera solicitud", `<p>Empieza registrando un cliente. Su identificador será el punto de partida para crear el primer envío.</p>${codePanel("first-curl", "Registrar un cliente", curl(create, `${location.origin}${create.path}`, sample), "cURL")}${codePanel("first-response", "Ejemplo de respuesta", example(spec, responseSchema(create)), "JSON", "201 Created · El ID identifica al cliente en las siguientes solicitudes.")}${callout("Prueba desde aquí.", `Abre <a href="${operationHref(create)}">Registrar un cliente</a> para editar el JSON, enviar la solicitud y consultar la respuesta real.`)}`) +
    section("conventions", "Convenciones de la API", `<div class="table-wrap"><table><thead><tr><th>CONVENCIÓN</th><th>QUÉ NECESITAS SABER</th></tr></thead><tbody><tr><td>URL base</td><td><code>${escape(location.origin)}/api/v1</code></td></tr><tr><td>Formato</td><td>Envía y recibe JSON con <code>Content-Type: application/json</code>.</td></tr><tr><td>Identificadores</td><td>Los recursos utilizan UUID. Conserva el <code>id</code> devuelto al registrarlos.</td></tr><tr><td>Paginación</td><td>Las páginas comienzan en <code>0</code>. Usa <code>page</code> y <code>size</code> al listar recursos.</td></tr><tr><td>Fechas e importes</td><td>Las fechas usan ISO 8601. Las tarifas se expresan en pesos y los importes finales tienen dos decimales.</td></tr></tbody></table></div>`) +
    section("next-steps", "Continúa integrando", `<p>Recorre el flujo completo: registra un cliente, crea un envío y asigna un repartidor disponible.</p><a class="outline-button" href="#quickstart">Ver el flujo de integración <span aria-hidden="true">→</span></a>`) + footer();
}

export function quickstart(spec, operations) {
  const steps = [
    ["post", "customers", "Registra al cliente", "Conserva el UUID del cliente. Lo necesitarás en customerId al registrar el envío."],
    ["post", "couriers", "Registra un repartidor", "Indica su nombre y tipo de vehículo. Un repartidor nuevo queda ACTIVO."],
    ["post", "shipments", "Crea el envío", "Incluye el cliente, las direcciones, el peso y las medidas. Recibirás tarifa, tracking y estado PENDIENTE."],
    ["put", "shipments/{id}/courier", "Asigna al repartidor", "Usa el ID del envío en la ruta y el UUID del repartidor en courierId."]
  ];
  return hero("De cliente a envío asignado", "Sigue el recorrido de integración paso a paso. Cada respuesta te entrega los identificadores necesarios para continuar.", "Primeros pasos") +
    section("integration-flow", "El flujo de integración", `<ol class="steps">${steps.map(([method, path, title, text]) => `<li><h3>${title}</h3><p>${text}</p><a href="${operationHref(operationFor(operations, method, path))}">${badge(method)} <span class="inline-code">/api/v1/${path}</span> →</a></li>`).join("")}</ol>`) +
    section("fee", "Cómo se calcula la tarifa", `<p>El peso facturable es el mayor entre el peso real y el volumétrico. El cálculo usa las medidas del paquete y la configuración del servicio.</p>${codePanel("fee-formula", "Regla de cálculo", "peso volumétrico = (ancho × alto × largo) / divisor\npeso facturable = max(peso real, peso volumétrico)\ntarifa = base + (peso facturable × precio por kg)", "Fórmula")}<p>Valores predeterminados: base de <code>1000,00</code>, precio de <code>250,00</code> por kg y divisor de <code>5000</code>. Un paquete de 2,5 kg y 30 × 20 × 40 cm tiene 4,8 kg facturables y una tarifa de 2200,00.</p>${callout("Unidades.", "Peso en kilogramos y medidas en centímetros. La tarifa se redondea al final a dos decimales; los valores de base, precio y divisor son configurables.")}`) +
    section("rules", "Antes de asignar", `<p>El envío debe permanecer <code>PENDIENTE</code> y el repartidor debe estar <code>ACTIVO</code>. Puedes reasignar un envío mientras cumpla esas condiciones.</p><p>La asignación no modifica el estado del envío. Si una regla no se cumple, la API devuelve <code>422</code> con el código de error correspondiente.</p>`) + footer();
}

export function reference(operations) {
  return hero("Referencia de la API", "Encuentra cada operación, sus parámetros y ejemplos. Prueba solicitudes reales sin salir de la documentación.", "Referencia API") +
    [...groups, { key: "other", label: "Otros recursos", description: "Operaciones adicionales publicadas por el servicio." }].map(group => {
      const list = operations.filter(operation => operation.group === group.key);
      return list.length ? section(group.key, group.label, `<p class="section-description">${group.description}</p>${list.map(endpointRow).join("")}`) : "";
    }).join("") + footer("Ver modelos de datos", "#models");
}

function constraints(schema) {
  return [schema.enum?.length ? `Valores: ${schema.enum.join(", ")}` : "", schema.maxLength ? `Hasta ${schema.maxLength} caracteres` : "",
    schema.minimum !== undefined ? `Mínimo: ${schema.minimum}` : "", schema.exclusiveMinimum === true ? "Mayor que el mínimo indicado" : "",
    schema.maximum !== undefined ? `Máximo: ${schema.maximum}` : "", schema.pattern ? `Patrón: ${schema.pattern}` : ""].filter(Boolean).join(" · ");
}

export function fieldsTable(spec, rawSchema) {
  const schema = resolve(spec, rawSchema);
  const properties = schema.properties || {};
  if (!Object.keys(properties).length) return `<p>Tipo de dato: <code>${escape(typeName(rawSchema))}</code>.</p>`;
  return `<div class="table-wrap"><table><thead><tr><th>CAMPO</th><th>TIPO</th><th>DESCRIPCIÓN</th></tr></thead><tbody>${Object.entries(properties).map(([name, raw]) => {
    const field = resolve(spec, raw);
    const type = typeName(raw);
    const typeLink = raw.$ref ? `<a href="#models/${encodeURIComponent(type)}">${escape(type)}</a>` : escape(type);
    return `<tr><td><span class="field-name">${escape(name)}</span><span class="${schema.required?.includes(name) ? "required" : "optional"}">${schema.required?.includes(name) ? "obligatorio" : "opcional"}</span></td><td><span class="type-label">${typeLink}</span></td><td>${escape(field.description || field.format || "—")}<div class="constraints">${escape(constraints(field))}</div></td></tr>`;
  }).join("")}</tbody></table></div>`;
}

export function operationView(spec, operation) {
  const requestSchema = bodySchema(operation);
  const params = parameters(spec, operation);
  const description = requestDescriptions[`${operation.method} ${operation.path}`] || operation.description || operation.summary || "Consulta los parámetros y la respuesta de esta operación.";
  const descriptionText = description.replace(/\s+/g, " ").trim();
  const group = groups.find(group => group.key === operation.group)?.label || "Referencia API";
  const body = requestSchema ? example(spec, requestSchema) : undefined;
  const requestParameters = params.map(parameter => ({ ...parameter, value: parameter.in === "query" ? String(parameter.schema?.default ?? (parameter.name === "page" ? 0 : parameter.name === "size" ? 20 : "")) : "" }));
  const previewUrl = requestUrl(operation, requestParameters, true);
  return hero(operation.label, descriptionText, group, `<div class="endpoint-line">${badge(operation.method)}<span><span class="base-url">${escape(location.origin)}</span>${escape(operation.path)}</span></div>`) +
    (params.length ? section("parameters", "Parámetros", `<div class="table-wrap"><table><thead><tr><th>PARÁMETRO</th><th>UBICACIÓN</th><th>DETALLE</th></tr></thead><tbody>${params.map(parameter => `<tr><td><span class="field-name">${escape(parameter.name)}</span>${parameter.required ? '<span class="required">obligatorio</span>' : ""}<div class="type-label">${escape(typeName(parameter.schema))}</div></td><td>${parameter.in === "path" ? "Ruta" : "Consulta"}</td><td>${escape(parameter.description || (parameter.in === "path" ? "Identificador del recurso que quieres consultar o modificar." : "Parámetro opcional de la consulta."))}<div class="constraints">${escape(constraints(resolve(spec, parameter.schema)))}</div></td></tr>`).join("")}</tbody></table></div>`) : "") +
    (requestSchema ? section("request-body", "Cuerpo de la solicitud", `<p class="section-description">Envía estos campos en formato JSON. Los objetos anidados se pueden consultar en sus modelos.</p>${fieldsTable(spec, requestSchema)}`) : "") +
    section("try-request", "Probar este endpoint", `<p>Edita los valores y envía una solicitud al servicio en ejecución.</p><form class="request-card" id="request-form"><div class="request-heading"><h3>Configura tu solicitud</h3><span>Entorno actual · ${escape(location.host)}</span></div>${params.length ? `<div class="request-fields">${requestParameters.map((parameter, index) => {
      const enumeration = resolve(spec, parameter.schema).enum;
      return `<label for="parameter-${index}">${escape(parameter.name)} <small>${parameter.in === "path" ? "Parámetro de ruta · obligatorio" : "Parámetro de consulta"}</small>${enumeration ? `<select id="parameter-${index}" data-parameter="${index}"><option value="">Sin filtro</option>${enumeration.map(value => `<option>${escape(value)}</option>`).join("")}</select>` : `<input id="parameter-${index}" data-parameter="${index}" value="${escape(parameter.value)}" ${parameter.required ? "required" : ""} placeholder="${parameter.in === "path" ? "Introduce el identificador real" : "Opcional"}" autocomplete="off">`}</label>`;
    }).join("")}</div>` : ""}${requestSchema ? `<label class="body-label" for="request-body-editor">Cuerpo JSON</label><textarea class="body-editor" id="request-body-editor" spellcheck="false" aria-label="Cuerpo JSON de la solicitud">${escape(JSON.stringify(body, null, 2))}</textarea>` : ""}<div class="request-actions"><button class="primary-button" type="submit" id="send-request">Enviar solicitud <span aria-hidden="true">→</span></button><span class="request-note">Se ejecutará una solicitud real en este entorno.</span></div><p class="request-error" id="request-error" role="alert" hidden></p></form>${codePanel("request-curl", "Solicitud", curl(operation, previewUrl, body), "cURL")}<div id="live-response" aria-live="polite"></div>`) +
    section("responses", "Respuestas", `<div class="table-wrap"><table><thead><tr><th>HTTP</th><th>DESCRIPCIÓN</th></tr></thead><tbody>${Object.entries(operation.responses || {}).map(([status, response]) => `<tr><td><code>${escape(status)}</code></td><td>${escape(response.description || "Respuesta del servicio")}</td></tr>`).join("")}</tbody></table></div>${responseSchema(operation) ? codePanel("response-example", "Ejemplo de respuesta", example(spec, responseSchema(operation)), "JSON", "Ejemplo orientativo. Los identificadores y las fechas se generan al ejecutar la solicitud.") : ""}<p class="section-description">Los errores siguen un formato común. Consulta <a href="#errors">el significado de cada código</a>.</p>`) + footer();
}

export function modelsView(spec, selected) {
  const names = Object.keys(spec.components.schemas).sort();
  const name = names.includes(selected) ? selected : names.includes("ShipmentResponse") ? "ShipmentResponse" : names[0];
  const schema = spec.components.schemas[name];
  return hero("Modelos de datos", "Consulta la estructura de las solicitudes y respuestas publicadas por la API. Los campos y restricciones se obtienen de OpenAPI.", "Modelos") +
    `<nav class="model-tabs" aria-label="Seleccionar modelo">${names.map(item => `<a href="#models/${encodeURIComponent(item)}" class="model-tab ${name === item ? "active" : ""}" ${name === item ? 'aria-current="page"' : ""}>${escape(item)}</a>`).join("")}</nav>` +
    section("model-fields", escape(name), `${schema.description ? `<p>${escape(schema.description)}</p>` : ""}${fieldsTable(spec, schema)}`) +
    section("model-example", "Ejemplo JSON", codePanel("model-json", name, example(spec, schema))) + footer();
}

export function errorsView() {
  const sample = { timestamp: "2026-10-19T12:00:00Z", status: 422, error: "Unprocessable Entity", code: "COURIER_NOT_ACTIVE", message: "El repartidor no está activo", path: "/api/v1/shipments/550e8400-e29b-41d4-a716-446655440000/courier" };
  return hero("Errores claros, respuestas predecibles", "Usa el estado HTTP y el código de negocio para entender qué ocurrió y guiar el siguiente paso de tu integración.", "Manejo de errores") +
    section("error-format", "Un formato común", `<p>Cuando una solicitud falla, la API devuelve <code>ErrorResponse</code>. El campo <code>code</code> es estable: úsalo para manejar el error sin depender del texto de <code>message</code>.</p>${codePanel("error-json", "Ejemplo de error de negocio", sample)}${callout("Detalles de validación.", "Cuando corresponde, la respuesta incluye un arreglo <code>details</code> con el <code>field</code> y el <code>message</code> de cada campo inválido.")}`) +
    section("error-codes", "Códigos y significados", `<div class="table-wrap"><table><thead><tr><th>HTTP</th><th>CÓDIGO</th><th>SIGNIFICADO</th></tr></thead><tbody>${errorCodes.map(([status, code, description]) => `<tr><td><code>${status}</code></td><td><span class="error-code">${code}</span></td><td>${description}</td></tr>`).join("")}</tbody></table></div>`) +
    section("error-handling", "Cómo manejarlos", `<p>Valida los datos antes de enviarlos. Ante un <code>404</code>, revisa el identificador; ante un <code>409</code>, consulta el recurso actual; y ante un <code>422</code>, corrige la condición de negocio antes de repetir la operación.</p><p>No repitas automáticamente una operación que crea datos sin comprobar antes el resultado de la solicitud original.</p>`) + footer();
}
