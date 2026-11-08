import {spawnSync, spawn} from 'node:child_process';
import {createHash, randomUUID, randomBytes} from 'node:crypto';
import {mkdirSync, readFileSync, writeFileSync, existsSync, readdirSync} from 'node:fs';
import {dirname, resolve, relative, join} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import net from 'node:net';

export const BASE_SHA = 'bc8e378bd6dd50a3b1484b06c268932ba5607dd3';
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../..');
const PUBLIC_PASSWORD = 'LocalBruno!234'; // Existing public Bruno fixture; ephemeral DB only.
const OWNER = '11111111-1111-1111-1111-111111111101';
const OTHER = '11111111-1111-1111-1111-111111111104';
const STUDENT = '22222222-2222-2222-2222-222222222201';
const PUBLISHED = '33333333-3333-3333-3333-333333333301';
const DRAFT = '33333333-3333-3333-3333-333333333302';
const INACTIVE = '33333333-3333-3333-3333-333333333303';
const ABSENT = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const delay = ms => new Promise(r => setTimeout(r, ms));
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const json = (path, data) => writeFileSync(path, JSON.stringify(data, null, 2) + '\n');

export function redact(value, secrets = []) {
  if (Array.isArray(value)) return value.map(v => redact(v, secrets));
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k,v]) =>
    [k, /password|token|authorization|cookie|secret|codigo|challenge/i.test(k) ? '[REDACTED]' : redact(v, secrets)]));
  if (typeof value !== 'string') return value;
  let out = value;
  for (const secret of [PUBLIC_PASSWORD, ...secrets].filter(Boolean).sort((a,b) => b.length-a.length)) out = out.split(secret).join('[REDACTED]');
  return out.replace(/Bearer\s+[^\s"',}]+/gi, 'Bearer [REDACTED]')
    .replace(/\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}/g, '[BCRYPT REDACTED]')
    .replace(/jdbc:postgresql:\/\/[^\s"']+/g, '[JDBC REDACTED]')
    .replace(/((?:password|accessToken|authorization|secret)\s*[=:]\s*)\S+/gi, '$1[REDACTED]');
}

// Explicit environment prevents inherited SPRING_*, DATABASE_*, JAVA_TOOL_OPTIONS and proxies.
export function cleanEnv(source = process.env) {
  const allowed = /^(path|systemroot|windir|comspec|pathext|temp|tmp|home|userprofile|localappdata|appdata|programfiles|programfiles\(x86\)|java_home|lang|lc_all|docker_host|docker_context|docker_config)$/i;
  return Object.fromEntries(Object.entries(source).filter(([key]) => allowed.test(key)));
}

export function ownsContainer(info, id, name, runId) {
  return /^[a-f0-9]{64}$/.test(id) && info.Id === id && info.Name === `/${name}`
    && info.Config?.Labels?.['prisaly.qa.run'] === runId;
}

export const SCENARIOS = [
  ['HTTP-01','GET','/health',200,null,'Servidor real disponible'],
  ['HTTP-02','GET','/v1/personal/me',401,'UNAUTHENTICATED','Sin credencial'],
  ['HTTP-03','POST','/v1/personal/autenticar',401,'AUTH_FAILED','Usuario desconocido'],
  ['HTTP-04','POST','/v1/personal/autenticar',401,'AUTH_FAILED','Password incorrecto'],
  ['HTTP-05','POST','/v1/personal/autenticar',401,'AUTH_FAILED','Cuenta inactiva'],
  ['HTTP-06','POST','/v1/personal/autenticar',200,null,'Personal docente fixture activo'],
  ['HTTP-07','GET','/v1/personal/me',200,null,'Bearer obtenido por login HTTP'],
  ['HTTP-08','GET','/v1/personal/me',401,'UNAUTHENTICATED','Bearer inválido'],
  ['HTTP-09','POST','/v1/personal/autenticar',200,null,'Personal sin permisos fixture'],
  ['HTTP-10','POST','/v1/personal/autenticar',200,null,'Otro docente fixture'],
  ['HTTP-11','POST','/v1/casos',401,'UNAUTHENTICATED','Sin credencial'],
  ['HTTP-12','POST','/v1/casos',403,'FORBIDDEN','Personal sin CREAR_CASO'],
  ['HTTP-13','POST','/v1/casos',201,null,'Docente con CREAR_CASO; borrador mínimo'],
  ['HTTP-14','POST','/v1/casos',400,'INVALID_CASE_REQUEST','Payload con propietario inyectado'],
  ['HTTP-15','GET','/m4/sesiones',401,'UNAUTHENTICATED','Sin credencial'],
  ['HTTP-16','POST','/m4/sesiones',403,'M4-SIN-PERMISO','Personal sin GESTIONAR_SESION'],
  ['HTTP-17','POST','/m4/sesiones',409,'M4-CASO-NO-PUBLICADO','Caso Borrador fixture'],
  ['HTTP-18','POST','/m4/sesiones',409,'M4-CASO-NO-PUBLICADO','Caso Publicado inactivo fixture'],
  ['HTTP-19','POST','/m4/sesiones',409,'M4-CASO-NO-PUBLICADO','Referencia académica inexistente'],
  ['HTTP-20','POST','/m4/sesiones',201,null,'Caso Publicado activo fixture; no prueba publicación HTTP'],
  ['HTTP-21','GET','/m4/sesiones/{sesionId}',200,null,'Docente propietario'],
  ['HTTP-22','GET','/m4/sesiones/{sesionId}',403,'M4-SESION-AJENA','Otro docente con permiso'],
  ['HTTP-23','GET','/m4/sesiones/{ausente}',404,'M4-SESION-NO-ENCONTRADA','Docente autorizado; ID inexistente'],
  ['HTTP-24','POST','/m4/sesiones/{sesionId}/convocatorias',201,null,'Estudiante fixture existente'],
  ['HTTP-25','GET','/m4/sesiones/{sesionId}/convocatorias',200,null,'Docente propietario'],
  ['HTTP-26','POST','/m4/sesiones/{sesionId}/convocatorias',409,'M4-CONVOCATORIA-DUPLICADA','Repetición de convocatoria'],
  ['HTTP-27','POST','/v1/acceso-estudiante/validar',501,'ESTUDIANTE_ACCESS_PENDING_CONTRACT','DD-02 deshabilitada'],
  ['HTTP-28','GET','/v1/estudiantes/intentos/{ausente}',401,'UNAUTHENTICATED','Sin credencial'],
  ['HTTP-29','GET','/v1/estudiantes/intentos/{ausente}',403,'STUDENT_IDENTITY_UNAVAILABLE','Bearer personal no suplanta estudiante'],
  ['HTTP-30','PUT','/v1/estudiantes/intentos/{ausente}/respuestas/{pregunta}',403,'STUDENT_IDENTITY_UNAVAILABLE','Bearer personal; sin escritura'],
  ['HTTP-31','POST','/v1/personal/cerrar-sesion',204,null,'Revocación del Bearer propio'],
  ['HTTP-32','GET','/v1/personal/me',401,'UNAUTHENTICATED','Bearer revocado']
].map(([id,method,path,expectedStatus,expectedCode,precondition]) =>
  ({id,method,path,expectedStatus,expectedCode,precondition,module:path.startsWith('/m4')?'M4':path.startsWith('/v1/casos')?'M3':path.startsWith('/v1/estudiantes')?'M5':'M1/APP',rf:null,cu:null,mandatory:true}));

class Blocked extends Error {}
export async function main(args = process.argv.slice(2)) {
  const opts = {sha:BASE_SHA, preflight:false, buildOnly:false};
  for(let i=0;i<args.length;i++) {
    if(args[i] === '--expected-sha' && /^[a-f0-9]{40}$/.test(args[i+1] || '')) opts.sha=args[++i];
    else if(args[i] === '--preflight-only') opts.preflight=true;
    else if(args[i] === '--build-only') opts.buildOnly=true;
    else throw new Error('Uso: node qa/backend-http/run.mjs [--expected-sha SHA40] [--preflight-only | --build-only]');
  }
  if(opts.preflight && opts.buildOnly) throw new Error('Elegir solo un modo');
  const runId=randomUUID(), name=`prisaly-http-${runId}`;
  const out=join(HERE,'evidence',new Date().toISOString().replace(/[:.]/g,'-')+'-'+runId.slice(0,8));
  mkdirSync(out,{recursive:true});
  const secrets=[], env=cleanEnv();
  const report={runId,started:new Date().toISOString(),expectedSha:opts.sha,sha:null,
    branch:null,mode:opts.preflight?'preflight':opts.buildOnly?'build-only':'http',
    status:'running',exitCode:null,preflight:[],scenarios:[],cleanup:[]};
  let containerId=null, app=null, pump=null, interrupted=false;
  const interrupt=()=>{interrupted=true;app?.kill('SIGTERM');};
  process.on('SIGINT',interrupt);process.on('SIGTERM',interrupt);
  const log=(file,text)=>writeFileSync(join(out,file),redact(text,secrets));
  function command(command, argv, {input, timeout=30000, extraEnv={}}={}) {
    const result=spawnSync(command,argv,{cwd:ROOT,env:{...env,...extraEnv},input,
      encoding:'utf8',timeout,maxBuffer:32*1024*1024,windowsHide:true});
    return {ok:!result.error&&result.status===0,code:result.status,
      stdout:result.stdout||'', text:(result.stdout||'')+(result.stderr||''),error:result.error?.code};
  }
  function required(commandName,argv,options) {
    const result=command(commandName,argv,options);
    if(!result.ok) {log('command-failure.log',result.text);throw new Blocked(`${commandName}: ${result.error || 'exit '+result.code}`);}
    return result.text.trim();
  }
  function save() {
    report.finished=new Date().toISOString();
    report.totals=Object.fromEntries(['passed','failed','blocked'].map(s=>[s,report.scenarios.filter(x=>x.status===s).length]));
    json(join(out,'summary.json'),redact(report,secrets));
    const columns=['id','module','method','path','precondition','rf','cu','expectedStatus','actualStatus','status','evidence'];
    const csv=columns.join(',')+'\n'+report.scenarios.map(row=>columns.map(k=>'"'+String(row[k]??'').replaceAll('"','""')+'"').join(',')).join('\n')+'\n';
    writeFileSync(join(out,'matrix.csv'),csv);
  }
  const blockRemaining=reason=>{for(const spec of SCENARIOS) if(!report.scenarios.some(r=>r.id===spec.id)) report.scenarios.push({...spec,status:'blocked',actualStatus:null,reason,evidence:'summary.json'});};
  try {
    report.sha=required('git',['rev-parse','HEAD']);
    report.branch=required('git',['branch','--show-current']);
    const dirtyResult=command('git',['status','--porcelain','--untracked-files=all']);
    if(!dirtyResult.ok) throw new Blocked('No se puede verificar el árbol de trabajo');
    const dirty=dirtyResult.text;
    const outsideQa=dirty.split('\n').filter(Boolean).filter(line=>!line.slice(3).replaceAll('\\','/').startsWith('qa/backend-http/'));
    const sourcePaths=['pom.xml','app','m1','m2','m3','m4','m5','m6','m7','.mvn','mvnw','mvnw.cmd'];
    // HEAD may be the eventual QA commit: product tree must still equal the declared candidate.
    const productDiff=command('git',['diff','--quiet',opts.sha,'HEAD','--',...sourcePaths]);
    const sourceOk=productDiff.ok&&outsideQa.length===0;
    report.preflight.push({name:'candidate',ok:sourceOk,detail:sourceOk?'product tree matches expected SHA; only QA changes allowed':'checkout/product tree mismatch or changes outside QA'});
    const java=command('java',['-version']);
    report.java=java.text.trim();
    const javaOk=java.ok && /version "21[.\"]/.test(java.text);
    report.preflight.push({name:'java21',ok:javaOk});
    const docker=command('docker',['info','--format','{{.ServerVersion}}']);
    report.preflight.push({name:'docker',ok:docker.ok,error:docker.ok?null:redact(docker.text,secrets)});
    log('docker-preflight.log',docker.text);
    if(!sourceOk||!javaOk) throw new Blocked('Preflight: SHA/árbol de fuentes o Java 21 inválidos');
    const migrations=join(ROOT,'app/src/main/resources/db/migration');
    const files=readdirSync(migrations).filter(f=>f!=='.gitkeep').sort();
    if(files.length!==9 || !files.every(f=>/^V00[1-9]__.+\.sql$/.test(f))) throw new Blocked('Se requiere exclusivamente V001–V009');
    report.migrations=files.map(file=>({file,sha256:hash(readFileSync(join(migrations,file)))}));
    report.harness=Object.fromEntries(['run.mjs','fixture.sql'].map(file=>[file,hash(readFileSync(join(HERE,file)))]));
    if(opts.preflight) {
      if(!docker.ok) throw new Blocked('Docker Engine no disponible; no se inició APP ni PostgreSQL');
      report.status='preflight-ready'; blockRemaining('Preflight únicamente; HTTP no ejecutado'); report.exitCode=0;
    } else {
      if(!opts.buildOnly&&!docker.ok) throw new Blocked('Docker Engine no disponible; no se inició APP ni PostgreSQL');
      const build=process.platform==='win32'
        ?command(env.ComSpec||env.COMSPEC||'cmd.exe',['/d','/c','mvnw.cmd -B -pl app -am package -DskipTests'],{timeout:600000})
        :command('sh',['./mvnw','-B','-pl','app','-am','package','-DskipTests'],{timeout:600000});
      log('build.log',build.text);
      if(!build.ok) throw new Blocked('No se pudo construir APP; consultar build.log');
      const jars=readdirSync(join(ROOT,'app/target')).filter(f=>/^app-.+\.jar$/.test(f));
      if(jars.length!==1) throw new Blocked('No se identifica un único JAR APP');
      const jar=join(ROOT,'app/target',jars[0]); report.jarSha256=hash(readFileSync(jar));
      if(opts.buildOnly) {report.status='build-only';blockRemaining('Construcción únicamente; HTTP no ejecutado');report.exitCode=0;}
      else {
        const dbPassword=randomBytes(24).toString('hex');secrets.push(dbPassword);
        const started=command('docker',['run','--detach','--rm','--name',name,'--label',`prisaly.qa.run=${runId}`,
          '--publish','127.0.0.1::5432','--tmpfs','/var/lib/postgresql/data:rw',
          '-e','POSTGRES_PASSWORD','-e','POSTGRES_USER=qa_http','-e','POSTGRES_DB=qa_http','postgres:16.15'],
          {extraEnv:{POSTGRES_PASSWORD:dbPassword},timeout:180000});
        if(!started.ok) {log('docker-start.log',started.text);throw new Blocked('No se pudo iniciar PostgreSQL 16.15 efímero');}
        containerId=started.stdout.trim();
        if(!/^[a-f0-9]{64}$/.test(containerId)) throw new Blocked('Docker no devolvió ID inequívoco');
        const inspect=JSON.parse(required('docker',['inspect',containerId]))[0];
        if(!ownsContainer(inspect,containerId,name,runId)) throw new Blocked('No se acredita propiedad del contenedor');
        report.postgresImage={image:'postgres:16.15',imageId:inspect.Image};
        const mapping=inspect.NetworkSettings.Ports['5432/tcp'];
        if(mapping.length!==1||mapping[0].HostIp!=='127.0.0.1') throw new Blocked('PostgreSQL debe publicarse solo en loopback');
        let ready=false;
        for(let i=0;i<90&&!interrupted;i++) {if(command('docker',['exec',containerId,'pg_isready','-U','qa_http','-d','qa_http']).ok){ready=true;break;}await delay(1000);}
        if(!ready) throw new Blocked('PostgreSQL no disponible dentro del plazo');
        const sql=text=>required('docker',['exec','-i',containerId,'psql','-X','-q','-t','-A','-U','qa_http','-d','qa_http','-v','ON_ERROR_STOP=1'],{input:text,timeout:30000});
        const port=await new Promise((res,rej)=>{const s=net.createServer();s.on('error',rej);s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>res(p));});});
        const jdbc=`jdbc:postgresql://127.0.0.1:${mapping[0].HostPort}/qa_http?sslmode=disable`;
        app=spawn('java',['-jar',jar,'--server.address=127.0.0.1',`--server.port=${port}`,
          '--server.servlet.context-path=/api','--spring.jpa.hibernate.ddl-auto=validate','--spring.flyway.enabled=true',
          '--prisaly.m1.student-access.enabled=false','--prisaly.m7.worker.enabled=false'],
          {cwd:ROOT,env:{...env,DATABASE_URL:jdbc,DATABASE_USER:'qa_http',DATABASE_PASSWORD:dbPassword},stdio:['ignore','pipe','pipe'],windowsHide:true});
        // Buffer by line so a credential split across stream chunks cannot leak.
        let appText=''; const chunks=[];
        for(const stream of [app.stdout,app.stderr]) stream.on('data',b=>chunks.push(b));
        pump=()=>{appText=Buffer.concat(chunks).toString('utf8');log('app.log',appText);};
        app.on('error',()=>{});
        const base=`http://127.0.0.1:${port}/api`;
        let available=false;
        for(let i=0;i<120&&!interrupted;i++) {
          if(app.exitCode!==null||app.signalCode!==null) break;
          try {const r=await fetch(base+'/health',{signal:AbortSignal.timeout(1000),redirect:'error'});const b=await r.json();
            if(r.status===200&&b.service==='prisaly-backend'&&b.status==='ok'&&Buffer.concat(chunks).toString('utf8').includes('Started PrisalyApplication')){await delay(200);if(app.exitCode===null){available=true;break;}}} catch {}
          await delay(500);
        }
        pump();
        if(!available) throw new Blocked('APP real no arrancó; consultar app.log');
        report.baseUrl=base;
        report.postgresVersion=sql('SHOW server_version;');
        if(!report.postgresVersion.startsWith('16.15')) throw new Blocked('Versión PostgreSQL inesperada');
        for(const file of ['bruno/scripts/m1-bruno-seed-local.sql','bruno/scripts/m7-bruno-seed-local.sql','qa/backend-http/fixture.sql']) {
          const content=readFileSync(join(ROOT,file),'utf8');sql(content);
          (report.fixtures??=[]).push({file,sha256:hash(content)});
        }
        const tokens={}; let session;
        const draftId=randomUUID();
        // Reuse the saved M4 positive request body instead of maintaining a second DTO.
        const collection=JSON.parse(readFileSync(join(ROOT,'postman/PRISALY-M4-PT-DCP-01.postman_collection.json'),'utf8'));
        const saved=collection.item.flatMap(f=>f.item||[]).find(i=>i.name.startsWith('PM4-01 |'));
        if(!saved?.request?.body?.raw) throw new Blocked('Solicitud canónica PM4-01 ausente');
        const sessionBody=JSON.parse(saved.request.body.raw.replaceAll('{{casoId}}',PUBLISHED)
          .replaceAll('{{fechaInicio}}','2035-11-08T14:00:00Z').replaceAll('{{fechaFin}}','2035-11-08T15:00:00Z'));
        sessionBody.titulo='Aceptación HTTP '+runId;
        const draftBody={idCaso:draftId,titulo:'Borrador aceptación HTTP',tiempoEstimadoSegundos:60};
        const login=correo=>({correo,password:PUBLIC_PASSWORD});
        const snapshot=()=>sql('SELECT md5(jsonb_build_array(' +
          ['Caso','Sesion','Convocatoria','Participacion','Intento','Respuesta'].map(table=>
            `(SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text), '[]'::jsonb) FROM "${table}" r)`
          ).join(',') + ')::text);');
        async function check(id,path,body,auth,extra=()=>[],capture) {
          if(interrupted) throw new Blocked('Ejecución interrumpida; no se continúa enviando solicitudes');
          const spec=SCENARIOS.find(s=>s.id===id), before=snapshot();
          const headers={'Accept':'application/json'};
          if(body!==undefined) headers['Content-Type']='application/json';
          if(auth) headers.Authorization='Bearer '+(tokens[auth]||auth);
          const result={...spec,path,actualStatus:null,status:'failed',evidence:id+'.json'};
          let responseBody=null,failures=[];
          try {
            const r=await fetch(base+path,{method:spec.method,headers,body:body===undefined?undefined:JSON.stringify(body),redirect:'error',signal:AbortSignal.timeout(10000)});
            result.actualStatus=r.status;
            const text=await r.text();try{responseBody=text?JSON.parse(text):null;}catch{responseBody=text;}
            if(r.status!==spec.expectedStatus) failures.push(`HTTP esperado ${spec.expectedStatus}, obtenido ${r.status}`);
            if(spec.expectedCode&&responseBody?.code!==spec.expectedCode) failures.push('Código de dominio inesperado');
            if(r.status>=400) {
              if(!responseBody || typeof responseBody.code!=='string' || typeof responseBody.message!=='string') failures.push('Error sin contrato code/message');
              if(/exception|jdbc:|org\.(springframework|hibernate)|select\s.+from|passwordhash|stacktrace/i.test(text)) failures.push('Respuesta expone detalles internos');
              if(snapshot()!==before) failures.push('Denegación/error alteró datos académicos persistidos');
            }
            if(!failures.length) failures.push(...extra(responseBody));
            if(capture&&!failures.length) capture(responseBody);
          } catch(e) {failures.push(redact(e.message,secrets));}
          result.failures=failures;result.status=failures.length?'failed':'passed';
          report.scenarios.push(result);
          json(join(out,result.evidence),redact({sha:report.sha,expectedSha:opts.sha,request:{method:spec.method,path,headers,body},
            response:{status:result.actualStatus,body:responseBody},assertions:result},secrets));save();
          return result.status==='passed';
        }
        const eq=(actual,expected,msg)=>actual===expected?[]:[msg];
        const doLogin=async(id,email,key)=>check(id,'/v1/personal/autenticar',login(email),null,
          b=>typeof b?.accessToken==='string'&&b.accessToken.length>0?[]:['Token no recibido'],b=>{tokens[key]=b.accessToken;secrets.push(b.accessToken);});
        await check('HTTP-01','/health',undefined,null,b=>eq(b?.status,'ok','Health incorrecto'));
        await check('HTTP-02','/v1/personal/me');
        await check('HTTP-03','/v1/personal/autenticar',login('unknown@prisaly.invalid'));
        await check('HTTP-04','/v1/personal/autenticar',{correo:'docente.bruno@prisaly.local',password:'incorrect-fixture'});
        await check('HTTP-05','/v1/personal/autenticar',login('inactive.bruno@prisaly.local'));
        await doLogin('HTTP-06','docente.bruno@prisaly.local','owner');
        await check('HTTP-08','/v1/personal/me',undefined,'invalid-fixture-token');
        await doLogin('HTTP-09','sinperm.bruno@prisaly.local','noperm');
        await doLogin('HTTP-10','other.qa@prisaly.invalid','other');
        await check('HTTP-11','/v1/casos',draftBody);
        await check('HTTP-15','/m4/sesiones');
        await check('HTTP-27','/v1/acceso-estudiante/validar',{challengeId:'absent-fixture',codigo:'00000000'});
        await check('HTTP-28',`/v1/estudiantes/intentos/${ABSENT}`);
        if(tokens.owner) {
          await check('HTTP-07','/v1/personal/me',undefined,'owner',b=>eq(b?.correo,'docente.bruno@prisaly.local','Identidad incorrecta'));
          await check('HTTP-13','/v1/casos',draftBody,'owner',b=>[
            ...eq(b?.idCaso,draftId,'ID borrador incorrecto'),...eq(b?.estado,'BORRADOR','Estado borrador incorrecto'),
            ...eq(sql(`SELECT "idDocente"::text FROM "Caso" WHERE "idCaso"='${draftId}';`),OWNER,'Propietario persistido incorrecto')]);
          await check('HTTP-14','/v1/casos',{...draftBody,idCaso:randomUUID(),docenteId:OTHER},'owner');
          for(const [id,caseId] of [['HTTP-17',DRAFT],['HTTP-18',INACTIVE],['HTTP-19',ABSENT]]) await check(id,'/m4/sesiones',{...sessionBody,casoId:caseId},'owner');
          await check('HTTP-20','/m4/sesiones',sessionBody,'owner',b=>[
            ...eq(b?.casoId,PUBLISHED,'Caso incorrecto'),...eq(b?.docenteId,OWNER,'Docente incorrecto'),
            ...eq(/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(b?.id||''),true,'ID de sesión inválido'),
            ...eq(sql(`SELECT ("fechaBloqueoAcademico" IS NOT NULL)::text FROM "Caso" WHERE "idCaso"='${PUBLISHED}';`),'true','F3-03 no persistido')],b=>{if(/^[a-f0-9-]{36}$/.test(b.id)) session=b.id;});
          if(session) {
            await check('HTTP-21',`/m4/sesiones/${session}`,undefined,'owner',b=>eq(b?.id,session,'Sesión diferente'));
            if(tokens.other) await check('HTTP-22',`/m4/sesiones/${session}`,undefined,'other');
            const conv=`/m4/sesiones/${session}/convocatorias`;
            await check('HTTP-24',conv,{estudianteId:STUDENT},'owner',b=>eq(b?.estudianteId,STUDENT,'Estudiante incorrecto'));
            await check('HTTP-25',conv,undefined,'owner',b=>Array.isArray(b)&&b.some(c=>c.estudianteId===STUDENT)?[]:['Convocatoria no aparece']);
            await check('HTTP-26',conv,{estudianteId:STUDENT},'owner');
          }
          await check('HTTP-23',`/m4/sesiones/${ABSENT}`,undefined,'owner');
          await check('HTTP-29',`/v1/estudiantes/intentos/${ABSENT}`,undefined,'owner');
          await check('HTTP-30',`/v1/estudiantes/intentos/${ABSENT}/respuestas/${ABSENT}`,{idOpcionSeleccionada:ABSENT,revisionEsperada:0},'owner');
          await check('HTTP-31','/v1/personal/cerrar-sesion',undefined,'owner');
          await check('HTTP-32','/v1/personal/me',undefined,'owner');
        }
        if(tokens.noperm) {
          await check('HTTP-12','/v1/casos',{...draftBody,idCaso:randomUUID()},'noperm');
          await check('HTTP-16','/m4/sesiones',{...sessionBody,titulo:'Sin permiso'},'noperm');
        }
        blockRemaining('Dependencia HTTP previa fallida; no se fabrican tokens ni IDs');
        report.status=report.scenarios.every(s=>s.status==='passed')?'passed':'failed';
        report.exitCode=report.status==='passed'?0:1;
      }
    }
  } catch(error) {
    report.status=error instanceof Blocked?'blocked':'failed'; report.exitCode=error instanceof Blocked?2:1;
    report.problem=redact(error.message,secrets);blockRemaining(report.problem);
  } finally {
    if(app) {
      app.kill('SIGTERM');
      for(let i=0;i<50&&app.exitCode===null&&app.signalCode===null;i++) await delay(100);
      if(app.exitCode===null&&app.signalCode===null) {app.kill('SIGKILL');await delay(200);}
      pump?.();report.cleanup.push({resource:'own-app',ok:app.exitCode!==null||app.signalCode!==null});
    }
    if(containerId) {
      const result=command('docker',['inspect',containerId]);let removed=false;
      try {const info=JSON.parse(result.text)[0];if(ownsContainer(info,containerId,name,runId)) removed=command('docker',['rm','--force',containerId]).ok;}catch{}
      report.cleanup.push({resource:'own-postgres',ok:removed});
    }
    if(report.cleanup.some(c=>!c.ok)) {report.status='failed';report.exitCode=1;report.problem='Limpieza propia incompleta; revisar summary.json';}
    process.removeListener('SIGINT',interrupt);process.removeListener('SIGTERM',interrupt);
    save();console.log(JSON.stringify({status:report.status,exitCode:report.exitCode,evidence:relative(ROOT,out),totals:report.totals}));
  }
  return report.exitCode;
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href) {
  main().then(code=>{process.exitCode=code;}).catch(()=>{console.error('Error de argumentos o inicialización; sin ejecución HTTP.');process.exitCode=2;});
}
