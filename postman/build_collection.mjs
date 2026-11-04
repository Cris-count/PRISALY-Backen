import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const OUT = join(dirname(fileURLToPath(import.meta.url)), "PRISALY-Integrante-1.postman_collection.json");

function tests(lines) {
  return [
    {
      listen: "test",
      script: {
        type: "text/javascript",
        exec: lines.replace(/^\n/, "").replace(/\n$/, "").split("\n"),
      },
    },
  ];
}

function item(name, method, path, description, script, { body, headers } = {}) {
  const header = (headers || []).map(([key, value]) => ({ key, value, type: "text" }));
  const req = {
    method,
    header,
    url: `{{baseUrl}}${path}`,
    description,
  };
  if (body != null) {
    req.body = {
      mode: "raw",
      raw: `${body.trim()}\n`,
      options: { raw: { language: "json" } },
    };
    if (!header.some((h) => h.key.toLowerCase() === "content-type")) {
      header.unshift({ key: "Content-Type", value: "application/json", type: "text" });
    }
  }
  return { name, request: req, event: tests(script) };
}

const AUTH = [["Authorization", "Bearer {{accessToken}}"]];
const NO_PERM = [["Authorization", "Bearer {{noPermAccessToken}}"]];
const JSONH = [["Content-Type", "application/json"]];
const JSON_AUTH = [...JSONH, ...AUTH];
const JSON_NOPERM = [...JSONH, ...NO_PERM];

const jsonCode = (code, err) => `
pm.test("HTTP ${code}", function () { pm.response.to.have.status(${code}); });
pm.test("JSON", function () { pm.response.to.be.json; });
const body = pm.response.json();
pm.test("code ${err}", function () { pm.expect(body.code).to.eql("${err}"); });
`;

const collection = {
  info: {
    name: "PRISALY — Integrante 1 (M1/M7)",
    description:
      "Espejo de bruno/ (carpetas 00–05). Tokens en variables de colección (accessToken, noPermAccessToken, sesionId). Contraseñas solo en Current value del entorno. Orden: 00 → 05. HTTP 201 de convocatoria no implica SMTP. DD-02 deshabilitada.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
  },
  variable: [
    { key: "accessToken", value: "" },
    { key: "noPermAccessToken", value: "" },
    { key: "sesionId", value: "" },
    { key: "convocatoriaId", value: "" },
  ],
  item: [
    {
      name: "00-verificacion-sistema",
      item: [
        item(
          "00-01 | Salud del backend | GET /health",
          "GET",
          "/health",
          "PRE-00. Primera solicitud. Backend en :8080.",
          `
pm.test("HTTP 200", function () { pm.response.to.have.status(200); });
pm.test("JSON", function () { pm.response.to.be.json; });
const body = pm.response.json();
pm.test("service", function () { pm.expect(body.service).to.eql("prisaly-backend"); });
pm.test("status ok", function () { pm.expect(body.status).to.eql("ok"); });
`,
        ),
      ],
    },
    {
      name: "01-m1-autenticacion",
      item: [
        item(
          "01-01 | M1-POST-01 | Iniciar sesión correctamente",
          "POST",
          "/v1/personal/autenticar",
          "Guarda accessToken en variable de colección. Password vacío → 400 (entorno).",
          `
pm.test("HTTP 200", function () { pm.response.to.have.status(200); });
pm.test("JSON", function () { pm.response.to.be.json; });
const body = pm.response.json();
pm.test("accessToken", function () { pm.expect(body.accessToken).to.be.ok; });
pm.test("tokenType", function () { pm.expect(body.tokenType).to.eql("Bearer"); });
pm.test("authPrototype", function () { pm.expect(body.authPrototype).to.eql("TD-M1-AUTH-PROVISIONAL"); });
pm.test("correo", function () { pm.expect(body.correo).to.eql(pm.environment.get("personnelEmail")); });
if (pm.response.code === 200 && body.accessToken) {
    pm.collectionVariables.set("accessToken", body.accessToken);
}
`,
          {
            body: `{
  "correo": "{{personnelEmail}}",
  "password": "{{personnelPassword}}"
}`,
            headers: JSONH,
          },
        ),
        item(
          "01-02 | M1-POST-02 | Rechazar contraseña incorrecta",
          "POST",
          "/v1/personal/autenticar",
          "No guarda ni sobrescribe accessToken.",
          jsonCode(401, "AUTH_FAILED"),
          {
            body: `{
  "correo": "{{personnelEmail}}",
  "password": "definitely-wrong-password"
}`,
            headers: JSONH,
          },
        ),
        item(
          "01-03 | M1-POST-03 | Rechazar usuario inexistente",
          "POST",
          "/v1/personal/autenticar",
          "No sobrescribe accessToken.",
          jsonCode(401, "AUTH_FAILED"),
          {
            body: `{
  "correo": "{{unknownEmail}}",
  "password": "{{personnelPassword}}"
}`,
            headers: JSONH,
          },
        ),
        item(
          "01-04 | M1-NEG-01 | Rechazar usuario inactivo",
          "POST",
          "/v1/personal/autenticar",
          "Seed M1 inactive.bruno. No sobrescribe accessToken.",
          jsonCode(401, "AUTH_FAILED"),
          {
            body: `{
  "correo": "{{inactiveEmail}}",
  "password": "{{inactivePassword}}"
}`,
            headers: JSONH,
          },
        ),
        item(
          "01-05 | M1-POST-07 | Rechazar datos de autenticación inválidos",
          "POST",
          "/v1/personal/autenticar",
          "No sobrescribe accessToken.",
          jsonCode(400, "VALIDATION_ERROR"),
          {
            body: `{
  "correo": "no-es-un-correo",
  "password": ""
}`,
            headers: JSONH,
          },
        ),
      ],
    },
    {
      name: "02-m1-identidad-permisos",
      item: [
        item(
          "02-01 | M1-PERF-01 | Consultar mi identidad",
          "GET",
          "/v1/personal/me",
          "Precondición: 01-01 (accessToken).",
          `
pm.test("HTTP 200", function () { pm.response.to.have.status(200); });
pm.test("JSON", function () { pm.response.to.be.json; });
const body = pm.response.json();
pm.test("correo", function () { pm.expect(body.correo).to.eql(pm.environment.get("personnelEmail")); });
pm.test("tokenType", function () { pm.expect(body.tokenType).to.eql("Bearer"); });
pm.test("authPrototype", function () { pm.expect(body.authPrototype).to.eql("TD-M1-AUTH-PROVISIONAL"); });
`,
          { headers: AUTH },
        ),
        item(
          "02-02 | M1-GET-04 | Rechazar consulta sin token",
          "GET",
          "/v1/personal/me",
          "Sin Authorization. No altera accessToken.",
          jsonCode(401, "UNAUTHENTICATED"),
        ),
        item(
          "02-03 | M1-GET-06 | Permitir operación con CREAR_CASO",
          "GET",
          "/v1/personal/autorizacion/crear-caso",
          "Precondición: 01-01.",
          `
pm.test("HTTP 200", function () { pm.response.to.have.status(200); });
pm.test("JSON", function () { pm.response.to.be.json; });
const body = pm.response.json();
pm.test("allowed", function () { pm.expect(body.allowed).to.eql(true); });
pm.test("permission", function () { pm.expect(body.permission).to.eql("CREAR_CASO"); });
pm.test("userId", function () { pm.expect(body.userId).to.be.ok; });
`,
          { headers: AUTH },
        ),
        item(
          "02-04-prep | Autenticar usuario sin CREAR_CASO",
          "POST",
          "/v1/personal/autenticar",
          "Guarda noPermAccessToken. No sobrescribe accessToken del docente.",
          `
pm.test("HTTP 200", function () { pm.response.to.have.status(200); });
pm.test("JSON", function () { pm.response.to.be.json; });
const body = pm.response.json();
pm.test("accessToken", function () { pm.expect(body.accessToken).to.be.ok; });
if (pm.response.code === 200 && body.accessToken) {
    pm.collectionVariables.set("noPermAccessToken", body.accessToken);
}
`,
          {
            body: `{
  "correo": "{{personnelNoPermEmail}}",
  "password": "{{personnelNoPermPassword}}"
}`,
            headers: JSONH,
          },
        ),
        item(
          "02-04 | M1-GET-05 | Rechazar operación sin CREAR_CASO",
          "GET",
          "/v1/personal/autorizacion/crear-caso",
          "Precondición: 02-04-prep. Usa noPermAccessToken.",
          jsonCode(403, "FORBIDDEN"),
          { headers: NO_PERM },
        ),
        item(
          "02-05 | M1-NEG-02 | Rechazar token inválido",
          "GET",
          "/v1/personal/me",
          "Bearer inexistente. No altera accessToken.",
          jsonCode(401, "UNAUTHENTICATED"),
          { headers: [["Authorization", "Bearer token-invalido-no-emitido"]] },
        ),
        item(
          "02-06 | M1-NEG | Rechazar CREAR_CASO sin token",
          "GET",
          "/v1/personal/autorizacion/crear-caso",
          "Equivalente a personal-autorizacion-crear-caso-unauthenticated.",
          jsonCode(401, "UNAUTHENTICATED"),
        ),
      ],
    },
    {
      name: "03-m1-acceso-estudiantil",
      item: [
        item(
          "03-01 | M1-POST-08 | Mantener bloqueado DD-02",
          "POST",
          "/v1/acceso-estudiante/validar",
          "DTO válido. Flag false → 501.",
          jsonCode(501, "ESTUDIANTE_ACCESS_PENDING_CONTRACT"),
          {
            body: `{
  "challengeId": "00000000-0000-0000-0000-000000000001",
  "codigo": "12345678"
}`,
            headers: JSONH,
          },
        ),
        item(
          "03-02 | M1-NEG-03 | Rechazar datos inválidos de acceso estudiantil",
          "POST",
          "/v1/acceso-estudiante/validar",
          "Body inválido → 400 VALIDATION_ERROR.",
          jsonCode(400, "VALIDATION_ERROR"),
          {
            body: `{
  "correo": "estudiante@prisaly.test",
  "codigo": "PENDIENTE-DD02"
}`,
            headers: JSONH,
          },
        ),
      ],
    },
    {
      name: "04-m7-integracion-m4",
      item: [
        item(
          "04-01 | M7-PREP-01 | Autenticar docente con GESTIONAR_SESION",
          "POST",
          "/v1/personal/autenticar",
          "Refresca accessToken. Seed M7: GESTIONAR_SESION.",
          `
pm.test("HTTP 200", function () { pm.response.to.have.status(200); });
pm.test("JSON", function () { pm.response.to.be.json; });
const body = pm.response.json();
pm.test("accessToken", function () { pm.expect(body.accessToken).to.be.ok; });
pm.test("authPrototype", function () { pm.expect(body.authPrototype).to.eql("TD-M1-AUTH-PROVISIONAL"); });
if (pm.response.code === 200 && body.accessToken) {
    pm.collectionVariables.set("accessToken", body.accessToken);
}
`,
          {
            body: `{
  "correo": "{{personnelEmail}}",
  "password": "{{personnelPassword}}"
}`,
            headers: JSONH,
          },
        ),
        item(
          "04-02 | M7-PREP-02 | Crear sesión académica",
          "POST",
          "/m4/sesiones",
          "Guarda sesionId. HTTP 201 no implica SMTP.",
          `
pm.test("HTTP 201", function () { pm.response.to.have.status(201); });
pm.test("JSON", function () { pm.response.to.be.json; });
const body = pm.response.json();
pm.test("id", function () { pm.expect(body.id).to.be.ok; });
pm.test("casoId", function () { pm.expect(body.casoId).to.eql(pm.environment.get("casoIdBruno")); });
if (pm.response.code === 201 && body.id) {
    pm.collectionVariables.set("sesionId", body.id);
}
`,
          {
            body: `{
  "casoId": "{{casoIdBruno}}",
  "titulo": "Sesion Bruno M7",
  "mensajePersonalizado": "Convocatoria de prueba M4-M7 (Bruno)",
  "fechaInicio": "2030-11-10T14:00:00Z",
  "fechaFin": "2030-11-10T16:00:00Z",
  "tiempoMaximoParticipacionSegundos": 3600
}`,
            headers: JSON_AUTH,
          },
        ),
        item(
          "04-03 | M7-via-M4-01 | Convocar estudiante",
          "POST",
          "/m4/sesiones/{{sesionId}}/convocatorias",
          "201 confirma convocatoria, no SMTP. Precondición: sesionId de 04-02.",
          `
pm.test("HTTP 201", function () { pm.response.to.have.status(201); });
pm.test("JSON", function () { pm.response.to.be.json; });
const body = pm.response.json();
pm.test("id", function () { pm.expect(body.id).to.be.ok; });
pm.test("sesionId", function () { pm.expect(body.sesionId).to.be.ok; });
pm.test("estudianteId", function () { pm.expect(body.estudianteId).to.eql(pm.environment.get("estudianteIdBruno")); });
if (pm.response.code === 201 && body.id) {
    pm.collectionVariables.set("convocatoriaId", body.id);
}
`,
          {
            body: `{
  "estudianteId": "{{estudianteIdBruno}}"
}`,
            headers: JSON_AUTH,
          },
        ),
        item(
          "04-04 | M7-VER-01 | Consultar convocatorias",
          "GET",
          "/m4/sesiones/{{sesionId}}/convocatorias",
          "Lista HTTP M4. No prueba SMTP.",
          `pm.test("HTTP 200", function () { pm.response.to.have.status(200); });`,
          { headers: AUTH },
        ),
        item(
          "04-05-prep | Autenticar docente sin GESTIONAR_SESION",
          "POST",
          "/v1/personal/autenticar",
          "Guarda noPermAccessToken. No sobrescribe accessToken del docente.",
          `
pm.test("HTTP 200", function () { pm.response.to.have.status(200); });
const body = pm.response.json();
pm.test("accessToken", function () { pm.expect(body.accessToken).to.be.ok; });
if (pm.response.code === 200 && body.accessToken) {
    pm.collectionVariables.set("noPermAccessToken", body.accessToken);
}
`,
          {
            body: `{
  "correo": "{{personnelNoPermEmail}}",
  "password": "{{personnelNoPermPassword}}"
}`,
            headers: JSONH,
          },
        ),
        item(
          "04-05 | M7-via-M4-02 | Rechazar operación sin permiso",
          "POST",
          "/m4/sesiones",
          "403 M4-SIN-PERMISO. No altera sesionId.",
          jsonCode(403, "M4-SIN-PERMISO"),
          {
            body: `{
  "casoId": "{{casoIdBruno}}",
  "titulo": "Sesion Bruno sin GESTIONAR_SESION",
  "mensajePersonalizado": "Debe fallar",
  "fechaInicio": "2030-11-11T14:00:00Z",
  "fechaFin": "2030-11-11T16:00:00Z",
  "tiempoMaximoParticipacionSegundos": 3600
}`,
            headers: JSON_NOPERM,
          },
        ),
        item(
          "04-06 | M7-via-M4-03 | Rechazar convocatoria sin token",
          "POST",
          "/m4/sesiones/{{sesionId}}/convocatorias",
          "Sin Authorization. Requiere sesionId de 04-02. Si queda {{sesionId}} literal, preparación incompleta.",
          `
pm.test("HTTP 401", function () { pm.response.to.have.status(401); });
pm.test("sesionId resuelto", function () {
    const sid = pm.collectionVariables.get("sesionId");
    pm.expect(sid, "Ejecutar 04-02 antes; sesionId no puede estar vacío").to.be.ok;
});
`,
          {
            body: `{
  "estudianteId": "{{estudianteIdBruno}}"
}`,
            headers: JSONH,
          },
        ),
      ],
    },
    {
      name: "05-m1-finalizacion",
      item: [
        item(
          "05-01 | M1-POST-09 | Cerrar sesión",
          "POST",
          "/v1/personal/cerrar-sesion",
          "DESPUÉS de 01–04. Conserva accessToken para 05-02.",
          `pm.test("HTTP 204", function () { pm.response.to.have.status(204); });`,
          { headers: AUTH },
        ),
        item(
          "05-02 | M1-VER-02 | Verificar token revocado",
          "GET",
          "/v1/personal/me",
          "El mismo accessToken debe fallar tras 05-01.",
          jsonCode(401, "UNAUTHENTICATED"),
          { headers: AUTH },
        ),
      ],
    },
  ],
};

writeFileSync(OUT, `${JSON.stringify(collection, null, 2)}\n`, "utf8");
console.log(`Wrote ${OUT}`);
