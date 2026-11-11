// Generates the M1 administrative Postman collection and environment (DC-M1-04). Writes no secrets.
// Usage: node postman/build_m1_admin_collection.mjs
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const COLLECTION = join(HERE, "PRISALY-M1-Administracion.postman_collection.json");
const ENVIRONMENT = join(HERE, "PRISALY-M1-Administracion.postman_environment.json");
const ESTUDIANTE = "11111111-1111-1111-1111-111111111201"; // public Bruno student account (Usuario + Estudiante)
const DOCENTE_BRUNO = "11111111-1111-1111-1111-111111111101";
const GENERATED = ["tokenAdmin", "tokenDocente", "tokenEstudiante", "tokenNuevo", "idAdmin", "idNuevo", "correoNuevo",
  "idCrearCaso", "idGestionarSesion", "idPublicarCaso", "idConsultarCaso", "intentosOrigen"];

function checks(status, code, extra = "") {
  return [
    `pm.test("HTTP ${status}", () => pm.response.to.have.status(${status}));`,
    code ? `pm.test("código ${code}", () => pm.expect(pm.response.json().code).to.eql("${code}"));` : "",
    "if (pm.response.code >= 400) {",
    '  pm.test("error con contrato code/message", () => { const b = pm.response.json();',
    '    pm.expect(b.code).to.be.a("string"); pm.expect(b.message).to.be.a("string"); });',
    '  pm.test("sin detalles internos ni secretos", () => pm.expect(pm.response.text())',
    "    .to.not.match(/exception|jdbc:|org\\.(springframework|hibernate)|stacktrace|passwordhash|accessToken/i));",
    "}",
    extra.trim(),
  ].filter(Boolean).join("\n");
}

function request(id, title, method, path, { auth, body, pre = "", status, code, test = "", note = "" }) {
  const header = [];
  if (body !== undefined) header.push({ key: "Content-Type", value: "application/json", type: "text" });
  if (auth) header.push({ key: "Authorization", value: `Bearer {{${auth}}}`, type: "text" });
  const req = { method, auth: { type: "noauth" }, header, url: `{{baseUrl}}${path}`,
    description: `${note ? note + " " : ""}Esperado: HTTP ${status}${code ? " " + code : ""}.` };
  if (body !== undefined) req.body = { mode: "raw", raw: typeof body === "string" ? body : JSON.stringify(body, null, 2),
    options: { raw: { language: "json" } } };
  const created = [...pre.matchAll(/collectionVariables\.set\("(\w+)"/g)].map((m) => m[1]);
  const required = [...new Set([...JSON.stringify(req).matchAll(/{{(\w+)}}/g)].map((m) => m[1]))].filter((v) => !created.includes(v));
  const prerequest = [pre, `const requeridas = ${JSON.stringify(required)};`,
    'const faltan = requeridas.filter(k => !String(pm.variables.get(k) ?? "").trim());',
    'if (faltan.length) { console.warn("PRISALY M1: bloqueada por variables ausentes: " + faltan.join(", ")); pm.execution.skipRequest(); }',
  ].filter(Boolean).join("\n");
  return { name: `${id} | ${title}`, event: [
    { listen: "prerequest", script: { type: "text/javascript", exec: prerequest.split("\n") } },
    { listen: "test", script: { type: "text/javascript", exec: checks(status, code, test).split("\n") } }], request: req };
}

const login = (id, title, email, password, tokenVar, extra = "") => request(id, title, "POST", "/v1/personal/autenticar", {
  body: `{\n  "correo": "{{${email}}}",\n  "password": "{{${password}}}"\n}`, status: 200,
  test: `const t = pm.response.json().accessToken; pm.test("token recibido", () => pm.expect(t).to.be.a("string").and.not.empty);
if (t) pm.collectionVariables.set("${tokenVar}", t);${extra}` });
const loginFallido = (id, title, email, password, status, code) => request(id, title, "POST", "/v1/personal/autenticar",
  { body: `{\n  "correo": "${email}",\n  "password": "${password}"\n}`, status, code });

const preparacion = { name: "00-autenticacion-e-identidad", item: [
  request("M1A-00-01", "Salud del backend", "GET", "/health", { status: 200 }),
  login("M1A-00-02", "Autenticar administrador", "adminEmail", "adminPassword", "tokenAdmin",
    '\npm.test("rol ADMINISTRADOR", () => pm.expect(pm.response.json().rol).to.eql("ADMINISTRADOR")); pm.collectionVariables.set("idAdmin", pm.response.json().userId);'),
  login("M1A-00-03", "Autenticar docente sin privilegios administrativos", "docenteEmail", "docentePassword", "tokenDocente"),
  login("M1A-00-04", "Autenticar cuenta de estudiante (Usuario con Estudiante)", "estudianteEmail", "estudiantePassword", "tokenEstudiante"),
  request("M1A-00-05", "Consultar la identidad del administrador", "GET", "/v1/personal/me", { auth: "tokenAdmin", status: 200,
    test: 'pm.test("es el administrador", () => pm.expect(pm.response.json().rol).to.eql("ADMINISTRADOR"));' }),
] };

const consultas = { name: "01-consulta-roles-permisos", item: [
  request("M1A-01-01", "Consultar roles", "GET", "/v1/admin/roles", { auth: "tokenAdmin", status: 200,
    test: 'pm.test("incluye ADMINISTRADOR", () => pm.expect(pm.response.json().items.map(r => r.nombre)).to.include("ADMINISTRADOR"));' }),
  request("M1A-01-02", "Consultar permisos (catálogo DM1-03)", "GET", "/v1/admin/permisos", { auth: "tokenAdmin", status: 200,
    test: `const items = pm.response.json().items; const id = c => (items.find(p => p.codigo === c) || {}).idPermiso;
pm.test("CREAR_CASO y GESTIONAR_SESION aprovisionados", () => { pm.expect(id("CREAR_CASO")).to.be.a("number"); pm.expect(id("GESTIONAR_SESION")).to.be.a("number"); });
pm.collectionVariables.set("idCrearCaso", id("CREAR_CASO")); pm.collectionVariables.set("idGestionarSesion", id("GESTIONAR_SESION"));
pm.collectionVariables.set("idPublicarCaso", id("PUBLICAR_CASO")); pm.collectionVariables.set("idConsultarCaso", id("CONSULTAR_CASO"));` }),
  request("M1A-01-03", "Rechazar consulta sin credencial", "GET", "/v1/admin/roles", { status: 401, code: "UNAUTHENTICATED" }),
  request("M1A-01-04", "Rechazar consulta de un docente", "GET", "/v1/admin/permisos", { auth: "tokenDocente", status: 403, code: "ADMIN_REQUIRED" }),
  request("M1A-01-05", "Rechazar consulta de un estudiante", "GET", "/v1/admin/roles", { auth: "tokenEstudiante", status: 403, code: "ADMIN_REQUIRED" }),
] };

const alta = { name: "02-alta-docente", item: [
  request("M1A-02-01", "Dar de alta un docente (sin credencial utilizable)", "POST", "/v1/admin/docentes", { auth: "tokenAdmin",
    pre: 'pm.collectionVariables.set("correoNuevo", "docente." + require("uuid").v4() + "@prisaly.invalid");',
    body: '{\n  "nombre": "Docente Newman",\n  "correo": "{{correoNuevo}}"\n}', status: 201,
    test: `const b = pm.response.json(); pm.test("UsuarioDocente sin credenciales", () => { pm.expect(Object.keys(b).sort()).to.eql(["correo","estado","idUsuario","nombre"]);
  pm.expect(b.estado).to.eql("Activo"); }); pm.collectionVariables.set("idNuevo", b.idUsuario);` }),
  request("M1A-02-02", "Rechazar correo duplicado", "POST", "/v1/admin/docentes", { auth: "tokenAdmin",
    body: '{\n  "nombre": "Duplicado",\n  "correo": "{{correoNuevo}}"\n}', status: 409, code: "DUPLICATE_EMAIL" }),
  request("M1A-02-03", "Rechazar datos inválidos", "POST", "/v1/admin/docentes", { auth: "tokenAdmin",
    body: '{\n  "nombre": "",\n  "correo": "no-es-correo"\n}', status: 400, code: "VALIDATION_ERROR" }),
  request("M1A-02-04", "Rechazar alta por un docente", "POST", "/v1/admin/docentes", { auth: "tokenDocente",
    body: '{\n  "nombre": "No autorizado",\n  "correo": "no.autorizado@prisaly.invalid"\n}', status: 403, code: "ADMIN_REQUIRED" }),
  request("M1A-02-05", "El docente nuevo no puede autenticarse hasta la entrega segura", "POST", "/v1/personal/autenticar",
    { body: '{\n  "correo": "{{correoNuevo}}",\n  "password": "{{docentePassword}}"\n}', status: 401, code: "AUTH_FAILED",
      note: "Credencial PENDIENTE: no existe infraestructura aprobada de entrega segura; no se simula." }),
] };

const ruta = (u, p) => `/v1/admin/usuarios/{{${u}}}/permisos/{{${p}}}`;
const asignacion = { name: "03-asignacion-consulta-autoasignacion", item: [
  request("M1A-03-01", "Asignar CREAR_CASO al docente nuevo", "PUT", ruta("idNuevo", "idCrearCaso"), { auth: "tokenAdmin", status: 200,
    test: 'pm.test("habilitado", () => pm.expect(pm.response.json().habilitado).to.eql(true));' }),
  request("M1A-03-02", "Rechazar asignación duplicada", "PUT", ruta("idNuevo", "idCrearCaso"), { auth: "tokenAdmin", status: 409, code: "DUPLICATE_ASSIGNMENT" }),
  request("M1A-03-03", "Consultar permisos del docente nuevo", "GET", "/v1/admin/usuarios/{{idNuevo}}/permisos", { auth: "tokenAdmin", status: 200,
    test: 'pm.test("incluye CREAR_CASO", () => pm.expect(pm.response.json().items.map(p => p.codigo)).to.eql(["CREAR_CASO"]));' }),
  request("M1A-03-04", "Rechazar usuario inexistente", "PUT", "/v1/admin/usuarios/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/permisos/{{idCrearCaso}}",
    { auth: "tokenAdmin", status: 404, code: "USER_NOT_FOUND" }),
  request("M1A-03-05", "Rechazar permiso inexistente", "PUT", "/v1/admin/usuarios/{{idNuevo}}/permisos/999999", { auth: "tokenAdmin", status: 404, code: "PERMISSION_NOT_FOUND" }),
  request("M1A-03-06", "Rechazar identificador mal formado", "PUT", "/v1/admin/usuarios/no-es-uuid/permisos/{{idCrearCaso}}", { auth: "tokenAdmin", status: 400, code: "VALIDATION_ERROR" }),
  request("M1A-03-07", "Rechazar asignación por un docente", "PUT", ruta("idNuevo", "idGestionarSesion"), { auth: "tokenDocente", status: 403, code: "ADMIN_REQUIRED" }),
  request("M1A-03-08", "Rechazar permiso controlado para un estudiante", "PUT", `/v1/admin/usuarios/${ESTUDIANTE}/permisos/{{idConsultarCaso}}`,
    { auth: "tokenAdmin", status: 422, code: "PERMISSION_NOT_ALLOWED_FOR_STUDENT" }),
  request("M1A-03-09", "Autoasignación permitida de CREAR_CASO (DM1-02b)", "PUT", ruta("idAdmin", "idCrearCaso"), { auth: "tokenAdmin", status: 200 }),
  request("M1A-03-10", "Autoasignación permitida de GESTIONAR_SESION (DM1-02b)", "PUT", ruta("idAdmin", "idGestionarSesion"), { auth: "tokenAdmin", status: 200 }),
  request("M1A-03-11", "Rechazar autoasignación de PUBLICAR_CASO (DM1-02b)", "PUT", ruta("idAdmin", "idPublicarCaso"),
    { auth: "tokenAdmin", status: 403, code: "PERMISSION_SELF_ASSIGNMENT_NOT_ALLOWED" }),
] };

const revocacion = { name: "04-revocacion-efectiva", item: [
  request("M1A-04-01", "El docente opera con CREAR_CASO", "GET", "/v1/personal/autorizacion/crear-caso", { auth: "tokenDocente", status: 200 }),
  request("M1A-04-02", "Revocar CREAR_CASO al docente", "DELETE", `/v1/admin/usuarios/${DOCENTE_BRUNO}/permisos/{{idCrearCaso}}`, { auth: "tokenAdmin", status: 204 }),
  request("M1A-04-03", "La revocación se aplica en la siguiente petición", "GET", "/v1/personal/autorizacion/crear-caso", { auth: "tokenDocente", status: 403 }),
  request("M1A-04-04", "Rechazar revocación repetida", "DELETE", `/v1/admin/usuarios/${DOCENTE_BRUNO}/permisos/{{idCrearCaso}}`, { auth: "tokenAdmin", status: 404, code: "PERMISSION_NOT_FOUND" }),
  request("M1A-04-05", "Rechazar revocación por un docente", "DELETE", ruta("idNuevo", "idCrearCaso"), { auth: "tokenDocente", status: 403, code: "ADMIN_REQUIRED" }),
  request("M1A-04-06", "Volver a asignar (reactivación)", "PUT", `/v1/admin/usuarios/${DOCENTE_BRUNO}/permisos/{{idCrearCaso}}`, { auth: "tokenAdmin", status: 200 }),
  request("M1A-04-07", "El permiso vuelve a valer", "GET", "/v1/personal/autorizacion/crear-caso", { auth: "tokenDocente", status: 200 }),
] };

const nueva = "una frase de paso nueva";
const cambio = (id, title, actual, nuevaClave, status, code) => request(id, title, "PUT", "/v1/personal/contrasena", {
  auth: "tokenDocente", body: `{\n  "actual": "${actual}",\n  "nueva": "${nuevaClave}"\n}`, status, code });
const contrasenas = { name: "05-politica-contrasenas", item: [
  cambio("M1A-05-01", "Rechazar menos de 12 caracteres", "{{docentePassword}}", "corta", 422, "PASSWORD_TOO_SHORT"),
  cambio("M1A-05-02", "Rechazar más de 72 bytes", "{{docentePassword}}", "a".repeat(73), 422, "PASSWORD_TOO_LONG"),
  cambio("M1A-05-03", "Rechazar contraseña común", "{{docentePassword}}", "Password1234", 422, "PASSWORD_COMMON"),
  cambio("M1A-05-04", "Rechazar contraseña que contiene el correo", "{{docentePassword}}", "x{{docenteEmail}}!", 422, "PASSWORD_CONTAINS_EMAIL"),
  cambio("M1A-05-05", "Rechazar contraseña actual incorrecta", "incorrecta-123", nueva, 401, "AUTH_FAILED"),
  cambio("M1A-05-06", "Cambiar la contraseña", "{{docentePassword}}", nueva, 204),
  request("M1A-05-07", "Autenticar con la contraseña nueva", "POST", "/v1/personal/autenticar",
    { body: `{\n  "correo": "{{docenteEmail}}",\n  "password": "${nueva}"\n}`, status: 200 }),
] };

const tokens = { name: "06-revocacion-de-token", item: [
  request("M1A-06-01", "Cerrar la sesión del docente", "POST", "/v1/personal/cerrar-sesion", { auth: "tokenDocente", status: 204 }),
  request("M1A-06-02", "El token revocado ya no vale", "GET", "/v1/personal/me", { auth: "tokenDocente", status: 401, code: "UNAUTHENTICATED" }),
] };

const limites = { name: "07-limite-de-intentos", description: "Ejecutar al final: limita el origen durante 15 minutos.", item: [
  ...[1, 2, 3, 4, 5].map((i) => loginFallido(`M1A-07-0${i}`, `Fallo ${i} de la cuenta de estudiante`, "estudiante.bruno@prisaly.local", `mala-${i}`, 401, "AUTH_FAILED")),
  request("M1A-07-06", "Sexto intento con la contraseña correcta: 429 con Retry-After", "POST", "/v1/personal/autenticar",
    { body: '{\n  "correo": "{{estudianteEmail}}",\n  "password": "{{estudiantePassword}}"\n}', status: 429, code: "AUTH_RATE_LIMITED",
      test: 'pm.test("Retry-After en segundos (1..900)", () => { const r = Number(pm.response.headers.get("Retry-After")); pm.expect(r).to.be.within(1, 900); });' }),
  request("M1A-07-07", "Otra cuenta del mismo origen sigue autenticando", "POST", "/v1/personal/autenticar",
    { body: '{\n  "correo": "{{adminEmail}}",\n  "password": "{{adminPassword}}"\n}', status: 200 }),
  request("M1A-07-08", "Fallos con cuentas distintas hasta el límite del origen", "POST", "/v1/personal/autenticar",
    { pre: 'pm.collectionVariables.set("intentosOrigen", Number(pm.collectionVariables.get("intentosOrigen") || 0) + 1);',
      body: '{\n  "correo": "{{$randomUUID}}@prisaly.invalid",\n  "password": "x"\n}', status: 401,
      note: "Se repite (setNextRequest) hasta que el origen queda limitado; el último intento cuenta como el que alcanza el umbral.",
      test: `const n = Number(pm.collectionVariables.get("intentosOrigen"));
pm.test("fallo contado (" + n + ")", () => pm.expect(n).to.be.at.most(20));
if (n < 30) { postman.setNextRequest(pm.response.code === 401 ? "M1A-07-08 | Fallos con cuentas distintas hasta el límite del origen" : "M1A-07-09 | Origen limitado: incluso el administrador recibe 429"); }` }),
  request("M1A-07-09", "Origen limitado: incluso el administrador recibe 429", "POST", "/v1/personal/autenticar",
    { body: '{\n  "correo": "{{adminEmail}}",\n  "password": "{{adminPassword}}"\n}', status: 429, code: "AUTH_RATE_LIMITED" }),
] };

// 07-08 loops until the origin is limited: its last iteration legitimately returns 429, so its test is specific.
const bucle = limites.item.find((i) => i.name.startsWith("M1A-07-08"));
bucle.event.find((e) => e.listen === "test").script.exec = [
  'const n = Number(pm.collectionVariables.get("intentosOrigen"));',
  'pm.test("401 AUTH_FAILED o 429 AUTH_RATE_LIMITED", () => { pm.expect([401, 429]).to.include(pm.response.code);',
  '  pm.expect(pm.response.json().code).to.eql(pm.response.code === 401 ? "AUTH_FAILED" : "AUTH_RATE_LIMITED"); });',
  'pm.test("el origen se limita antes de 20 intentos de este bucle (" + n + ")", () => pm.expect(n).to.be.at.most(20));',
  'if (pm.response.code === 429) { pm.test("Retry-After presente", () => pm.expect(Number(pm.response.headers.get("Retry-After"))).to.be.within(1, 900)); }',
  'if (pm.response.code === 401 && n < 20) { postman.setNextRequest("M1A-07-08 | Fallos con cuentas distintas hasta el límite del origen"); }',
];

const collection = {
  info: { name: "PRISALY — M1 Administración y seguridad (DC-M1-04)",
    description: "Flujo administrativo y de seguridad de M1 sobre el backend real. Orden: 00 a 07 (07 al final). " +
      "La inicialización del primer administrador (D-C / DM1-01) NO está incluida: quedó detenida por la incompatibilidad de V010 con la guarda de esquema de M4. " +
      "El administrador de prueba lo crea qa/m1-postman/fixture-administrador.sql en una base efímera. Contraseñas del entorno vacías.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
  item: [preparacion, consultas, alta, asignacion, revocacion, contrasenas, tokens, limites],
  variable: GENERATED.map((key) => ({ key, value: "" })),
};
const environment = { name: "PRISALY M1 Administración local", values: [
  ["baseUrl", "http://localhost:8080/api"], ["adminEmail", "admin.qa@prisaly.invalid"], ["adminPassword", ""],
  ["docenteEmail", "docente.bruno@prisaly.local"], ["docentePassword", ""],
  ["estudianteEmail", "estudiante.bruno@prisaly.local"], ["estudiantePassword", ""],
].map(([key, value]) => ({ key, value, type: key.endsWith("Password") ? "secret" : "default", enabled: true })) };

writeFileSync(COLLECTION, JSON.stringify(collection, null, 2) + "\n");
writeFileSync(ENVIRONMENT, JSON.stringify(environment, null, 2) + "\n");
console.log(`Colección: ${collection.item.reduce((n, f) => n + f.item.length, 0)} solicitudes`);
