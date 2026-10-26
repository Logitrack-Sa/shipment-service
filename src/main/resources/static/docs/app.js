import { loadSpecification, operations, groups, parameters, bodySchema, requestUrl, curl, sendRequest } from "./api.js";
import { sections } from "./content.js";
import { escape, icon, badge, operationHref, hero, introduction, quickstart, reference, operationView, modelsView, errorsView, codePanel } from "./views.js";

const main = document.getElementById("main");
const navigation = document.getElementById("navigation");
const search = document.getElementById("search");
const results = document.getElementById("search-results");
const menuToggle = document.getElementById("menu-toggle");
const overlay = document.getElementById("nav-overlay");
const health = document.getElementById("service-status");
let specification, endpoints = [], currentRoute = "", activeOperation, observer, pendingRequest, toastTimer;

const navLink = (route, label, symbol) => `<a class="nav-link" href="#${route}" data-route="${route}">${icon(symbol)}${label}</a>`;

function renderNavigation() {
  navigation.innerHTML = `<p class="nav-heading">PRIMEROS PASOS</p>${navLink("introduction", "Introducción", "book")}${navLink("quickstart", "Flujo de integración", "route")}<div class="sidebar-separator"></div><p class="nav-heading">REFERENCIA API</p>${navLink("reference", "Todas las operaciones", "layers")}${[...groups, { key: "other", label: "Otros recursos" }].map(group => {
    const list = endpoints.filter(endpoint => endpoint.group === group.key);
    return list.length ? `<details class="nav-group" open><summary>${escape(group.label)}<small>${list.length}</small></summary>${list.map(endpoint => `<a class="nav-link" data-route="api/${escape(endpoint.id)}" href="${operationHref(endpoint)}"><span>${escape(endpoint.label)}</span>${badge(endpoint.method)}</a>`).join("")}</details>` : "";
  }).join("")}<div class="sidebar-separator"></div><p class="nav-heading">RECURSOS</p>${navLink("models", "Modelos de datos", "layers")}${navLink("errors", "Códigos de error", "alert")}<a class="nav-link" href="/docs/explorer.html">${icon("terminal")}Explorador Swagger <span aria-hidden="true">↗</span></a>`;
}

function routeParts() {
  const [route, query = ""] = location.hash.replace(/^#\/?/, "").split("?");
  return { route: decodeURIComponent(route || "introduction"), section: new URLSearchParams(query).get("section") };
}

function renderRoute() {
  if (!specification) return;
  let target;
  try { target = routeParts(); } catch { target = { route: "introduction" }; }
  if (target.route === currentRoute && target.section) {
    document.getElementById(target.section)?.scrollIntoView();
    return;
  }
  pendingRequest?.abort();
  activeOperation = undefined;
  const [page, selected] = target.route.split("/");
  let html, title, category = page;
  if (page === "api") {
    activeOperation = endpoints.find(endpoint => endpoint.id === selected);
    if (activeOperation) { html = operationView(specification, activeOperation); title = activeOperation.label; category = "operation"; }
  } else if (page === "models") { html = modelsView(specification, selected); title = selected || "Modelos de datos"; }
  else if (page === "errors") { html = errorsView(); title = "Manejo de errores"; }
  else if (page === "quickstart") { html = quickstart(specification, endpoints); title = "Flujo de integración"; }
  else if (page === "reference") { html = reference(endpoints); title = "Referencia API"; }
  else if (page === "introduction") { html = introduction(specification, endpoints); title = "Introducción"; }
  if (!html) {
    html = hero("Esta página no está disponible", "Elige una operación del menú o vuelve a la introducción.") + '<p class="section"><a href="#introduction">Volver a la documentación →</a></p>';
    title = "Página no encontrada";
  }
  currentRoute = target.route;
  main.innerHTML = html;
  document.title = `${title} · LogiTrack Docs`;
  updateNavigation(page);
  renderPageIndex(category);
  closeSearch();
  setMobileNavigation(false);
  window.scrollTo(0, 0);
  if (target.section) document.getElementById(target.section)?.scrollIntoView();
  main.querySelector("h1")?.focus({ preventScroll: true });
}

function updateNavigation(page) {
  navigation.querySelectorAll("[data-route]").forEach(link => {
    const active = link.dataset.route === currentRoute || (page === "models" && link.dataset.route === "models");
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page"); else link.removeAttribute("aria-current");
  });
  const tab = page === "api" || page === "reference" ? "api" : page === "models" || page === "errors" ? page : "guide";
  document.querySelectorAll("[data-tab]").forEach(link => {
    const active = link.dataset.tab === tab;
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page"); else link.removeAttribute("aria-current");
  });
}

function renderPageIndex(category) {
  observer?.disconnect();
  const available = (sections[category] || []).filter(([id]) => document.getElementById(id));
  const index = document.getElementById("page-index");
  index.innerHTML = available.map(([id, title], position) => `<a href="#${encodeURI(currentRoute)}?section=${id}" data-section="${id}" class="${position === 0 ? "active" : ""}">${title}</a>`).join("");
  observer = new IntersectionObserver(entries => {
    const visible = entries.find(entry => entry.isIntersecting);
    if (visible) index.querySelectorAll("a").forEach(link => link.classList.toggle("active", link.dataset.section === visible.target.id));
  }, { rootMargin: "-15% 0px -65% 0px" });
  available.forEach(([id]) => observer.observe(document.getElementById(id)));
}

function searchEntries() {
  return [
    { label: "Introducción", route: "#introduction", detail: "Primeros pasos" },
    { label: "Flujo de integración", route: "#quickstart", detail: "JWT, cliente, envío y asignación por REST" },
    { label: "Códigos de error", route: "#errors", detail: "Respuestas HTTP y errores del negocio" },
    ...endpoints.map(endpoint => ({ label: endpoint.label, route: operationHref(endpoint), detail: `${endpoint.method.toUpperCase()} ${endpoint.path}` })),
    ...Object.keys(specification?.components.schemas || {}).map(name => ({ label: name, route: `#models/${encodeURIComponent(name)}`, detail: "Modelo de datos" }))
  ];
}

function updateSearch() {
  const query = search.value.trim().toLocaleLowerCase("es");
  if (!query) { closeSearch(); return; }
  const found = searchEntries().filter(entry => `${entry.label} ${entry.detail}`.toLocaleLowerCase("es").includes(query)).slice(0, 9);
  results.innerHTML = found.length ? found.map(entry => `<a href="${escape(entry.route)}">${escape(entry.label)}<small>${escape(entry.detail)}</small></a>`).join("") : '<p class="search-empty">No encontramos resultados para esa búsqueda.</p>';
  results.hidden = false;
}

function closeSearch() { results.hidden = true; }

function setMobileNavigation(open) {
  document.body.classList.toggle("nav-open", open);
  menuToggle.setAttribute("aria-expanded", String(open));
  menuToggle.setAttribute("aria-label", open ? "Cerrar navegación" : "Abrir navegación");
  overlay.hidden = !open;
}

function showToast(message) {
  const toast = document.getElementById("toast");
  clearTimeout(toastTimer);
  toast.textContent = message;
  toast.hidden = false;
  toastTimer = setTimeout(() => { toast.hidden = true; }, 2200);
}

function requestValues(form, operation) {
  const entries = parameters(specification, operation).map((parameter, index) => ({ ...parameter, value: form.querySelector(`[data-parameter="${index}"]`).value }));
  const editor = form.querySelector("textarea");
  let body;
  if (editor) {
    try { body = JSON.parse(editor.value); }
    catch { throw new Error("El cuerpo no es JSON válido. Revisa las comillas, las comas y las llaves."); }
  }
  return { entries, body };
}

function updateCurl(form) {
  try {
    const { entries, body } = requestValues(form, activeOperation);
    document.getElementById("request-curl").textContent = curl(activeOperation, requestUrl(activeOperation, entries, true), body);
  } catch { /* Conserva el ejemplo mientras se completa el formulario. */ }
}

async function submitRequest(event) {
  if (event.target.id !== "request-form") return;
  event.preventDefault();
  const form = event.target;
  const operation = activeOperation;
  const button = form.querySelector("button[type=submit]");
  const error = form.querySelector("#request-error");
  const output = document.getElementById("live-response");
  error.hidden = true;
  try {
    const { entries, body } = requestValues(form, operation);
    const url = requestUrl(operation, entries);
    const controller = new AbortController();
    pendingRequest = controller;
    const timeout = setTimeout(() => controller.abort(), 15000);
    button.disabled = true;
    button.textContent = "Enviando…";
    form.setAttribute("aria-busy", "true");
    const started = performance.now();
    let response;
    try { response = await sendRequest(operation, url, body, controller.signal, form.elements.namedItem("bearer-token").value.trim().replace(/^Bearer\s+/i, "")); }
    finally { clearTimeout(timeout); }
    if (!form.isConnected) return;
    const elapsed = Math.round(performance.now() - started);
    output.innerHTML = `<div class="response-meta"><span class="http-status ${response.ok ? "" : "error"}">HTTP ${response.status}</span><span>${elapsed} ms</span><span>Respuesta real del servicio</span></div>${codePanel("live-response-json", "Respuesta de tu solicitud", response.data, typeof response.data === "string" ? "Texto" : "JSON")}`;
    output.scrollIntoView({ behavior: "smooth", block: "nearest" });
  } catch (failure) {
    if (!form.isConnected) return;
    error.textContent = failure.name === "AbortError" ? "La solicitud superó el tiempo de espera. Comprueba el servicio antes de reintentar." : failure.message || "No se pudo conectar con el servicio.";
    error.hidden = false;
  } finally {
    button.disabled = false;
    button.innerHTML = 'Enviar solicitud <span aria-hidden="true">→</span>';
    form.removeAttribute("aria-busy");
  }
}

async function checkHealth() {
  health.disabled = true;
  try {
    const response = await fetch("/actuator/health", { signal: AbortSignal.timeout(10000) });
    const data = await response.json();
    const up = response.ok && data.status === "UP";
    health.classList.toggle("up", up);
    health.classList.toggle("down", !up);
    health.lastElementChild.textContent = up ? "Servicio disponible" : "Servicio no disponible";
  } catch {
    health.classList.remove("up"); health.classList.add("down");
    health.lastElementChild.textContent = "Sin conexión al servicio";
  } finally { health.disabled = false; }
}

async function initialize() {
  try {
    specification = await loadSpecification();
    endpoints = operations(specification);
    document.getElementById("version").textContent = `v${specification.info?.version || "1"}`;
    renderNavigation();
    currentRoute = "";
    renderRoute();
    await checkHealth();
  } catch (failure) {
    main.innerHTML = hero("No pudimos cargar la documentación", "La especificación OpenAPI debe estar disponible para mostrar las operaciones del servicio.") + `<p class="section">${escape(failure.message)}</p><button class="primary-button" id="retry-load">Volver a intentar</button>`;
  }
}

window.addEventListener("hashchange", renderRoute);
search.addEventListener("input", updateSearch);
document.querySelector(".search-box").addEventListener("click", event => {
  if (!event.target.closest("#search-results")) search.focus();
});
search.addEventListener("focus", () => { if (search.value.trim()) updateSearch(); });
search.addEventListener("keydown", event => {
  if (event.key === "Enter") { const first = results.querySelector("a"); if (first) { event.preventDefault(); first.click(); } }
});
menuToggle.addEventListener("click", () => setMobileNavigation(!document.body.classList.contains("nav-open")));
overlay.addEventListener("click", () => { setMobileNavigation(false); menuToggle.focus(); });
health.addEventListener("click", checkHealth);
main.addEventListener("submit", submitRequest);
main.addEventListener("input", event => { const form = event.target.closest("#request-form"); if (form) updateCurl(form); });
main.addEventListener("change", event => { const form = event.target.closest("#request-form"); if (form) updateCurl(form); });
document.addEventListener("click", async event => {
  if (event.target.closest(".skip-link")) {
    event.preventDefault();
    main.focus();
    main.scrollIntoView();
    return;
  }
  const copy = event.target.closest("[data-copy]");
  if (copy) {
    try { await navigator.clipboard.writeText(document.getElementById(copy.dataset.copy).textContent); showToast("Copiado al portapapeles"); }
    catch { showToast("No se pudo copiar. Puedes seleccionar el código y copiarlo manualmente."); }
  }
  if (!event.target.closest(".search-box")) closeSearch();
  if (event.target.closest("#search-results a")) { search.value = ""; closeSearch(); }
  if (event.target.closest("#retry-load")) initialize();
});
document.addEventListener("keydown", event => {
  const editing = event.target.matches("input, textarea, select, [contenteditable=true]");
  if ((event.key === "/" || ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k")) && !editing) { event.preventDefault(); search.focus(); }
  if (event.key === "Escape") { closeSearch(); if (document.body.classList.contains("nav-open")) { setMobileNavigation(false); menuToggle.focus(); } }
  if (event.key === "Tab" && document.body.classList.contains("nav-open")) {
    const focusable = [menuToggle, ...navigation.querySelectorAll("a, summary")].filter(element => element.offsetParent !== null);
    const boundary = event.shiftKey ? focusable[0] : focusable.at(-1);
    if (document.activeElement === boundary) { event.preventDefault(); (event.shiftKey ? focusable.at(-1) : focusable[0]).focus(); }
  }
});

initialize();
