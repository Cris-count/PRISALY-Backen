import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const dir = dirname(fileURLToPath(import.meta.url));
const collectionPath = join(dir, "PRISALY-Flujos.postman_collection.json");
const environmentPath = join(dir, "PRISALY-Flujos.postman_environment.json");

function tests(lines) {
  return [{
    listen: "test",
    script: { type: "text/javascript", exec: lines.replace(/^\n/, "").replace(/\n$/, "").split("\n") },
  }];
}

function item(name, method, path, description, script, { body, headers, save } = {}) {
  const header = (headers || []).map(([key, value]) => ({ key, value, type: "text" }));
  const req = { method, auth: { type: "noauth" }, header, url: `{{baseUrl}}${path}`, description };
  if (body != null) {
    req.body = { mode: "raw", raw: `${body.trim()}\n`, options: { raw: { language: "json" } } };
    if (!header.some((h) => h.key.toLowerCase() === "content-type")) {
      header.unshift({ key: "Content-Type", value: "application/json", type: "text" });
    }
  }
  const required = [...new Set([...JSON.stringify(req).matchAll(/{{(\w+)}}/g)].map((m) => m[1]))]
    .filter((key) => !["baseUrl"].includes(key));
  const preflight = [
    `const required = ${JSON.stringify(required)};`,
    'const missing = required.filter((key) => !String(pm.variables.get(key) ?? "").trim());',
    'if (missing.length) {',
    '  console.warn("PRISALY: faltan variables: " + missing.join(", "));',
    '  pm.execution.skipRequest();',
    '}',
  ];
  if (save) {
    preflight.push(
      'const stamp = Date.now().toString(16);',
      'const id = (prefix) => `${prefix}-0000-4000-8000-${stamp.padStart(12, "0").slice(-12)}`;',
      'if (!pm.collectionVariables.get("flujoCasoId")) pm.collectionVariables.set("flujoCasoId", id("11111111"));',
      'if (!pm.collectionVariables.get("flujoEscenarioId")) pm.collectionVariables.set("flujoEscenarioId", id("22222222"));',
      'if (!pm.collectionVariables.get("flujoPreguntaId")) pm.collectionVariables.set("flujoPreguntaId", id("33333333"));',
      'if (!pm.collectionVariables.get("flujoOpcionId")) pm.collectionVariables.set("flujoOpcionId", id("44444444"));',
      'if (!pm.collectionVariables.get("flujoCriterioId")) pm.collectionVariables.set("flujoCriterioId", id("55555555"));',
      'if (!pm.collectionVariables.get("flujoDocenteCorreo")) pm.collectionVariables.set("flujoDocenteCorreo", `docente.flujo.${stamp}@prisaly.local`);',
    );
  }
  return {
    name,
    request: req,
    event: [{ listen: "prerequest", script: { type: "text/javascript", exec: preflight } }, ...tests(script)],
  };
}

const AUTH = [["Authorization", "Bearer {{accessToken}}"]];
const NO_PERM = [["Authorization", "Bearer {{noPermAccessToken}}"]];
const ok = (code) => `pm.test("HTTP ${code}", () => pm.response.to.have.status(${code}));`;
const jsonCode = (code, err) => `
${ok(code)}
pm.test("codigo ${err}", () => pm.expect(pm.response.json().code).to.eql("${err}"));
`;
const login = (email, password, variable) => `
${ok(200)}
const body = pm.response.json();
pm.test("token", () => pm.expect(body.accessToken).to.be.a("string").and.not.empty);
pm.collectionVariables.set("${variable}", body.accessToken);
`;

const collection = {
  info: {
    name: "PRISALY — Flujos",
    description: "Una carpeta es un recorrido. Los negativos no reemplazan accessToken. Las contraseñas van solo en el valor actual del entorno. Las carpetas 02 a 06 vuelven a iniciar sesión porque 01 cierra la suya. HTTP 201 de convocatoria no es entrega de correo. El acceso estudiantil productivo responde 501.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
  },
  variable: [
    ["accessToken", ""], ["noPermAccessToken", ""], ["sesionId", ""], ["convocatoriaId", ""],
    ["flujoCasoId", ""], ["flujoEscenarioId", ""], ["flujoPreguntaId", ""], ["flujoOpcionId", ""],
    ["flujoCriterioId", ""], ["etagCaso", ""], ["flujoDocenteId", ""], ["flujoDocenteCorreo", ""],
    ["flujoPermisoId", ""], ["sesionAjenaId", ""],
  ].map(([key, value]) => ({ key, value })),
  item: [
    {
      name: "00 — Sistema",
      description: "Si esta carpeta falla, no continúes.",
      item: [item("00-01 Salud", "GET", "/health", "El backend debe responder antes de cualquier flujo.", `
${ok(200)}
pm.test("ok", () => pm.expect(pm.response.json().status).to.eql("ok"));
`)],
    },
    {
      name: "01 — Inicio de sesión del personal",
      description: "El inicio correcto guarda accessToken. Los negativos no lo pisan. El cierre queda al final de esta carpeta.",
      item: [
        item("01-01 Iniciar sesión", "POST", "/v1/personal/autenticar", "Docente activo del seed.", login("personnelEmail", "personnelPassword", "accessToken"), {
          body: '{ "correo": "{{personnelEmail}}", "password": "{{personnelPassword}}" }',
        }),
        item("01-02 Clave incorrecta", "POST", "/v1/personal/autenticar", "No modifica accessToken.", jsonCode(401, "AUTH_FAILED"), {
          body: '{ "correo": "{{personnelEmail}}", "password": "clave-incorrecta-flujo" }',
        }),
        item("01-03 Usuario inexistente", "POST", "/v1/personal/autenticar", "La respuesta no distingue correo inexistente de clave mala.", jsonCode(401, "AUTH_FAILED"), {
          body: '{ "correo": "{{unknownEmail}}", "password": "clave-inexistente-flujo" }',
        }),
        item("01-04 Usuario inactivo", "POST", "/v1/personal/autenticar", "Requiere el usuario inactivo del seed.", jsonCode(401, "AUTH_FAILED"), {
          body: '{ "correo": "{{inactiveEmail}}", "password": "{{inactivePassword}}" }',
        }),
        item("01-05 Datos inválidos", "POST", "/v1/personal/autenticar", "Cuerpo sin contraseña.", jsonCode(400, "VALIDATION_ERROR"), {
          body: '{ "correo": "{{personnelEmail}}" }',
        }),
        item("01-06 Cerrar sesión", "POST", "/v1/personal/cerrar-sesion", "Revoca el token guardado en 01-01.", ok(204), { headers: AUTH }),
        item("01-07 Token revocado", "GET", "/v1/personal/me", "El mismo token ya no identifica.", jsonCode(401, "UNAUTHENTICATED"), { headers: AUTH }),
      ],
    },
    {
      name: "02 — Identidad y permiso",
      description: "Vuelve a iniciar sesión. El cambio de contraseña no se ejecuta solo: deja la variable de colección ejecutarCambioContrasena en si y restaura la clave del seed después.",
      item: [
        item("02-01 Iniciar sesión", "POST", "/v1/personal/autenticar", "Renueva accessToken.", login("personnelEmail", "personnelPassword", "accessToken"), {
          body: '{ "correo": "{{personnelEmail}}", "password": "{{personnelPassword}}" }',
        }),
        item("02-02 Consultar identidad", "GET", "/v1/personal/me", "Usa el token de 02-01.", `
${ok(200)}
pm.test("correo", () => pm.expect(pm.response.json().correo).to.eql(pm.variables.get("personnelEmail")));
`, { headers: AUTH }),
        item("02-03 Crear caso permitido", "GET", "/v1/personal/autorizacion/crear-caso", "El docente del seed tiene CREAR_CASO.", ok(200), { headers: AUTH }),
        item("02-04 Sin token", "GET", "/v1/personal/me", "No envía Authorization.", jsonCode(401, "UNAUTHENTICATED")),
        item("02-05 Token inválido", "GET", "/v1/personal/me", "No reemplaza accessToken.", jsonCode(401, "UNAUTHENTICATED"), {
          headers: [["Authorization", "Bearer token-invalido"]],
        }),
        item("02-06 Iniciar sesión sin permiso", "POST", "/v1/personal/autenticar", "Guarda noPermAccessToken y conserva accessToken.", login("personnelNoPermEmail", "personnelNoPermPassword", "noPermAccessToken"), {
          body: '{ "correo": "{{personnelNoPermEmail}}", "password": "{{personnelNoPermPassword}}" }',
        }),
        item("02-07 Crear caso denegado", "GET", "/v1/personal/autorizacion/crear-caso", "El segundo docente no tiene CREAR_CASO.", jsonCode(403, "FORBIDDEN"), { headers: NO_PERM }),
        item("02-08 Cambiar contraseña", "PUT", "/v1/personal/contrasena", "Omitida salvo ejecutarCambioContrasena=si. Cambia la clave del seed.", `
${ok(204)}
`, {
          headers: AUTH,
          body: '{ "actual": "{{personnelPassword}}", "nueva": "{{personnelPasswordNueva}}" }',
        }),
      ],
    },
    {
      name: "03 — Administración de identidad",
      description: "Cualquier personal autenticado llega a estas rutas. No hay cambio de rol: BD-005.2 admite un solo rol por usuario.",
      item: [
        item("03-01 Iniciar sesión", "POST", "/v1/personal/autenticar", "Renueva accessToken.", login("personnelEmail", "personnelPassword", "accessToken"), {
          body: '{ "correo": "{{personnelEmail}}", "password": "{{personnelPassword}}" }',
        }),
        item("03-02 Listar roles", "GET", "/v1/admin/roles", "Catálogo persistido.", ok(200), { headers: AUTH }),
        item("03-03 Listar permisos", "GET", "/v1/admin/permisos", "Guarda el primer idPermiso para asignarlo y quitarlo.", `
${ok(200)}
const items = pm.response.json().items || [];
pm.test("hay permisos", () => pm.expect(items.length).to.be.above(0));
pm.collectionVariables.set("flujoPermisoId", String(items[0].idPermiso));
`, { headers: AUTH }),
        item("03-04 Alta de docente", "POST", "/v1/admin/docentes", "Correo nuevo en cada ejecución. No incluye contraseña.", `
${ok(201)}
pm.collectionVariables.set("flujoDocenteId", pm.response.json().idUsuario);
`, { headers: AUTH, save: true, body: '{ "nombre": "Docente flujo", "correo": "{{flujoDocenteCorreo}}" }' }),
        item("03-05 Asignar permiso", "PUT", "/v1/admin/usuarios/{{flujoDocenteId}}/permisos/{{flujoPermisoId}}", "Sobre el docente recién creado.", ok(200), { headers: AUTH }),
        item("03-06 Quitar permiso", "DELETE", "/v1/admin/usuarios/{{flujoDocenteId}}/permisos/{{flujoPermisoId}}", "Revoca la asignación anterior.", ok(204), { headers: AUTH }),
      ],
    },
    {
      name: "04 — Caso académico",
      description: "Crea un borrador, le agrega contenido y una rúbrica con criterio, publica y archiva. Publicar exige pregunta, una opción correcta y rúbrica con criterio. Las escrituras reenvían el ETag anterior.",
      item: [
        item("04-01 Iniciar sesión", "POST", "/v1/personal/autenticar", "Docente con CREAR_CASO.", login("personnelEmail", "personnelPassword", "accessToken"), {
          body: '{ "correo": "{{personnelEmail}}", "password": "{{personnelPassword}}" }',
        }),
        item("04-02 Crear borrador", "POST", "/v1/casos", "UUID nuevo de esta ejecución.", `
${ok(201)}
pm.test("borrador", () => pm.expect(pm.response.json().estado).to.eql("BORRADOR"));
pm.collectionVariables.set("flujoCasoId", pm.response.json().idCaso);
const etag = pm.response.headers.get("ETag");
if (etag) pm.collectionVariables.set("etagCaso", etag);
`, { headers: AUTH, save: true, body: `{
  "idCaso": "{{flujoCasoId}}",
  "titulo": "Caso del flujo",
  "tiempoEstimadoSegundos": 300,
  "historia": null,
  "desarrolloSituacional": null
}` }),
        item("04-03 Consultar caso", "GET", "/v1/casos/{{flujoCasoId}}", "Guarda el ETag para la siguiente escritura.", `
${ok(200)}
const etag = pm.response.headers.get("ETag");
pm.test("etag", () => pm.expect(etag).to.be.a("string").and.not.empty);
pm.collectionVariables.set("etagCaso", etag);
`, { headers: AUTH }),
        item("04-04 Agregar escenario", "POST", "/v1/casos/{{flujoCasoId}}/escenarios", "If-Match del GET anterior.", `
${ok(201)}
const etag = pm.response.headers.get("ETag");
if (etag) pm.collectionVariables.set("etagCaso", etag);
`, { headers: [...AUTH, ["If-Match", "{{etagCaso}}"]], save: true, body: `{
  "idEscenario": "{{flujoEscenarioId}}",
  "titulo": "Escenario del flujo",
  "contexto": "Contexto de prueba",
  "instrucciones": "Instrucciones de prueba",
  "orden": 1
}` }),
        item("04-05 Agregar pregunta", "POST", "/v1/casos/{{flujoCasoId}}/escenarios/{{flujoEscenarioId}}/preguntas", "Una pregunta de selección.", `
${ok(201)}
const etag = pm.response.headers.get("ETag");
if (etag) pm.collectionVariables.set("etagCaso", etag);
`, { headers: [...AUTH, ["If-Match", "{{etagCaso}}"]], body: `{
  "idPregunta": "{{flujoPreguntaId}}",
  "enunciado": "Pregunta del flujo",
  "textoAyuda": null,
  "orden": 1
}` }),
        item("04-06 Agregar opción correcta", "POST", "/v1/casos/{{flujoCasoId}}/preguntas/{{flujoPreguntaId}}/opciones", "Una sola opción correcta.", `
${ok(201)}
const etag = pm.response.headers.get("ETag");
if (etag) pm.collectionVariables.set("etagCaso", etag);
`, { headers: [...AUTH, ["If-Match", "{{etagCaso}}"]], body: `{
  "idOpcion": "{{flujoOpcionId}}",
  "texto": "Opción correcta",
  "esCorrecta": true,
  "explicacion": "Es la opción prevista por la prueba",
  "orden": 1
}` }),
        item("04-07 Consultar contenido", "GET", "/v1/casos/{{flujoCasoId}}/contenido", "Lectura del docente dueño.", ok(200), { headers: AUTH }),
        item("04-08 Guardar rúbrica", "PUT", "/v1/casos/{{flujoCasoId}}/rubrica", "Un criterio basta para la regla estructural de completitud. No calcula una nota.", `
${ok(200)}
const etag = pm.response.headers.get("ETag");
if (etag) pm.collectionVariables.set("etagCaso", etag);
`, { headers: [...AUTH, ["If-Match", "{{etagCaso}}"]], body: `{
  "nombre": "Rubrica del flujo",
  "descripcion": "Criterio estructural de prueba",
  "puntajeTotal": 100,
  "criterios": [{ "id": "{{flujoCriterioId}}", "descripcion": "Criterio", "puntaje": 100, "ponderacion": 1 }],
  "preguntas": [{ "idPregunta": "{{flujoPreguntaId}}", "valorPregunta": 100, "criterio": "{{flujoCriterioId}}" }]
}` }),
        item("04-09 Publicar", "POST", "/v1/casos/{{flujoCasoId}}/publicar", "Pasa a Publicado solo si la validación académica lo acepta.", `
${ok(200)}
pm.test("publicado", () => pm.expect(pm.response.json().estado).to.eql("PUBLICADO"));
const etag = pm.response.headers.get("ETag");
if (etag) pm.collectionVariables.set("etagCaso", etag);
`, { headers: [...AUTH, ["If-Match", "{{etagCaso}}"]] }),
        item("04-10 Archivar", "POST", "/v1/casos/{{flujoCasoId}}/archivar", "Solo después de publicar.", `
${ok(200)}
pm.test("archivado", () => pm.expect(pm.response.json().estado).to.eql("ARCHIVADO"));
`, { headers: [...AUTH, ["If-Match", "{{etagCaso}}"]] }),
        item("04-11 Sin token", "POST", "/v1/casos", "No crea el caso.", jsonCode(401, "UNAUTHENTICATED"), {
          body: '{ "idCaso": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "titulo": "Sin sesion", "tiempoEstimadoSegundos": 300, "historia": null, "desarrolloSituacional": null }',
        }),
        item("04-12 Sin permiso", "POST", "/v1/casos", "Requiere noPermAccessToken de la carpeta 02. Si corres solo esta carpeta, ejecuta antes 02-06.", jsonCode(403, "FORBIDDEN"), {
          headers: NO_PERM,
          body: '{ "idCaso": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "titulo": "Sin permiso", "tiempoEstimadoSegundos": 300, "historia": null, "desarrolloSituacional": null }',
        }),
        item("04-13 Cuerpo inválido", "POST", "/v1/casos", "Tiempo estimado en cero.", `
pm.test("rechazo", () => pm.expect(pm.response.code).to.be.oneOf([400, 422]));
`, { headers: AUTH, body: '{ "idCaso": "cccccccc-cccc-4ccc-8ccc-cccccccccccc", "titulo": "Invalido", "tiempoEstimadoSegundos": 0, "historia": null, "desarrolloSituacional": null }' }),
        item("04-14 Caso de otro docente", "GET", "/v1/casos/{{flujoCasoId}}", "El docente sin CREAR_CASO no es el dueño. Requiere noPermAccessToken de 02-06.", `
pm.test("no es el dueño", () => pm.expect(pm.response.code).to.be.oneOf([403, 404]));
`, { headers: NO_PERM }),
      ],
    },
    {
      name: "05 — Sesión y convocatoria",
      description: "Usa el caso publicado y el estudiante del seed, no el caso archivado de la carpeta 04. La convocatoria se hace en SinIniciar: al pasar a EnCurso ya no se admiten convocatorias. El 201 no prueba el correo.",
      item: [
        item("05-01 Iniciar sesión", "POST", "/v1/personal/autenticar", "El docente del seed tiene GESTIONAR_SESION.", login("personnelEmail", "personnelPassword", "accessToken"), {
          body: '{ "correo": "{{personnelEmail}}", "password": "{{personnelPassword}}" }',
        }),
        item("05-02 Crear sesión", "POST", "/m4/sesiones", "Caso publicado del seed.", `
${ok(201)}
pm.collectionVariables.set("sesionId", pm.response.json().id);
`, { headers: AUTH, body: `{
  "casoId": "{{casoIdBruno}}",
  "titulo": "Sesion del flujo",
  "mensajePersonalizado": "Convocatoria de prueba del flujo",
  "fechaInicio": "2030-11-10T14:00:00Z",
  "fechaFin": "2030-11-10T16:00:00Z",
  "tiempoMaximoParticipacionSegundos": 3600
}` }),
        item("05-03 Consultar sesión", "GET", "/m4/sesiones/{{sesionId}}", "La sesión recién creada.", ok(200), { headers: AUTH }),
        item("05-04 Convocar estudiante", "POST", "/m4/sesiones/{{sesionId}}/convocatorias", "201 significa convocatoria aceptada, no correo entregado.", `
${ok(201)}
pm.collectionVariables.set("convocatoriaId", pm.response.json().id);
`, { headers: AUTH, body: '{ "estudianteId": "{{estudianteIdBruno}}" }' }),
        item("05-05 Listar convocatorias", "GET", "/m4/sesiones/{{sesionId}}/convocatorias", "Incluye la convocatoria anterior.", ok(200), { headers: AUTH }),
        item("05-06 Pasar a EnCurso", "PATCH", "/m4/sesiones/{{sesionId}}/estado", "Después de convocar. EnCurso ya no admite otra convocatoria.", ok(200), {
          headers: AUTH, body: '{ "nuevoEstado": "EnCurso" }',
        }),
        item("05-07 Sin permiso", "POST", "/m4/sesiones", "Requiere noPermAccessToken.", jsonCode(403, "M4-SIN-PERMISO"), { headers: NO_PERM, body: `{
  "casoId": "{{casoIdBruno}}",
  "titulo": "Sesion sin permiso",
  "mensajePersonalizado": "No debe crearse",
  "fechaInicio": "2030-11-10T14:00:00Z",
  "fechaFin": "2030-11-10T16:00:00Z",
  "tiempoMaximoParticipacionSegundos": 3600
}` }),
        item("05-08 Sin token", "POST", "/m4/sesiones/{{sesionId}}/convocatorias", "No convoca.", jsonCode(401, "UNAUTHENTICATED"), {
          body: '{ "estudianteId": "{{estudianteIdBruno}}" }',
        }),
        item("05-09 Estudiante inexistente", "POST", "/m4/sesiones", "Se crea otra sesión en SinIniciar para no usar la que ya está EnCurso.", `
${ok(201)}
pm.collectionVariables.set("sesionAjenaId", pm.response.json().id);
`, { headers: AUTH, body: `{
  "casoId": "{{casoIdBruno}}",
  "titulo": "Sesion para estudiante inexistente",
  "mensajePersonalizado": "No debe convocar",
  "fechaInicio": "2030-12-10T14:00:00Z",
  "fechaFin": "2030-12-10T16:00:00Z",
  "tiempoMaximoParticipacionSegundos": 3600
}` }),
        item("05-10 Convocar inexistente", "POST", "/m4/sesiones/{{sesionAjenaId}}/convocatorias", "La clave foránea rechaza un estudiante que no existe.", jsonCode(409, "M4-REFERENCIA-NO-VALIDA"), { headers: AUTH, body: '{ "estudianteId": "99999999-9999-4999-8999-999999999999" }' }),
        item("05-11 Ruta de acceso retirada", "GET", "/m4/sesiones/{{sesionId}}/acceso/{{estudianteIdBruno}}", "Responde 410. No abre la participación del estudiante.", jsonCode(410, "M4-ACCESO-LEGADO-RETIRADO"), { headers: AUTH }),
      ],
    },
    {
      name: "06 — Acceso estudiantil bloqueado",
      description: "El flag productivo sigue apagado. Estas peticiones no guardan cookie ni identificadores.",
      item: [
        item("06-01 Validar bloqueado", "POST", "/v1/acceso-estudiante/validar", "Cuerpo válido y DD-02 apagada.", jsonCode(501, "ESTUDIANTE_ACCESS_PENDING_CONTRACT"), {
          body: '{ "challengeId": "challenge-flujo", "codigo": "12345678" }',
        }),
        item("06-02 Validar inválido", "POST", "/v1/acceso-estudiante/validar", "El código no tiene ocho dígitos.", jsonCode(400, "VALIDATION_ERROR"), {
          body: '{ "challengeId": "challenge-flujo", "codigo": "12" }',
        }),
        item("06-03 Activar bloqueado", "POST", "/v1/acceso-estudiante/activar", "No crea la contraseña.", jsonCode(501, "ESTUDIANTE_ACCESS_PENDING_CONTRACT"), {
          body: '{ "challengeId": "11111111-1111-4111-8111-111111111111", "codigo": "12345678", "contrasena": "contrasena-flujo-segura" }',
        }),
        item("06-04 Autenticar bloqueado", "POST", "/v1/acceso-estudiante/autenticar", "No abre sesión estudiantil.", jsonCode(501, "ESTUDIANTE_ACCESS_PENDING_CONTRACT"), {
          body: '{ "correo": "estudiante.flujo@prisaly.local", "contrasena": "contrasena-flujo-segura" }',
        }),
      ],
    },
    {
      name: "07 — Flujos que el backend no permite",
      description: "No hay peticiones de éxito. El estudiante no tiene sesión productiva ni HTTP de participación. M5 no publica intentos. M6 no publica evaluación. M7 no tiene API de avisos ni entrega de correo. No hay alta pública del estudiante ni envío del código.",
      item: [],
    },
  ],
};

const passwordRequest = collection.item[2].item.find((entry) => entry.name.startsWith("02-08"));
passwordRequest.event[0].script.exec.unshift(
  'if (pm.collectionVariables.get("ejecutarCambioContrasena") !== "si") {',
  '  console.warn("PRISALY: cambio de contraseña omitido. No altera el seed.");',
  '  pm.execution.skipRequest();',
  '}',
);

writeFileSync(collectionPath, JSON.stringify(collection, null, 2));
writeFileSync(environmentPath, JSON.stringify({
  id: "prisaly-flujos-local",
  name: "PRISALY Flujos local",
  values: [
    ["baseUrl", "http://localhost:8080/api", "default"],
    ["personnelEmail", "docente.bruno@prisaly.local", "default"],
    ["personnelPassword", "", "secret"],
    ["personnelPasswordNueva", "", "secret"],
    ["personnelNoPermEmail", "sinperm.bruno@prisaly.local", "default"],
    ["personnelNoPermPassword", "", "secret"],
    ["inactiveEmail", "inactive.bruno@prisaly.local", "default"],
    ["inactivePassword", "", "secret"],
    ["unknownEmail", "ghost.bruno@prisaly.local", "default"],
    ["casoIdBruno", "33333333-3333-3333-3333-333333333301", "default"],
    ["estudianteIdBruno", "22222222-2222-2222-2222-222222222201", "default"],
  ].map(([key, value, type]) => ({ key, value, type, enabled: true })),
  _postman_variable_scope: "environment",
}, null, 2));
