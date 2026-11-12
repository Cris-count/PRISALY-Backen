// DC-EJEC-CIERRE-01: M3 -> M4 acceptance campaign with Newman against the real APP JAR and a disposable PostgreSQL 16.15.
// Each mode runs on its own fresh database and APP:
//   productiva: application.yml as shipped (F4 read policy blank). The teacher receives the case permissions anyway,
//               and every read, edit and publication is still denied; M4 refuses the unpublished case.
//   qa:         F4 policy of the integration tests (NOT ratified, D-B) passed ONLY as command-line arguments.
// In both modes:
//   - seeds are the public Bruno M1 fixture (teacher and no-permission user) and PD-01 (catalog). No "Caso" row is
//     inserted by SQL: the runner fails if any case exists before Newman starts;
//   - the first administrator is created by the real P5 initialization (random credential, file deleted at startup) and
//     grants CONSULTAR_CASO, MODIFICAR_CASO, PUBLICAR_CASO and GESTIONAR_SESION over HTTP (PUT /v1/admin/...);
//   - DD-02 student access stays disabled. In qa mode only (DC-CIERRE-MODULAR-01) the M7 convocation worker runs
//     against a disposable Mailpit capture mailbox (loopback, own label, removed at the end), never a real SMTP;
//   - qa mode also seeds one student (qa/m3-m4-postman/fixture-estudiante-qa.sql): there is no HTTP route to register
//     students, and that student is convoked over HTTP. Still no case, session or convocation is inserted by SQL.
// Usage: node qa/m3-m4-postman/run.mjs [--modo productiva|qa|ambos] [--newman <ruta>] [--sin-construir] [--salida <dir>]
import {spawnSync, spawn} from 'node:child_process';
import {createHash, randomUUID, randomBytes} from 'node:crypto';
import {mkdirSync, readFileSync, writeFileSync, existsSync, readdirSync, unlinkSync} from 'node:fs';
import {dirname, resolve, join, relative} from 'node:path';
import {fileURLToPath} from 'node:url';
import net from 'node:net';
import {redact, cleanEnv, ownsContainer} from '../backend-http/run.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../..');
const PUBLIC_PASSWORD = 'LocalBruno!234'; // Public Bruno fixture already in the repository; disposable DB only.
const COLLECTION = 'postman/PRISALY-M3-M4-QA-F4.postman_collection.json';
const SEEDS = ['bruno/scripts/m1-bruno-seed-local.sql', 'docs/provision/pd01-inicializacion.sql'];
const SEED_ESTUDIANTE = 'qa/m3-m4-postman/fixture-estudiante-qa.sql';
const MAILPIT = 'axllent/mailpit:v1.31.2';
const REMITENTE_QA = 'no-reply@prisaly.test';
const ADMIN = {correo: 'admin.qa@prisaly.invalid', nombre: 'Administrador inicial QA'};
// Exactly the values of qa/m3-postman/run.mjs (Newman M3 151/151) and of the M3 integration tests. NOT ratified.
const POLITICA_QA = ['--prisaly.m3.lectura.permiso=CONSULTAR_CASO',
  '--prisaly.m3.lectura.estados-visibles=BORRADOR,PUBLICADO,ARCHIVADO', '--prisaly.m3.lectura.requiere-activo=true'];
const CARPETA = {productiva: 'P-configuracion-productiva', qa: 'Q-configuracion-qa-f4'};
const delay = ms => new Promise(r => setTimeout(r, ms));
const sha256 = data => createHash('sha256').update(data).digest('hex');

const args = process.argv.slice(2);
const opt = (name, fallback) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : fallback; };
const modos = {productiva: ['productiva'], qa: ['qa'], ambos: ['productiva', 'qa']}[opt('--modo', 'ambos')];
if (!modos) throw new Error('--modo debe ser productiva, qa o ambos');
const newmanBin = opt('--newman', 'newman');
const env = cleanEnv();
const secrets = [PUBLIC_PASSWORD];
const run = (cmd, argv, {input, timeout = 60000, shell = false} = {}) => {
  const r = spawnSync(cmd, argv, {cwd: ROOT, env, input, encoding: 'utf8', timeout, maxBuffer: 64 * 1024 * 1024, windowsHide: true, shell});
  return {ok: !r.error && r.status === 0, code: r.status, out: (r.stdout || '') + (r.stderr || ''), stdout: r.stdout || ''};
};
const git = argv => run('git', argv).stdout.trim();

const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const outRoot = resolve(opt('--salida', join(HERE, 'evidence', stamp)));
mkdirSync(outRoot, {recursive: true});
const dirty = git(['status', '--porcelain', '--untracked-files=all']);
const resumen = {inicio: new Date().toISOString(), sha: git(['rev-parse', 'HEAD']), arbolConCambiosSinCommit: dirty.length > 0,
  applicationYmlSha256: sha256(readFileSync(join(ROOT, 'app/src/main/resources/application.yml'))),
  coleccionSha256: sha256(readFileSync(join(ROOT, COLLECTION))),
  newman: run(newmanBin, ['--version'], {shell: process.platform === 'win32'}).stdout.trim(), corridas: []};
for (const file of [...SEEDS, SEED_ESTUDIANTE]) {
  if (/INSERT\s+INTO\s+"(Sesion|Convocatoria)"/i.test(readFileSync(join(ROOT, file), 'utf8'))) throw new Error(`${file} inserta sesiones o convocatorias`);
  if (/INSERT\s+INTO\s+"Caso"/i.test(readFileSync(join(ROOT, file), 'utf8'))) throw new Error(`${file} inserta casos: no admitido`);
}

if (!args.includes('--sin-construir')) {
  const build = process.platform === 'win32'
    ? run(env.ComSpec || env.COMSPEC || 'cmd.exe', ['/d', '/c', 'mvnw.cmd -B -q -pl app -am package -DskipTests'], {timeout: 900000})
    : run('sh', ['./mvnw', '-B', '-q', '-pl', 'app', '-am', 'package', '-DskipTests'], {timeout: 900000});
  writeFileSync(join(outRoot, 'build.log'), redact(build.out, secrets));
  if (!build.ok) { console.error('No se pudo construir APP; ver build.log'); process.exit(2); }
}
const jars = readdirSync(join(ROOT, 'app/target')).filter(f => /^app-.+\.jar$/.test(f));
if (jars.length !== 1) throw new Error('No se identifica un único JAR de APP');
const jar = join(ROOT, 'app/target', jars[0]);
resumen.jarSha256 = sha256(readFileSync(jar));

for (const modo of modos) resumen.corridas.push(await corrida(modo));
resumen.fin = new Date().toISOString();
writeFileSync(join(outRoot, 'resumen.json'), JSON.stringify(redact(resumen, secrets), null, 2) + '\n');
console.log(JSON.stringify({evidencia: relative(ROOT, outRoot), corridas: resumen.corridas.map(c =>
  ({modo: c.modo, estado: c.estado, solicitudes: c.solicitudes, aserciones: c.aserciones, fallidas: c.asercionesFallidas,
    bloqueadas: c.bloqueadas, problema: c.problema}))}));
process.exitCode = resumen.corridas.every(c => c.estado === 'aprobada') ? 0 : 1;

async function corrida(modo) {
  const out = join(outRoot, modo); mkdirSync(out, {recursive: true});
  const runId = randomUUID(), name = `prisaly-m3m4-${runId}`;
  const informe = {modo, runId, politicaLectura: modo === 'qa' ? POLITICA_QA : 'application.yml (vacía)', estado: 'en curso', limpieza: []};
  let containerId = null, app = null, mail = null; const appLog = [];
  const flujoM7 = modo === 'qa';
  try {
    const dbPassword = randomBytes(24).toString('hex'); secrets.push(dbPassword);
    const started = spawnSync('docker', ['run', '--detach', '--rm', '--name', name, '--label', `prisaly.qa.run=${runId}`,
      '--publish', '127.0.0.1::5432', '--tmpfs', '/var/lib/postgresql/data:rw', '-e', 'POSTGRES_PASSWORD',
      '-e', 'POSTGRES_USER=qa_m3', '-e', 'POSTGRES_DB=qa_m3', 'postgres:16.15'],
      {env: {...env, POSTGRES_PASSWORD: dbPassword}, encoding: 'utf8', timeout: 180000, windowsHide: true});
    containerId = (started.stdout || '').trim();
    if (started.status !== 0 || !/^[a-f0-9]{64}$/.test(containerId)) throw new Error('No se pudo iniciar PostgreSQL efímero');
    const info = JSON.parse(run('docker', ['inspect', containerId]).stdout)[0];
    if (!ownsContainer(info, containerId, name, runId)) throw new Error('No se acredita la propiedad del contenedor');
    const port5432 = info.NetworkSettings.Ports['5432/tcp'][0];
    if (port5432.HostIp !== '127.0.0.1') throw new Error('PostgreSQL debe publicarse solo en loopback');
    for (let i = 0; i < 90 && !run('docker', ['exec', containerId, 'pg_isready', '-U', 'qa_m3', '-d', 'qa_m3']).ok; i++) await delay(1000);
    const sql = text => {
      const r = run('docker', ['exec', '-i', containerId, 'psql', '-X', '-q', '-t', '-A', '-U', 'qa_m3', '-d', 'qa_m3', '-v', 'ON_ERROR_STOP=1'], {input: text});
      if (!r.ok) throw new Error('SQL falló: ' + redact(r.out, secrets).slice(0, 400));
      return r.stdout.trim();
    };
    if (flujoM7) {
      const mailName = `prisaly-m3m4-mailpit-${runId}`;
      const st = spawnSync('docker', ['run', '--detach', '--rm', '--name', mailName, '--label', `prisaly.qa.run=${runId}`,
        '--publish', '127.0.0.1::1025', '--publish', '127.0.0.1::8025', MAILPIT], {env, encoding: 'utf8', timeout: 180000, windowsHide: true});
      const id = (st.stdout || '').trim();
      if (st.status !== 0 || !/^[a-f0-9]{64}$/.test(id)) throw new Error('No se pudo iniciar Mailpit efímero');
      const mi = JSON.parse(run('docker', ['inspect', id]).stdout)[0];
      if (!ownsContainer(mi, id, mailName, runId)) throw new Error('No se acredita la propiedad del contenedor Mailpit');
      mail = {id, name: mailName, smtp: mi.NetworkSettings.Ports['1025/tcp'][0].HostPort, api: mi.NetworkSettings.Ports['8025/tcp'][0].HostPort};
    }
    const trabajadorM7 = () => flujoM7 ? ['--prisaly.m7.worker.enabled=true', `--prisaly.m7.correo.remitente=${REMITENTE_QA}`,
      '--prisaly.m7.worker.lote=10', '--prisaly.m7.worker.lease-segundos=60', '--prisaly.m7.worker.intervalo-ms=1000',
      '--spring.mail.host=127.0.0.1', `--spring.mail.port=${mail.smtp}`] : ['--prisaly.m7.worker.enabled=false'];
    const port = await new Promise((res, rej) => { const s = net.createServer(); s.on('error', rej); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => res(p)); }); });
    const jdbc = `jdbc:postgresql://127.0.0.1:${port5432.HostPort}/qa_m3?sslmode=disable`;
    const base = `http://127.0.0.1:${port}/api`;
    const arrancar = async extra => {
      const desde = appLog.length;
      app = spawn('java', ['-jar', jar, '--server.address=127.0.0.1', `--server.port=${port}`, '--server.servlet.context-path=/api',
        '--spring.jpa.hibernate.ddl-auto=validate', '--spring.flyway.enabled=true', '--prisaly.m1.student-access.enabled=false',
        ...trabajadorM7(), ...(modo === 'qa' ? POLITICA_QA : []), ...extra],
        {cwd: ROOT, env: {...env, DATABASE_URL: jdbc, DATABASE_USER: 'qa_m3', DATABASE_PASSWORD: dbPassword}, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true});
      for (const s of [app.stdout, app.stderr]) s.on('data', b => appLog.push(b));
      app.on('error', () => {});
      let up = false;
      for (let i = 0; i < 180 && !up; i++) {
        if (app.exitCode !== null) break;
        try { const r = await fetch(base + '/health', {signal: AbortSignal.timeout(1000)}); up = r.status === 200 && Buffer.concat(appLog.slice(desde)).toString().includes('Started PrisalyApplication'); } catch {}
        if (!up) await delay(500);
      }
      if (!up) throw new Error('APP no arrancó; ver app.log');
      return Buffer.concat(appLog.slice(desde)).toString('utf8');
    };
    const detener = async () => {
      if (!app) return;
      app.kill('SIGTERM'); for (let i = 0; i < 100 && app.exitCode === null; i++) await delay(100);
      if (app.exitCode === null) app.kill('SIGKILL');
      app = null;
    };
    await arrancar([]);
    informe.postgres = sql('SHOW server_version;');
    informe.flyway = sql(`SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success;`);
    informe.tablasDd02b = Number(sql(`SELECT count(*) FROM information_schema.tables WHERE table_schema='public'
      AND table_name IN ('CredencialAccesoEstudiante','IntentoAccesoEstudiante','EntregaCodigoAcceso');`));
    informe.semillas = [];
    for (const file of flujoM7 ? [...SEEDS, SEED_ESTUDIANTE] : SEEDS) { const t = readFileSync(join(ROOT, file), 'utf8'); sql(t); informe.semillas.push({file, sha256: sha256(t)}); }

    // P5-N1 operating procedure: one-shot initialization with a random credential read from a file deleted at startup.
    const adminPassword = 'P5#' + randomBytes(18).toString('base64url'); secrets.push(adminPassword);
    const credencial = join(out, `credencial-${runId}.txt`);
    writeFileSync(credencial, adminPassword + '\n', {mode: 0o600});
    await detener();
    let log;
    try {
      log = await arrancar(['--prisaly.m1.inicializacion-admin.habilitada=true', `--prisaly.m1.inicializacion-admin.correo=${ADMIN.correo}`,
        `--prisaly.m1.inicializacion-admin.nombre=${ADMIN.nombre}`, `--prisaly.m1.inicializacion-admin.archivo-credencial=${credencial}`]);
      for (let i = 0; i < 120 && !/SEGURIDAD_ADMINISTRACION evento=INICIALIZACION/.test(log); i++) {
        await delay(500); log = Buffer.concat(appLog).toString('utf8');
      }
    } finally { if (existsSync(credencial)) unlinkSync(credencial); }
    informe.inicializacion = {evento: (log.match(/SEGURIDAD_ADMINISTRACION evento=INICIALIZACION[^\r\n]*/) || [null])[0],
      credencialEnLog: log.includes(adminPassword), archivoCredencialBorrado: !existsSync(credencial)};
    if (!/resultado=EXITOSO/.test(informe.inicializacion.evento || '') || informe.inicializacion.credencialEnLog) {
      throw new Error('La inicialización del primer administrador no se verificó');
    }
    informe.antesDeNewman = JSON.parse(sql(`SELECT jsonb_build_object('casos', (SELECT count(*) FROM "Caso"),
      'sesiones', (SELECT count(*) FROM "Sesion"),
      'permisosDocente', (SELECT coalesce(jsonb_agg(p."codigo" ORDER BY p."codigo"), '[]'::jsonb) FROM "UsuarioPermiso" up
        JOIN "Usuario" u ON u."idUsuario" = up."idUsuario" JOIN "Permiso" p ON p."idPermiso" = up."idPermiso"
        WHERE u."correo" = 'docente.bruno@prisaly.local' AND up."habilitado"));`));
    if (informe.antesDeNewman.casos !== 0) throw new Error('Existen casos antes de Newman: la campaña exige publicarlos por HTTP');

    const envFile = join(out, `env-${runId}.json`), rawFile = join(out, `raw-${runId}.json`);
    writeFileSync(envFile, JSON.stringify({values: Object.entries({baseUrl: base, adminEmail: ADMIN.correo, adminPassword,
      docenteEmail: 'docente.bruno@prisaly.local', docentePassword: PUBLIC_PASSWORD,
      sinPermisoEmail: 'sinperm.bruno@prisaly.local', sinPermisoPassword: PUBLIC_PASSWORD}).map(([key, value]) => ({key, value, enabled: true}))}));
    try {
      const result = run(newmanBin, ['run', COLLECTION, '--folder', '00-preparacion', '--folder', CARPETA[modo],
        '--environment', envFile, '--reporters', 'cli,json', '--reporter-json-export', rawFile, '--color', 'off', '--disable-unicode'],
        {timeout: 300000, shell: process.platform === 'win32'});
      const raw = existsSync(rawFile) ? JSON.parse(readFileSync(rawFile, 'utf8')) : null;
      for (const ex of raw?.run?.executions || []) {
        const m = /"accessToken"\s*:\s*"([^"]+)"/.exec(Buffer.from(ex.response?.stream?.data || []).toString()); if (m) secrets.push(m[1]);
      }
      writeFileSync(join(out, 'newman-cli.log'), redact(result.out, secrets));
      const ejecuciones = (raw?.run?.executions || []).map(e => ({
        solicitud: e.item?.name, metodo: e.request?.method, url: redact(String(e.request?.url?.path?.join('/') ?? ''), secrets),
        http: e.response?.code ?? null, bloqueada: !e.response,
        cuerpo: redact((() => { try { return JSON.parse(Buffer.from(e.response?.stream?.data || []).toString() || 'null'); } catch { return null; } })(), secrets),
        aserciones: (e.assertions || []).map(a => ({nombre: a.assertion, fallida: !!a.error, mensaje: a.error?.message ?? null}))}));
      const aserciones = ejecuciones.flatMap(e => e.aserciones);
      Object.assign(informe, {newmanExit: result.code, solicitudes: ejecuciones.length, bloqueadas: ejecuciones.filter(e => e.bloqueada).length,
        aserciones: aserciones.length, asercionesFallidas: aserciones.filter(a => a.fallida).length, ejecuciones});
      informe.estado = result.ok && ejecuciones.length > 0 && informe.bloqueadas === 0 && informe.asercionesFallidas === 0 ? 'aprobada' : 'fallida';
    } finally { for (const f of [envFile, rawFile]) if (existsSync(f)) unlinkSync(f); }

    // DC-CIERRE-MODULAR-01, qa mode: convocation -> Aviso (Observer) -> EnvioCorreo (Outbox) -> worker -> Mailpit.
    if (flujoM7) {
      const consulta = () => JSON.parse(sql(`SELECT jsonb_build_object(
        'convocatorias', (SELECT count(*) FROM "Convocatoria"),
        'avisos', (SELECT count(*) FROM "Aviso" a WHERE a."idUsuarioDestino" = '11111111-1111-1111-1111-111111111301'),
        'envios', (SELECT coalesce(jsonb_agg(jsonb_build_object('estado', e."estadoEnvio", 'intentos', e."numeroIntentos",
          'enviado', e."fechaEnvio" IS NOT NULL, 'error', e."ultimoError")), '[]'::jsonb)
          FROM "EnvioCorreo" e JOIN "Convocatoria" c ON c."idConvocatoria" = e."idConvocatoria"));`));
      let m7 = consulta();
      for (let i = 0; i < 60 && !(m7.envios.length && m7.envios.every(e => e.estado !== 'Pendiente')); i++) { await delay(1000); m7 = consulta(); }
      const buzon = await (await fetch(`http://127.0.0.1:${mail.api}/api/v1/messages`)).json();
      const mensajes = (buzon.messages || []).map(m => ({para: (m.To || []).map(t => t.Address), de: m.From?.Address, asunto: m.Subject}));
      informe.flujoM4M7 = {...m7, buzonQa: {total: buzon.messages_count ?? mensajes.length, mensajes}};
      const entregado = m7.convocatorias === 1 && m7.avisos >= 1 && m7.envios.length === 1 && m7.envios[0].estado === 'Enviado'
        && mensajes.length === 1 && mensajes[0].para.includes('estudiante.qa@prisaly.invalid') && mensajes[0].de === REMITENTE_QA;
      informe.flujoM4M7.verificado = entregado;
      if (!entregado && informe.estado === 'aprobada') { informe.estado = 'fallida'; informe.problema = 'Flujo M4 -> M7 no verificado'; }
    }

    // PostgreSQL evidence: what the run persisted, and who granted the permissions.
    informe.postgresEvidencia = JSON.parse(sql(`SELECT jsonb_build_object(
      'casos', (SELECT coalesce(jsonb_agg(jsonb_build_object('idCaso', c."idCaso", 'nombre', c."nombre", 'estado', c."estado", 'activo', c."activo",
        'escenarios', (SELECT count(*) FROM "Escenario" e WHERE e."idCaso" = c."idCaso"),
        'preguntas', (SELECT count(*) FROM "Pregunta" p JOIN "Escenario" e ON e."idEscenario" = p."idEscenario" WHERE e."idCaso" = c."idCaso"),
        'opciones', (SELECT count(*) FROM "OpcionRespuesta" o JOIN "Pregunta" p ON p."idPregunta" = o."idPregunta"
          JOIN "Escenario" e ON e."idEscenario" = p."idEscenario" WHERE e."idCaso" = c."idCaso"),
        'rubricas', (SELECT count(*) FROM "Rubrica" r WHERE r."idCaso" = c."idCaso")) ORDER BY c."nombre"), '[]'::jsonb) FROM "Caso" c),
      'sesiones', (SELECT coalesce(jsonb_agg(jsonb_build_object('idSesion', s."idSesion", 'idCaso', s."idCaso", 'estado', s."estado",
        'casoEstado', c."estado")), '[]'::jsonb) FROM "Sesion" s JOIN "Caso" c ON c."idCaso" = s."idCaso"),
      'permisosAsignadosPorAdministrador', (SELECT coalesce(jsonb_agg(p."codigo" ORDER BY p."codigo"), '[]'::jsonb) FROM "UsuarioPermiso" up
        JOIN "Permiso" p ON p."idPermiso" = up."idPermiso" JOIN "Usuario" u ON u."idUsuario" = up."idUsuario"
        WHERE u."correo" = 'docente.bruno@prisaly.local' AND up."idAdministradorAsignador" =
          (SELECT "idUsuarioAdministrador" FROM "InicializacionAdministrador")),
      'permisosSinPermiso', (SELECT count(*) FROM "UsuarioPermiso" up JOIN "Usuario" u ON u."idUsuario" = up."idUsuario"
        WHERE u."correo" = 'sinperm.bruno@prisaly.local' AND up."habilitado"));`));
  } catch (error) {
    informe.estado = 'bloqueada'; informe.problema = redact(String(error.message), secrets);
  } finally {
    if (app) { app.kill('SIGTERM'); for (let i = 0; i < 50 && app.exitCode === null; i++) await delay(100); if (app.exitCode === null) app.kill('SIGKILL'); }
    const appText = Buffer.concat(appLog).toString('utf8');
    informe.filtracionesEnLogApp = secrets.filter(s => s !== PUBLIC_PASSWORD && appText.includes(s)).length;
    writeFileSync(join(out, 'app.log'), redact(appText, secrets));
    if (containerId) {
      let removed = false;
      try { const info = JSON.parse(run('docker', ['inspect', containerId]).stdout)[0]; if (ownsContainer(info, containerId, name, runId)) removed = run('docker', ['rm', '--force', containerId]).ok; } catch {}
      informe.limpieza.push({recurso: 'postgres-propio', ok: removed});
    }
    if (mail) {
      let removed = false;
      try { const mi = JSON.parse(run('docker', ['inspect', mail.id]).stdout)[0]; if (ownsContainer(mi, mail.id, mail.name, runId)) removed = run('docker', ['rm', '--force', mail.id]).ok; } catch {}
      informe.limpieza.push({recurso: 'mailpit-propio', ok: removed});
    }
    informe.fin = new Date().toISOString();
    writeFileSync(join(out, 'informe.json'), JSON.stringify(redact(informe, secrets), null, 2) + '\n');
  }
  return informe;
}
