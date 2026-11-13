// DC-M3-01: runs the M3 Postman collection with Newman against the real APP JAR and a disposable
// PostgreSQL 16.15. Two configurations, each on its own fresh database:
//   productiva: application.yml as delivered (F4 since I1), no case permissions assigned;
//   prueba:     the same application.yml, with case permissions granted by SQL.
// Usage: node qa/m3-postman/run.mjs [--modo productiva|prueba|ambos] [--newman <ruta>] [--sin-construir]
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
const COLLECTION = 'postman/PRISALY-M3-Casos.postman_collection.json';
const SEEDS = ['bruno/scripts/m1-bruno-seed-local.sql', 'bruno/scripts/m7-bruno-seed-local.sql', 'docs/provision/pd01-inicializacion.sql'];
// I1: F4 is delivered in application.yml (CONSULTAR_CASO; BORRADOR,PUBLICADO,ARCHIVADO; requiere-activo=false).
const POLITICA_PRUEBA = [];
const delay = ms => new Promise(r => setTimeout(r, ms));
const sha256 = data => createHash('sha256').update(data).digest('hex');

const args = process.argv.slice(2);
const opt = (name, fallback) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : fallback; };
const modos = {productiva: ['productiva'], prueba: ['prueba'], ambos: ['productiva', 'prueba']}[opt('--modo', 'ambos')];
if (!modos) throw new Error('--modo debe ser productiva, prueba o ambos');
const newmanBin = opt('--newman', 'newman');
const env = cleanEnv();
const secrets = [PUBLIC_PASSWORD];
const run = (cmd, argv, {input, timeout = 60000, shell = false} = {}) => {
  const r = spawnSync(cmd, argv, {cwd: ROOT, env, input, encoding: 'utf8', timeout, maxBuffer: 64 * 1024 * 1024, windowsHide: true, shell});
  return {ok: !r.error && r.status === 0, code: r.status, out: (r.stdout || '') + (r.stderr || ''), stdout: r.stdout || ''};
};
const git = argv => run('git', argv).stdout.trim();

const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const outRoot = join(HERE, 'evidence', stamp);
mkdirSync(outRoot, {recursive: true});
const dirty = git(['status', '--porcelain', '--untracked-files=all']);
const resumen = {inicio: new Date().toISOString(), sha: git(['rev-parse', 'HEAD']), rama: git(['branch', '--show-current']),
  arbolConCambiosSinCommit: dirty.length > 0, cambiosLocales: dirty.split('\n').filter(Boolean),
  parcheSha256: sha256(run('git', ['diff', 'HEAD']).stdout), coleccionSha256: sha256(readFileSync(join(ROOT, COLLECTION))),
  newman: run(newmanBin, ['--version'], {shell: process.platform === 'win32'}).stdout.trim(), corridas: []};

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
writeFileSync(join(outRoot, 'resumen.json'), JSON.stringify(resumen, null, 2) + '\n');
console.log(JSON.stringify({evidencia: relative(ROOT, outRoot), corridas: resumen.corridas.map(c =>
  ({modo: c.modo, estado: c.estado, solicitudes: c.solicitudes, aserciones: c.aserciones, fallidas: c.asercionesFallidas, bloqueadas: c.bloqueadas}))}));
process.exitCode = resumen.corridas.every(c => c.estado === 'aprobada') ? 0 : 1;

async function corrida(modo) {
  const out = join(outRoot, modo); mkdirSync(out, {recursive: true});
  const runId = randomUUID(), name = `prisaly-m3-${runId}`;
  const informe = {modo, runId, politicaLectura: 'application.yml (F4 entregada en I1)', estado: 'en curso', limpieza: []};
  let containerId = null, app = null, appLog = [];
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
    const port = await new Promise((res, rej) => { const s = net.createServer(); s.on('error', rej); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => res(p)); }); });
    const jdbc = `jdbc:postgresql://127.0.0.1:${port5432.HostPort}/qa_m3?sslmode=disable`;
    app = spawn('java', ['-jar', jar, '--server.address=127.0.0.1', `--server.port=${port}`, '--server.servlet.context-path=/api',
      '--spring.jpa.hibernate.ddl-auto=validate', '--spring.flyway.enabled=true', '--prisaly.m1.student-access.enabled=false',
      '--prisaly.m7.worker.enabled=false', ...(modo === 'prueba' ? POLITICA_PRUEBA : [])],
      {cwd: ROOT, env: {...env, DATABASE_URL: jdbc, DATABASE_USER: 'qa_m3', DATABASE_PASSWORD: dbPassword}, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true});
    for (const s of [app.stdout, app.stderr]) s.on('data', b => appLog.push(b));
    app.on('error', () => {});
    const base = `http://127.0.0.1:${port}/api`;
    let up = false;
    for (let i = 0; i < 180 && !up; i++) {
      if (app.exitCode !== null) break;
      try { const r = await fetch(base + '/health', {signal: AbortSignal.timeout(1000)}); up = r.status === 200 && Buffer.concat(appLog).toString().includes('Started PrisalyApplication'); } catch {}
      if (!up) await delay(500);
    }
    if (!up) throw new Error('APP no arrancó; ver app.log');
    informe.postgres = sql('SHOW server_version;');
    informe.semillas = [];
    const semillas = [...SEEDS, ...(modo === 'prueba' ? ['qa/m3-postman/fixture-permisos-casos.sql'] : []), 'qa/backend-http/fixture.sql'];
    for (const file of semillas) { const t = readFileSync(join(ROOT, file), 'utf8'); sql(t); informe.semillas.push({file, sha256: sha256(t)}); }
    informe.permisosDocente = sql(`SELECT string_agg(p."codigo", ',' ORDER BY p."codigo") FROM "UsuarioPermiso" up
      JOIN "Usuario" u ON u."idUsuario" = up."idUsuario" JOIN "Permiso" p ON p."idPermiso" = up."idPermiso"
      WHERE u."correo" = 'docente.bruno@prisaly.local' AND up."habilitado";`);

    const envFile = join(out, `env-${runId}.json`), rawFile = join(out, `raw-${runId}.json`);
    writeFileSync(envFile, JSON.stringify({values: Object.entries({baseUrl: base,
      docenteEmail: 'docente.bruno@prisaly.local', docentePassword: PUBLIC_PASSWORD,
      sinPermisoEmail: 'sinperm.bruno@prisaly.local', sinPermisoPassword: PUBLIC_PASSWORD,
      otroDocenteEmail: 'other.qa@prisaly.invalid', otroDocentePassword: PUBLIC_PASSWORD}).map(([key, value]) => ({key, value, enabled: true}))}));
    try {
      const carpeta = modo === 'prueba' ? 'B-configuracion-prueba' : 'A-configuracion-productiva';
      // Newman resolves to newman.cmd on Windows: a shell is required (P2-5 of DC-AUD-01).
      const result = run(newmanBin, ['run', COLLECTION, '--folder', '00-preparacion', '--folder', carpeta,
        '--environment', envFile, '--reporters', 'cli,json', '--reporter-json-export', rawFile, '--color', 'off', '--disable-unicode'],
        {timeout: 300000, shell: process.platform === 'win32'});
      writeFileSync(join(out, 'newman-cli.log'), redact(result.out, secrets));
      const raw = existsSync(rawFile) ? JSON.parse(readFileSync(rawFile, 'utf8')) : null;
      for (const ex of raw?.run?.executions || []) for (const tok of [ex.response?.stream && Buffer.from(ex.response.stream.data || []).toString()]) {
        const m = /"accessToken"\s*:\s*"([^"]+)"/.exec(tok || ''); if (m) secrets.push(m[1]);
      }
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

    // PostgreSQL evidence: what the run actually persisted for the cases it created.
    informe.postgresEvidencia = JSON.parse(sql(`SELECT coalesce(jsonb_agg(x ORDER BY x->>'nombre'), '[]'::jsonb) FROM (
      SELECT jsonb_build_object('idCaso', c."idCaso", 'nombre', c."nombre", 'estado', c."estado", 'activo', c."activo",
        'historia', c."historia", 'escenarios', (SELECT count(*) FROM "Escenario" e WHERE e."idCaso" = c."idCaso"),
        'preguntas', (SELECT count(*) FROM "Pregunta" p JOIN "Escenario" e ON e."idEscenario" = p."idEscenario" WHERE e."idCaso" = c."idCaso"),
        'opcionesConExplicacion', (SELECT count(*) FROM "OpcionRespuesta" o JOIN "Pregunta" p ON p."idPregunta" = o."idPregunta"
          JOIN "Escenario" e ON e."idEscenario" = p."idEscenario" WHERE e."idCaso" = c."idCaso" AND coalesce(o."explicacion", '') <> ''),
        'rubricas', (SELECT count(*) FROM "Rubrica" r WHERE r."idCaso" = c."idCaso"),
        'asociacionesRubrica', (SELECT count(*) FROM "RubricaPregunta" rp JOIN "Rubrica" r ON r."idRubrica" = rp."idRubrica" WHERE r."idCaso" = c."idCaso"))
      AS x FROM "Caso" c WHERE c."nombre" LIKE 'Postman M3%') t;`));
  } catch (error) {
    informe.estado = 'bloqueada'; informe.problema = redact(String(error.message), secrets);
  } finally {
    if (app) { app.kill('SIGTERM'); for (let i = 0; i < 50 && app.exitCode === null; i++) await delay(100); if (app.exitCode === null) app.kill('SIGKILL'); }
    writeFileSync(join(out, 'app.log'), redact(Buffer.concat(appLog).toString('utf8'), secrets));
    if (containerId) {
      let removed = false;
      try { const info = JSON.parse(run('docker', ['inspect', containerId]).stdout)[0]; if (ownsContainer(info, containerId, name, runId)) removed = run('docker', ['rm', '--force', containerId]).ok; } catch {}
      informe.limpieza.push({recurso: 'postgres-propio', ok: removed});
    }
    informe.fin = new Date().toISOString();
    writeFileSync(join(out, 'informe.json'), JSON.stringify(redact(informe, secrets), null, 2) + '\n');
  }
  return informe;
}
