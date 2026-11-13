// Generates the M3 Postman collection and its environment (DC-M3-01). Writes no secrets.
// Usage: node postman/build_m3_collection.mjs
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const COLLECTION = join(HERE, "PRISALY-M3-Casos.postman_collection.json");
const ENVIRONMENT = join(HERE, "PRISALY-M3-Casos.postman_environment.json");

const GENERATED = ["tokenDocente", "tokenSinPermiso", "tokenOtroDocente", "idCaso", "idCaso2", "etag", "etagAnterior",
  "etag2", "idEscenario", "idPregunta", "idOpcionCorrecta", "idOpcionIncorrecta"];

// Common checks: status, domain code, and no internal details in errors (contract {code, message}).
function checks(status, code, extra = "") {
  return [
    `pm.test("HTTP ${status}", () => pm.response.to.have.status(${status}));`,
    code ? `pm.test("código ${code}", () => pm.expect(pm.response.json().code).to.eql("${code}"));` : "",
    "if (pm.response.code >= 400) {",
    '  pm.test("error con contrato code/message", () => { const b = pm.response.json();',
    '    pm.expect(b.code).to.be.a("string"); pm.expect(b.message).to.be.a("string"); });',
    '  pm.test("sin detalles internos", () => pm.expect(pm.response.text())',
    "    .to.not.match(/exception|jdbc:|org\\.(springframework|hibernate)|stacktrace|passwordhash/i));",
    "}",
    extra.trim(),
  ].filter(Boolean).join("\n");
}

const saveEtag = (name = "etag") => `
pm.test("ETag fuerte m3-v2", () => pm.expect(pm.response.headers.get("ETag")).to.match(/^"m3-v2:[0-9a-f]{64}"$/));
if (pm.response.headers.get("ETag")) pm.collectionVariables.set("${name}", pm.response.headers.get("ETag"));`;

const uuid = (name) => `pm.collectionVariables.set("${name}", require("uuid").v4());`;

function request(id, title, method, path, { auth, ifMatch, body, pre = "", status, code, test = "", note = "" }) {
  const header = [];
  if (body !== undefined) header.push({ key: "Content-Type", value: "application/json", type: "text" });
  if (auth) header.push({ key: "Authorization", value: `Bearer {{${auth}}}`, type: "text" });
  if (ifMatch) header.push({ key: "If-Match", value: ifMatch, type: "text" });
  const req = { method, auth: { type: "noauth" }, header, url: `{{baseUrl}}${path}`,
    description: `${note ? note + " " : ""}Esperado: HTTP ${status}${code ? " " + code : ""}.` };
  if (body !== undefined) {
    req.body = { mode: "raw", raw: typeof body === "string" ? body : JSON.stringify(body, null, 2),
      options: { raw: { language: "json" } } };
  }
  // Variables this request reads (excluding the ones its own pre-request creates).
  const created = [...pre.matchAll(/collectionVariables\.set\("(\w+)"/g)].map((m) => m[1]);
  const required = [...new Set([...JSON.stringify(req).matchAll(/{{(\w+)}}/g)].map((m) => m[1]))]
    .filter((v) => !created.includes(v));
  const prerequest = [
    pre,
    `const requeridas = ${JSON.stringify(required)};`,
    'const faltan = requeridas.filter(k => !String(pm.variables.get(k) ?? "").trim());',
    "if (faltan.length) {",
    '  console.warn("PRISALY M3: bloqueada por variables ausentes: " + faltan.join(", "));',
    "  pm.execution.skipRequest();",
    "}",
  ].filter(Boolean).join("\n");
  return {
    name: `${id} | ${title}`,
    event: [
      { listen: "prerequest", script: { type: "text/javascript", exec: prerequest.split("\n") } },
      { listen: "test", script: { type: "text/javascript", exec: checks(status, code, test).split("\n") } },
    ],
    request: req,
  };
}

const login = (id, title, emailVar, passwordVar, tokenVar) => request(id, title, "POST", "/v1/personal/autenticar", {
  body: `{\n  "correo": "{{${emailVar}}}",\n  "password": "{{${passwordVar}}}"\n}`, status: 200,
  test: `const t = pm.response.json().accessToken;
pm.test("token recibido", () => pm.expect(t).to.be.a("string").and.not.empty);
if (t) pm.collectionVariables.set("${tokenVar}", t);`,
});

const caso = (idVar, titulo) => `{\n  "idCaso": "{{${idVar}}}",\n  "titulo": "${titulo}",\n  "tiempoEstimadoSegundos": 1800,\n  "historia": "Historia del caso de prueba",\n  "desarrolloSituacional": "Desarrollo situacional de prueba"\n}`;

const preparacion = {
  name: "00-preparacion",
  item: [
    request("M3-00-01", "Salud del backend", "GET", "/health", { status: 200,
      test: 'pm.test("servicio ok", () => pm.expect(pm.response.json().status).to.eql("ok"));' }),
    login("M3-00-02", "Autenticar docente propietario", "docenteEmail", "docentePassword", "tokenDocente"),
    login("M3-00-03", "Autenticar personal sin permisos", "sinPermisoEmail", "sinPermisoPassword", "tokenSinPermiso"),
    login("M3-00-04", "Autenticar otro docente (mismos permisos, no propietario)", "otroDocenteEmail", "otroDocentePassword", "tokenOtroDocente"),
  ],
};

const productiva = {
  name: "A-configuracion-productiva",
  description: "application.yml entregado (F4 de I1: CONSULTAR_CASO; BORRADOR, PUBLICADO, ARCHIVADO; también inactivos propios) y sin permisos de casos asignados: la lectura se niega por falta de CONSULTAR_CASO.",
  item: [
    request("M3-A-01", "Crear caso en Borrador", "POST", "/v1/casos", { auth: "tokenDocente", pre: uuid("idCaso"),
      body: caso("idCaso", "Postman M3 productiva"), status: 201,
      test: 'pm.test("estado BORRADOR", () => pm.expect(pm.response.json().estado).to.eql("BORRADOR"));' }),
    request("M3-A-02", "Consultar caso propio sin CONSULTAR_CASO asignado", "GET", "/v1/casos/{{idCaso}}",
      { auth: "tokenDocente", status: 403, code: "FORBIDDEN" }),
    request("M3-A-03", "Consultar contenido sin CONSULTAR_CASO asignado", "GET", "/v1/casos/{{idCaso}}/contenido",
      { auth: "tokenDocente", status: 403, code: "FORBIDDEN" }),
    request("M3-A-04", "Consultar rúbrica sin CONSULTAR_CASO asignado", "GET", "/v1/casos/{{idCaso}}/rubrica",
      { auth: "tokenDocente", status: 403, code: "FORBIDDEN" }),
    request("M3-A-05", "Editar sin MODIFICAR_CASO asignado", "PATCH", "/v1/casos/{{idCaso}}",
      { auth: "tokenDocente", ifMatch: '"m3-v2:' + "0".repeat(64) + '"', body: { titulo: "Cambio" }, status: 403, code: "FORBIDDEN" }),
    request("M3-A-06", "Agregar escenario sin MODIFICAR_CASO asignado", "POST", "/v1/casos/{{idCaso}}/escenarios",
      { auth: "tokenDocente", ifMatch: '"m3-v2:' + "0".repeat(64) + '"', pre: uuid("idEscenario"),
        body: `{\n  "idEscenario": "{{idEscenario}}",\n  "orden": 1\n}`, status: 403, code: "FORBIDDEN" }),
    request("M3-A-07", "Publicar sin PUBLICAR_CASO asignado", "POST", "/v1/casos/{{idCaso}}/publicar",
      { auth: "tokenDocente", ifMatch: '"m3-v2:' + "0".repeat(64) + '"', status: 403, code: "FORBIDDEN" }),
  ],
};

const prueba = {
  name: "B-configuracion-prueba",
  description: "application.yml entregado (F4 de I1: CONSULTAR_CASO; BORRADOR, PUBLICADO, ARCHIVADO; también inactivos propios). Permisos de casos asignados por SQL en una base efímera.",
  item: [
    request("M3-B-01", "Rechazar creación sin credencial", "POST", "/v1/casos", { pre: uuid("idCaso"),
      body: caso("idCaso", "Sin credencial"), status: 401, code: "UNAUTHENTICATED" }),
    request("M3-B-02", "Rechazar creación sin CREAR_CASO", "POST", "/v1/casos", { auth: "tokenSinPermiso",
      body: caso("idCaso", "Sin permiso"), status: 403, code: "FORBIDDEN" }),
    request("M3-B-03", "Rechazar tiempo estimado no positivo", "POST", "/v1/casos", { auth: "tokenDocente",
      body: `{\n  "idCaso": "{{idCaso}}",\n  "titulo": "Tiempo cero",\n  "tiempoEstimadoSegundos": 0\n}`, status: 400, code: "INVALID_CASE_REQUEST" }),
    request("M3-B-04", "Rechazar propietario inyectado", "POST", "/v1/casos", { auth: "tokenDocente",
      body: `{\n  "idCaso": "{{idCaso}}",\n  "titulo": "Inyección",\n  "tiempoEstimadoSegundos": 60,\n  "docenteId": "11111111-1111-1111-1111-111111111104"\n}`,
      status: 400, code: "INVALID_CASE_REQUEST" }),
    request("M3-B-05", "Crear caso en Borrador", "POST", "/v1/casos", { auth: "tokenDocente",
      body: caso("idCaso", "Postman M3 recorrido"), status: 201,
      test: `pm.test("estado BORRADOR e identificador", () => { const b = pm.response.json();
  pm.expect(b.estado).to.eql("BORRADOR"); pm.expect(b.idCaso).to.eql(pm.collectionVariables.get("idCaso")); });` }),
    request("M3-B-06", "Rechazar identificador ya creado", "POST", "/v1/casos", { auth: "tokenDocente",
      body: caso("idCaso", "Duplicado"), status: 409, code: "CASE_CONFLICT" }),
    request("M3-B-07", "Consultar caso propio", "GET", "/v1/casos/{{idCaso}}", { auth: "tokenDocente", status: 200,
      test: `pm.test("ficha", () => { const b = pm.response.json(); pm.expect(b.estado).to.eql("BORRADOR");
  pm.expect(b.tiempoEstimadoSegundos).to.eql(1800); });${saveEtag()}
pm.collectionVariables.set("etagAnterior", pm.response.headers.get("ETag"));` }),
    request("M3-B-08", "Rechazar consulta sin credencial", "GET", "/v1/casos/{{idCaso}}", { status: 401, code: "UNAUTHENTICATED" }),
    request("M3-B-09", "Rechazar consulta de otro docente", "GET", "/v1/casos/{{idCaso}}", { auth: "tokenOtroDocente", status: 403, code: "FORBIDDEN",
      test: 'pm.test("no filtra el título", () => pm.expect(pm.response.text()).to.not.include("Postman M3"));' }),
    request("M3-B-10", "Caso inexistente", "GET", "/v1/casos/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", { auth: "tokenDocente", status: 404, code: "CASE_NOT_FOUND" }),
    request("M3-B-11", "Editar sin If-Match", "PATCH", "/v1/casos/{{idCaso}}", { auth: "tokenDocente",
      body: { titulo: "Sin precondición" }, status: 428, code: "IF_MATCH_REQUIRED" }),
    request("M3-B-12", "Editar con If-Match mal formado", "PATCH", "/v1/casos/{{idCaso}}", { auth: "tokenDocente", ifMatch: "no-es-un-etag",
      body: { titulo: "Precondición inválida" }, status: 400, code: "INVALID_CASE_REQUEST" }),
    request("M3-B-13", "Editar título e historia", "PATCH", "/v1/casos/{{idCaso}}", { auth: "tokenDocente", ifMatch: "{{etag}}",
      body: { titulo: "Postman M3 recorrido editado", historia: "Historia editada" }, status: 204 }),
    request("M3-B-14", "Rechazar ETag obsoleto", "PATCH", "/v1/casos/{{idCaso}}", { auth: "tokenDocente", ifMatch: "{{etagAnterior}}",
      body: { titulo: "Con ETag obsoleto" }, status: 412, code: "STALE_CASE" }),
    request("M3-B-15", "Consultar caso editado", "GET", "/v1/casos/{{idCaso}}", { auth: "tokenDocente", status: 200,
      test: `pm.test("título editado", () => pm.expect(pm.response.json().titulo).to.eql("Postman M3 recorrido editado"));${saveEtag()}` }),
    request("M3-B-16", "Rechazar edición con tiempo no positivo", "PATCH", "/v1/casos/{{idCaso}}", { auth: "tokenDocente", ifMatch: "{{etag}}",
      body: { tiempoEstimadoSegundos: 0 }, status: 400, code: "INVALID_CASE_REQUEST" }),
    request("M3-B-17", "Agregar escenario", "POST", "/v1/casos/{{idCaso}}/escenarios", { auth: "tokenDocente", ifMatch: "{{etag}}",
      pre: uuid("idEscenario"),
      body: `{\n  "idEscenario": "{{idEscenario}}",\n  "titulo": "Escenario 1",\n  "contexto": "Contexto situacional",\n  "instrucciones": "Lea y responda",\n  "orden": 1\n}`,
      status: 201, test: `pm.test("id del escenario", () => pm.expect(pm.response.json().id).to.eql(pm.collectionVariables.get("idEscenario")));${saveEtag()}` }),
    request("M3-B-18", "Rechazar orden de escenario repetido", "POST", "/v1/casos/{{idCaso}}/escenarios", { auth: "tokenDocente", ifMatch: "{{etag}}",
      body: `{\n  "idEscenario": "${"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"}",\n  "orden": 1\n}`, status: 409, code: "SCENARIO_ORDER_IN_USE" }),
    request("M3-B-19", "Agregar pregunta al escenario", "POST", "/v1/casos/{{idCaso}}/escenarios/{{idEscenario}}/preguntas",
      { auth: "tokenDocente", ifMatch: "{{etag}}", pre: uuid("idPregunta"),
        body: `{\n  "idPregunta": "{{idPregunta}}",\n  "enunciado": "¿Qué decisión toma?",\n  "orden": 1\n}`, status: 201, test: saveEtag() }),
    request("M3-B-20", "Rechazar opción sin explicación", "POST", "/v1/casos/{{idCaso}}/preguntas/{{idPregunta}}/opciones",
      { auth: "tokenDocente", ifMatch: "{{etag}}",
        body: `{\n  "idOpcion": "${"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}",\n  "texto": "Sin explicación",\n  "esCorrecta": false,\n  "orden": 3\n}`,
        status: 400, code: "INVALID_CASE_REQUEST" }),
    request("M3-B-21", "Agregar opción correcta con explicación", "POST", "/v1/casos/{{idCaso}}/preguntas/{{idPregunta}}/opciones",
      { auth: "tokenDocente", ifMatch: "{{etag}}", pre: uuid("idOpcionCorrecta"),
        body: `{\n  "idOpcion": "{{idOpcionCorrecta}}",\n  "texto": "Opción correcta",\n  "esCorrecta": true,\n  "explicacion": "Es correcta porque…",\n  "orden": 1\n}`,
        status: 201, test: saveEtag() }),
    request("M3-B-22", "Agregar opción incorrecta con explicación", "POST", "/v1/casos/{{idCaso}}/preguntas/{{idPregunta}}/opciones",
      { auth: "tokenDocente", ifMatch: "{{etag}}", pre: uuid("idOpcionIncorrecta"),
        body: `{\n  "idOpcion": "{{idOpcionIncorrecta}}",\n  "texto": "Opción incorrecta",\n  "esCorrecta": false,\n  "explicacion": "Es incorrecta porque…",\n  "orden": 2\n}`,
        status: 201, test: saveEtag() }),
    request("M3-B-23", "Rechazar publicación sin rúbrica", "POST", "/v1/casos/{{idCaso}}/publicar",
      { auth: "tokenDocente", ifMatch: "{{etag}}", status: 409, code: "INCOMPLETE_RUBRIC" }),
    request("M3-B-24", "Rechazar rúbrica con puntaje no positivo", "PUT", "/v1/casos/{{idCaso}}/rubrica", { auth: "tokenDocente", ifMatch: "{{etag}}",
      body: `{\n  "nombre": "Rúbrica inválida",\n  "criterios": [{"id": "c1", "descripcion": "Criterio", "puntaje": 0}]\n}`, status: 400, code: "INVALID_CASE_REQUEST" }),
    request("M3-B-25", "Guardar rúbrica con criterio y pregunta asociada", "PUT", "/v1/casos/{{idCaso}}/rubrica", { auth: "tokenDocente", ifMatch: "{{etag}}",
      body: `{\n  "nombre": "Rúbrica del caso",\n  "descripcion": "Datos almacenados sin fórmula (DD-03)",\n  "puntajeTotal": 5.00,\n  "criterios": [{"id": "c1", "descripcion": "Argumenta la decisión", "puntaje": 5}],\n  "preguntas": [{"idPregunta": "{{idPregunta}}", "valorPregunta": 5.00, "criterio": "c1"}]\n}`,
      status: 200, test: `pm.test("rúbrica completa", () => pm.expect(pm.response.json().completa).to.eql(true));${saveEtag()}` }),
    request("M3-B-26", "Consultar rúbrica", "GET", "/v1/casos/{{idCaso}}/rubrica", { auth: "tokenDocente", status: 200,
      test: 'pm.test("criterio y asociación", () => { const b = pm.response.json(); pm.expect(b.criterios).to.have.length(1); pm.expect(b.preguntas).to.have.length(1); });' }),
    request("M3-B-27", "Consultar contenido completo", "GET", "/v1/casos/{{idCaso}}/contenido", { auth: "tokenDocente", status: 200,
      test: `pm.test("escenario, pregunta y dos opciones con explicación", () => { const b = pm.response.json();
  pm.expect(b.escenarios).to.have.length(1); const p = b.escenarios[0].preguntas; pm.expect(p).to.have.length(1);
  pm.expect(p[0].opciones).to.have.length(2); p[0].opciones.forEach(o => pm.expect(o.explicacion).to.be.a("string").and.not.empty); });` }),
    request("M3-B-28", "Listar escenarios", "GET", "/v1/casos/{{idCaso}}/escenarios", { auth: "tokenDocente", status: 200,
      test: 'pm.test("un escenario", () => pm.expect(pm.response.json()).to.have.length(1));' }),
    request("M3-B-29", "Consultar pregunta", "GET", "/v1/casos/{{idCaso}}/preguntas/{{idPregunta}}", { auth: "tokenDocente", status: 200,
      test: 'pm.test("pregunta", () => pm.expect(pm.response.json().idPregunta).to.eql(pm.collectionVariables.get("idPregunta")));' }),
    request("M3-B-30", "Listar opciones", "GET", "/v1/casos/{{idCaso}}/preguntas/{{idPregunta}}/opciones", { auth: "tokenDocente", status: 200,
      test: 'pm.test("una correcta", () => pm.expect(pm.response.json().filter(o => o.esCorrecta)).to.have.length(1));' }),
    request("M3-B-31", "Rechazar pregunta de otro caso", "GET", "/v1/casos/{{idCaso}}/preguntas/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      { auth: "tokenDocente", status: 404, code: "ELEMENT_NOT_IN_CASE" }),
    request("M3-B-32", "Rechazar publicación sin PUBLICAR_CASO", "POST", "/v1/casos/{{idCaso}}/publicar",
      { auth: "tokenSinPermiso", ifMatch: "{{etag}}", status: 403, code: "FORBIDDEN" }),
    request("M3-B-33", "Rechazar publicación por un docente no propietario", "POST", "/v1/casos/{{idCaso}}/publicar",
      { auth: "tokenOtroDocente", ifMatch: "{{etag}}", status: 403, code: "FORBIDDEN" }),
    request("M3-B-34", "Publicar caso validado", "POST", "/v1/casos/{{idCaso}}/publicar", { auth: "tokenDocente", ifMatch: "{{etag}}", status: 200,
      test: `pm.test("estado PUBLICADO", () => pm.expect(pm.response.json().estado).to.eql("PUBLICADO"));${saveEtag()}` }),
    request("M3-B-35", "Rechazar segunda publicación", "POST", "/v1/casos/{{idCaso}}/publicar",
      { auth: "tokenDocente", ifMatch: "{{etag}}", status: 409, code: "ALREADY_PUBLISHED" }),
    request("M3-B-36", "Rechazar edición de un caso publicado", "PATCH", "/v1/casos/{{idCaso}}", { auth: "tokenDocente", ifMatch: "{{etag}}",
      body: { titulo: "No editable" }, status: 409, code: "CASE_NOT_EDITABLE" }),
    request("M3-B-37", "Rechazar contenido en un caso publicado", "POST", "/v1/casos/{{idCaso}}/escenarios", { auth: "tokenDocente", ifMatch: "{{etag}}",
      body: `{\n  "idEscenario": "${"dddddddd-dddd-4ddd-8ddd-dddddddddddd"}",\n  "orden": 2\n}`, status: 409, code: "CASE_NOT_EDITABLE" }),
    request("M3-B-38", "Archivar caso publicado", "POST", "/v1/casos/{{idCaso}}/archivar", { auth: "tokenDocente", ifMatch: "{{etag}}", status: 200,
      test: `pm.test("estado ARCHIVADO", () => pm.expect(pm.response.json().estado).to.eql("ARCHIVADO"));${saveEtag()}` }),
    request("M3-B-39", "Rechazar segundo archivado", "POST", "/v1/casos/{{idCaso}}/archivar",
      { auth: "tokenDocente", ifMatch: "{{etag}}", status: 409, code: "ARCHIVED_CASE_OPERATION_PENDING" }),
    request("M3-B-40", "Consultar caso archivado", "GET", "/v1/casos/{{idCaso}}", { auth: "tokenDocente", status: 200,
      test: 'pm.test("estado ARCHIVADO", () => pm.expect(pm.response.json().estado).to.eql("ARCHIVADO"));' }),
    request("M3-B-41", "Crear segundo caso sin contenido", "POST", "/v1/casos", { auth: "tokenDocente", pre: uuid("idCaso2"),
      body: caso("idCaso2", "Postman M3 incompleto"), status: 201 }),
    request("M3-B-42", "Consultar segundo caso", "GET", "/v1/casos/{{idCaso2}}", { auth: "tokenDocente", status: 200, test: saveEtag("etag2") }),
    request("M3-B-43", "Rechazar publicación de un caso sin preguntas", "POST", "/v1/casos/{{idCaso2}}/publicar",
      { auth: "tokenDocente", ifMatch: "{{etag2}}", status: 409, code: "CASE_INCOMPLETE" }),
    request("M3-B-44", "Rechazar archivado de un borrador", "POST", "/v1/casos/{{idCaso2}}/archivar",
      { auth: "tokenDocente", ifMatch: "{{etag2}}", status: 409, code: "CASE_NOT_PUBLISHED" }),
  ],
};

const collection = {
  info: {
    name: "PRISALY — M3 Casos (DC-M3-01)",
    description: "Colección M3: creación, edición, escenarios, preguntas, opciones, rúbrica, publicación, archivado, consultas y rechazos. " +
      "Ejecutar 00 y después A (configuración productiva) o B (configuración de prueba, con la política F4 no ratificada). " +
      "Ejecutor reproducible: qa/m3-postman/run.mjs. Las contraseñas del entorno se entregan vacías.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
  },
  item: [preparacion, productiva, prueba],
  variable: GENERATED.map((key) => ({ key, value: "" })),
};

const environment = {
  name: "PRISALY M3 local",
  values: [
    ["baseUrl", "http://localhost:8080/api"],
    ["docenteEmail", "docente.bruno@prisaly.local"], ["docentePassword", ""],
    ["sinPermisoEmail", "sinperm.bruno@prisaly.local"], ["sinPermisoPassword", ""],
    ["otroDocenteEmail", "other.qa@prisaly.invalid"], ["otroDocentePassword", ""],
  ].map(([key, value]) => ({ key, value, type: key.endsWith("Password") ? "secret" : "default", enabled: true })),
};

writeFileSync(COLLECTION, JSON.stringify(collection, null, 2) + "\n");
writeFileSync(ENVIRONMENT, JSON.stringify(environment, null, 2) + "\n");
console.log(`Colección: ${collection.item.reduce((n, f) => n + f.item.length, 0)} solicitudes`);
