// DC-EJEC-CIERRE-01: builds PRISALY-M3-M4-QA-F4.postman_collection.json, the M3 -> M4 acceptance campaign.
// The teacher's case permissions are granted over HTTP by the real first administrator (P5 initialization), never by
// SQL. The case reaches "Publicado" ONLY through POST /v1/casos/{id}/publicar and M4 schedules that same case.
// The M3 content requests (M3-B-01..M3-B-34) are reused verbatim from PRISALY-M3-Casos.postman_collection.json;
// M3-B-09 and M3-B-33 are left out because they need a second teacher created by SQL (D-05 blocks it over HTTP).
// Usage: node postman/build_m3_m4_qa_collection.mjs
import {readFileSync, writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, 'PRISALY-M3-M4-QA-F4.postman_collection.json');
const m3 = JSON.parse(readFileSync(join(HERE, 'PRISALY-M3-Casos.postman_collection.json'), 'utf8'));
const m3Items = [];
const walk = items => items.forEach(i => (i.item ? walk(i.item) : m3Items.push(i)));
walk(m3.item);
const reuse = prefix => {
  const item = m3Items.find(i => i.name.startsWith(prefix + ' '));
  if (!item) throw new Error('M3 request not found: ' + prefix);
  return structuredClone(item);
};

const DOCENTE_ID = '11111111-1111-1111-1111-111111111101'; // Bruno fixture teacher (D-05: no HTTP teacher can log in).
const SIN_PERMISO_ID = '11111111-1111-1111-1111-111111111102'; // Bruno fixture personnel account without case permissions.
const PERMISOS_QA = ['CONSULTAR_CASO', 'MODIFICAR_CASO', 'PUBLICAR_CASO', 'GESTIONAR_SESION'];

const guard = vars => [`const requeridas = ${JSON.stringify(vars)};`,
  'const faltan = requeridas.filter(k => !String(pm.variables.get(k) ?? "").trim());',
  'if (faltan.length) {',
  '  console.warn("PRISALY M3-M4: bloqueada por variables ausentes: " + faltan.join(", "));',
  '  pm.execution.skipRequest();',
  '}'];
const errorContract = ['if (pm.response.code >= 400) {',
  '  pm.test("error con contrato code/message", () => { const b = pm.response.json();',
  '    pm.expect(b.code).to.be.a("string"); pm.expect(b.message).to.be.a("string"); });',
  '  pm.test("sin detalles internos", () => pm.expect(pm.response.text())',
  '    .to.not.match(/exception|jdbc:|org\\.(springframework|hibernate)|stacktrace|passwordhash/i));',
  '}'];

function request(id, name, method, path, {auth, headers = [], body, pre = [], status, tests = [], vars = []} = {}) {
  const used = new Set(['baseUrl', ...vars]);
  if (auth) used.add(auth);
  for (const m of `${path} ${body ?? ''} ${headers.map(h => h.value).join(' ')}`.matchAll(/\{\{(\w+)\}\}/g)) used.add(m[1]);
  const header = [...(body ? [{key: 'Content-Type', value: 'application/json', type: 'text'}] : []),
    ...(auth ? [{key: 'Authorization', value: `Bearer {{${auth}}}`, type: 'text'}] : []), ...headers];
  const segments = path.replace(/^\//, '').split('/');
  return {
    name: `${id} | ${name}`,
    event: [
      {listen: 'prerequest', script: {type: 'text/javascript', exec: [...pre, ...guard([...used].filter(v => !pre.join('').includes(`set("${v}"`)))]}},
      {listen: 'test', script: {type: 'text/javascript', exec: [`pm.test("HTTP ${status}", () => pm.response.to.have.status(${status}));`, ...errorContract, ...tests]}},
    ],
    request: {method, header, auth: {type: 'noauth'}, ...(body ? {body: {mode: 'raw', raw: body, options: {raw: {language: 'json'}}}} : {}),
      url: {raw: `{{baseUrl}}${path}`, host: ['{{baseUrl}}'], path: segments}},
    response: [],
  };
}
const login = (id, name, emailVar, passVar, tokenVar) => request(id, name, 'POST', '/v1/personal/autenticar', {
  body: `{\n  "correo": "{{${emailVar}}}",\n  "password": "{{${passVar}}}"\n}`, status: 200,
  tests: ['const t = pm.response.json().accessToken;', 'pm.test("token recibido", () => pm.expect(t).to.be.a("string").and.not.empty);',
    `if (t) pm.collectionVariables.set("${tokenVar}", t);`]});
const sesion = casoVar => `{\n  "casoId": "{{${casoVar}}}",\n  "titulo": "Sesion QA M3-M4",\n  "mensajePersonalizado": "Campana DC-EJEC-CIERRE-01",\n`
  + '  "fechaInicio": "{{fechaInicio}}",\n  "fechaFin": "{{fechaFin}}",\n  "tiempoMaximoParticipacionSegundos": 1800\n}';
const forbidden = code => [`pm.test("codigo ${code}", () => pm.expect(pm.response.json().code).to.eql("${code}"));`];
const ETAG_FICTICIO = '"m3-v2:0000000000000000000000000000000000000000000000000000000000000000"';

const preparacion = [
  request('QA-00-01', 'Salud del backend', 'GET', '/health', {status: 200}),
  login('QA-00-02', 'Autenticar primer administrador (inicializacion P5)', 'adminEmail', 'adminPassword', 'tokenAdmin'),
  request('QA-00-03', 'Consultar catalogo de permisos', 'GET', '/v1/admin/permisos', {auth: 'tokenAdmin', status: 200, tests: [
    `const requeridos = ${JSON.stringify(PERMISOS_QA)};`,
    'const porCodigo = Object.fromEntries((pm.response.json().items || []).map(p => [p.codigo, p.idPermiso]));',
    'pm.test("el catalogo contiene los permisos QA", () => requeridos.forEach(c => pm.expect(porCodigo[c], c).to.be.a("number")));',
    'requeridos.forEach(c => pm.collectionVariables.set("idPermiso_" + c, String(porCodigo[c])));']}),
  ...PERMISOS_QA.map((codigo, i) => request(`QA-00-0${4 + i}`, `Administrador asigna ${codigo} al docente QA`, 'PUT',
    `/v1/admin/usuarios/{{docenteId}}/permisos/{{idPermiso_${codigo}}}`, {auth: 'tokenAdmin', status: 200, tests: [
      'const b = pm.response.json();',
      `pm.test("asignacion habilitada", () => { pm.expect(b.habilitado).to.eql(true); pm.expect(b.idUsuario).to.eql(pm.variables.get("docenteId")); });`]})),
  request('QA-00-08', 'Permisos efectivos del docente QA', 'GET', '/v1/admin/usuarios/{{docenteId}}/permisos', {auth: 'tokenAdmin', status: 200, tests: [
    'const codigos = (pm.response.json().items || []).map(p => p.codigo).sort();',
    `pm.test("CREAR_CASO (semilla) + 4 asignados por HTTP", () => pm.expect(codigos).to.eql(${JSON.stringify(['CREAR_CASO', ...PERMISOS_QA].sort())}));`]}),
  login('QA-00-09', 'Autenticar docente QA', 'docenteEmail', 'docentePassword', 'tokenDocente'),
  login('QA-00-10', 'Autenticar personal sin permisos', 'sinPermisoEmail', 'sinPermisoPassword', 'tokenSinPermiso'),
];

const uuidPre = v => [`pm.collectionVariables.set("${v}", require("uuid").v4());`];
const productiva = [
  request('QA-P-01', 'Crear caso en Borrador (CREAR_CASO)', 'POST', '/v1/casos', {auth: 'tokenDocente', pre: uuidPre('idCasoProd'),
    body: '{\n  "idCaso": "{{idCasoProd}}",\n  "titulo": "QA M3-M4 productiva",\n  "tiempoEstimadoSegundos": 1800,\n  "historia": "Historia QA",\n  "desarrolloSituacional": "Desarrollo QA"\n}',
    status: 201, tests: ['pm.test("estado BORRADOR", () => pm.expect(pm.response.json().estado).to.eql("BORRADOR"));']}),
  request('QA-P-02', 'Consultar caso propio con permisos asignados: F4 vacia lo niega', 'GET', '/v1/casos/{{idCasoProd}}',
    {auth: 'tokenDocente', status: 403, tests: forbidden('FORBIDDEN')}),
  request('QA-P-03', 'Consultar contenido: F4 vacia lo niega', 'GET', '/v1/casos/{{idCasoProd}}/contenido',
    {auth: 'tokenDocente', status: 403, tests: forbidden('FORBIDDEN')}),
  request('QA-P-04', 'Editar con MODIFICAR_CASO sin ETag obtenible (GET negado): rechazado', 'PATCH', '/v1/casos/{{idCasoProd}}', {auth: 'tokenDocente',
    headers: [{key: 'If-Match', value: ETAG_FICTICIO, type: 'text'}], body: '{\n  "titulo": "QA editado"\n}', status: 412, tests: forbidden('STALE_CASE')}),
  request('QA-P-05', 'Agregar escenario sin ETag obtenible: rechazado', 'POST', '/v1/casos/{{idCasoProd}}/escenarios', {auth: 'tokenDocente',
    pre: uuidPre('idEscenarioProd'), headers: [{key: 'If-Match', value: ETAG_FICTICIO, type: 'text'}],
    body: '{\n  "idEscenario": "{{idEscenarioProd}}",\n  "titulo": "Escenario QA",\n  "contexto": "Contexto QA",\n  "instrucciones": "Lea y responda",\n  "orden": 1\n}',
    status: 412, tests: forbidden('STALE_CASE')}),
  request('QA-P-06', 'Publicar con PUBLICAR_CASO sin ETag obtenible: rechazado', 'POST', '/v1/casos/{{idCasoProd}}/publicar', {auth: 'tokenDocente',
    headers: [{key: 'If-Match', value: ETAG_FICTICIO, type: 'text'}], status: 412, tests: forbidden('STALE_CASE')}),
  request('QA-P-07', 'M4 no programa un caso que sigue en Borrador', 'POST', '/m4/sesiones', {auth: 'tokenDocente', body: sesion('idCasoProd'),
    status: 409, tests: forbidden('M4-CASO-NO-PUBLICADO')}),
  request('QA-P-08', 'Personal sin permisos no edita (autorizacion M1)', 'PATCH', '/v1/casos/{{idCasoProd}}', {auth: 'tokenSinPermiso',
    headers: [{key: 'If-Match', value: ETAG_FICTICIO, type: 'text'}], body: '{\n  "titulo": "QA intruso"\n}', status: 403, tests: forbidden('FORBIDDEN')}),
  request('QA-P-09', 'Personal sin permisos no publica (autorizacion M1)', 'POST', '/v1/casos/{{idCasoProd}}/publicar', {auth: 'tokenSinPermiso',
    headers: [{key: 'If-Match', value: ETAG_FICTICIO, type: 'text'}], status: 403, tests: forbidden('FORBIDDEN')}),
  request('QA-P-10', 'Personal sin GESTIONAR_SESION no programa', 'POST', '/m4/sesiones', {auth: 'tokenSinPermiso', body: sesion('idCasoProd'),
    status: 403, tests: forbidden('M4-SIN-PERMISO')}),
  // DC-EJEC-NOCTURNA-01 N1 (F4-H1): permission and ownership are decided before the If-Match precondition.
  request('QA-P-11', 'Administrador asigna PUBLICAR_CASO al personal no propietario', 'PUT',
    '/v1/admin/usuarios/{{sinPermisoId}}/permisos/{{idPermiso_PUBLICAR_CASO}}', {auth: 'tokenAdmin', status: 200,
      tests: ['pm.test("asignacion habilitada", () => pm.expect(pm.response.json().habilitado).to.eql(true));']}),
  request('QA-P-12', 'No propietario con PUBLICAR_CASO y sin If-Match: 403 antes de la precondicion', 'POST',
    '/v1/casos/{{idCasoProd}}/publicar', {auth: 'tokenSinPermiso', status: 403, tests: forbidden('FORBIDDEN')}),
  request('QA-P-13', 'No propietario con PUBLICAR_CASO e If-Match malformado: 403', 'POST', '/v1/casos/{{idCasoProd}}/publicar',
    {auth: 'tokenSinPermiso', headers: [{key: 'If-Match', value: 'W/"x"', type: 'text'}], status: 403, tests: forbidden('FORBIDDEN')}),
  request('QA-P-14', 'Propietario sin If-Match: 428 por su causa real', 'POST', '/v1/casos/{{idCasoProd}}/publicar',
    {auth: 'tokenDocente', status: 428, tests: forbidden('IF_MATCH_REQUIRED')}),
  request('QA-P-15', 'Administrador revoca PUBLICAR_CASO del personal no propietario', 'DELETE',
    '/v1/admin/usuarios/{{sinPermisoId}}/permisos/{{idPermiso_PUBLICAR_CASO}}', {auth: 'tokenAdmin', status: 204}),
];

const m3Reutilizadas = ['M3-B-01', 'M3-B-02', 'M3-B-03', 'M3-B-04', 'M3-B-05', 'M3-B-06', 'M3-B-07', 'M3-B-08', 'M3-B-10', 'M3-B-11',
  'M3-B-12', 'M3-B-13', 'M3-B-14', 'M3-B-15', 'M3-B-16', 'M3-B-17', 'M3-B-18', 'M3-B-19', 'M3-B-20', 'M3-B-21', 'M3-B-22', 'M3-B-23',
  'M3-B-24', 'M3-B-25', 'M3-B-26', 'M3-B-27', 'M3-B-28', 'M3-B-29', 'M3-B-30', 'M3-B-31', 'M3-B-32', 'M3-B-34'].map(reuse);
const qa = [
  ...m3Reutilizadas,
  request('QA-Q-01', 'Consultar el caso publicado por HTTP', 'GET', '/v1/casos/{{idCaso}}', {auth: 'tokenDocente', status: 200,
    tests: ['pm.test("estado PUBLICADO", () => pm.expect(pm.response.json().estado).to.eql("PUBLICADO"));']}),
  request('QA-Q-02', 'Personal sin permisos no consulta el caso publicado', 'GET', '/v1/casos/{{idCaso}}',
    {auth: 'tokenSinPermiso', status: 403, tests: forbidden('FORBIDDEN')}),
  request('QA-Q-03', 'Personal sin GESTIONAR_SESION no programa la sesion', 'POST', '/m4/sesiones', {auth: 'tokenSinPermiso',
    body: sesion('idCaso'), status: 403, tests: forbidden('M4-SIN-PERMISO')}),
  request('QA-Q-04', 'M4 programa la sesion con el caso recien publicado', 'POST', '/m4/sesiones', {auth: 'tokenDocente',
    body: sesion('idCaso'), status: 201, tests: ['const b = pm.response.json();',
      'pm.test("sesion SinIniciar", () => { pm.expect(b.id).to.be.a("string"); pm.expect(b.estado).to.eql("SinIniciar"); });',
      'if (b.id) pm.collectionVariables.set("sesionId", b.id);']}),
  request('QA-Q-05', 'Consultar la sesion: referencia al mismo caso', 'GET', '/m4/sesiones/{{sesionId}}', {auth: 'tokenDocente', status: 200,
    tests: ['const b = pm.response.json();', 'pm.test("misma sesion", () => pm.expect(b.id).to.eql(pm.collectionVariables.get("sesionId")));',
      'pm.test("mismo caso publicado por HTTP", () => pm.expect(b.casoId).to.eql(pm.collectionVariables.get("idCaso")));']}),
  request('QA-Q-06', 'Personal sin permisos no consulta la sesion', 'GET', '/m4/sesiones/{{sesionId}}',
    {auth: 'tokenSinPermiso', status: 403, tests: forbidden('M4-SIN-PERMISO')}),
];

const VARS = ['tokenAdmin', 'tokenDocente', 'tokenSinPermiso', 'tokenOtroDocente', 'idCaso', 'idCaso2', 'idCasoProd', 'idEscenarioProd', 'etag', 'etagAnterior',
  'etag2', 'idEscenario', 'idPregunta', 'idOpcionCorrecta', 'idOpcionIncorrecta', 'sesionId',
  ...PERMISOS_QA.map(c => 'idPermiso_' + c)];
const collection = {
  info: {name: 'PRISALY-M3-M4-QA-F4', schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    description: 'DC-EJEC-CIERRE-01. Campana QA M3 -> M4 con la politica F4 de pruebas (NO ratificada). Folder 00 + P (configuracion '
      + 'productiva) o 00 + Q (politica F4 QA). Permisos asignados por HTTP por el primer administrador; el caso se publica por HTTP; '
      + 'ningun caso Publicado se inserta por SQL. Solo bases desechables. Ejecutar con qa/m3-m4-postman/run.mjs.'},
  variable: [...VARS.map(key => ({key, value: ''})), {key: 'docenteId', value: DOCENTE_ID}, {key: 'sinPermisoId', value: SIN_PERMISO_ID},
    {key: 'fechaInicio', value: '2030-11-05T14:00:00Z'}, {key: 'fechaFin', value: '2030-11-05T15:00:00Z'}],
  item: [{name: '00-preparacion', item: preparacion}, {name: 'P-configuracion-productiva', item: productiva},
    {name: 'Q-configuracion-qa-f4', item: qa}],
};
writeFileSync(OUT, JSON.stringify(collection, null, 2) + '\n');
const count = f => collection.item.find(i => i.name === f).item.length;
console.log(JSON.stringify({salida: 'postman/PRISALY-M3-M4-QA-F4.postman_collection.json', preparacion: count('00-preparacion'),
  productiva: count('P-configuracion-productiva'), qa: count('Q-configuracion-qa-f4')}));
