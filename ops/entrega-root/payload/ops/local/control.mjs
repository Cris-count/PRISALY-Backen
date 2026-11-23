import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomBytes,createHash} from 'node:crypto';
import net from 'node:net';
const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'../..');
const project=process.env.PRISALY_OPS_PROJECT||'prisaly-ops70-local';
if(!/^prisaly-ops(?:70|71)-[a-z0-9-]{1,32}$/.test(project))throw Error('Nombre de instancia local no permitido.');
const build=path.join(here,'.build');
const command=process.argv[2];
if(!['up','down','status'].includes(command))throw Error('Usar npm run up, down o status desde la raÃ­z.');
function run(exe,args=[],options={}) {
 const r=spawnSync(exe,args,{cwd:root,encoding:'utf8',stdio:options.capture?'pipe':'inherit',...options});
 if(r.error)throw r.error;
 if(r.status!==0&&!options.allowFailure)throw Error(`FallÃ³ ${path.basename(exe)} (cÃ³digo ${r.status}). Revisar la salida; no se borraron datos.`);
 return r;
}
function shellFile(exe,args,cwd=root) {
 if(process.platform!=='win32')return run(exe,args,{cwd});
 const quote=x=>"'"+String(x).replaceAll("'","''")+"'";
 return run('powershell.exe',['-NoProfile','-NonInteractive','-Command','& '+[exe,...args].map(quote).join(' ')+'; exit $LASTEXITCODE'],{cwd});
}
function compose(args,options={}) {
 return run('docker',['compose','-p',project,'--project-directory',here,'--env-file',fs.existsSync(path.join(here,'.env'))?path.join(here,'.env'):path.join(root,'.env.example'),'-f',path.join(here,'compose.yaml'),...args],{...options,env:{...process.env,...settings}});
}
function digest(paths) {
 const h=createHash('sha256');
 const walk=p=>{if(!fs.existsSync(p))return;const s=fs.statSync(p);if(s.isDirectory()){for(const n of fs.readdirSync(p).sort())walk(path.join(p,n));}else{h.update(path.relative(root,p));h.update(fs.readFileSync(p));}};
 paths.forEach(walk);return h.digest('hex');
}
function git(repo,...args){return run('git',['--no-optional-locks','-c','safe.directory='+repo.replaceAll('\\','/'),'-C',repo,...args],{capture:true}).stdout.trim();}
async function portFree(port){await new Promise((resolve,reject)=>{const s=net.createServer();s.once('error',()=>reject(Error(`Puerto ${port} ocupado; no se detendrÃ¡ su propietario.`)));s.listen(port,'127.0.0.1',()=>s.close(resolve));});}
const settings={WEB_PORT:'18170',MAILPIT_PORT:'18025',SMTP_PORT:'11025'};
if(fs.existsSync(path.join(here,'.env')))for(const line of fs.readFileSync(path.join(here,'.env'),'utf8').split(/\r?\n/)){
 if(!line.trim()||line.trim().startsWith('#'))continue;
 const m=line.match(/^(WEB_PORT|MAILPIT_PORT|SMTP_PORT)=(\d+)$/);if(!m)throw Error('ops/local/.env solo admite los tres puertos documentados, sin secretos ni flags.');settings[m[1]]=m[2];
}
for(const p of Object.values(settings))if(+p<1024||+p>65535)throw Error('Puerto fuera de rango 1024â€“65535.');
if(new Set(Object.values(settings)).size!==3)throw Error('Los puertos deben ser diferentes.');
run('docker',['info'],{capture:true});
const endpoint=process.env.DOCKER_HOST||JSON.parse(run('docker',['context','inspect','--format','{{json .Endpoints.docker.Host}}'],{capture:true}).stdout);
if(!/^(npipe:|unix:)/.test(endpoint))throw Error('Se requiere Docker local; no se utilizarÃ¡ un daemon remoto.');
const ids=run('docker',['ps','-aq','--filter','label=com.docker.compose.project='+project],{capture:true}).stdout.trim().split(/\s+/).filter(Boolean);
for(const id of ids){const label=run('docker',['inspect','--format','{{index .Config.Labels "com.prisaly.instance"}}',id],{capture:true}).stdout.trim();if(label!=='ops70-root')throw Error('Proyecto Docker con propietario inesperado; detener y revisar.');}
if(command==='status'){
 compose(['ps','--all']);
 console.log(`Angular/API: http://localhost:${settings.WEB_PORT} | Mailpit: http://localhost:${settings.MAILPIT_PORT}`);
 process.exit(0);
}
fs.mkdirSync(build,{recursive:true});
const lock=path.join(build,'operation.lock');let handle;
try{handle=fs.openSync(lock,'wx');}catch{throw Error('Otra operaciÃ³n OPS70 estÃ¡ en curso. Si se interrumpiÃ³, revisar su proceso antes de retirar .build/operation.lock.');}
try {
 if(command==='down'){
  if(ids.length)compose(['down','--timeout','30']);else console.log('La instancia OPS70 ya estÃ¡ apagada.');
  console.log('VolÃºmenes y credenciales conservados. No se usa down -v.');
 }else{
  if(!ids.length)for(const p of Object.values(settings))await portFree(+p);
  const secretDir=path.join(here,'.secrets'),secret=path.join(secretDir,'postgres-password');
  const volumeExists=run('docker',['volume','inspect',project+'_pg_data'],{capture:true,allowFailure:true}).status===0;
  if(volumeExists&&!fs.existsSync(secret))throw Error('Existe volumen sin su clave local: recuperar .secrets desde copia privada; no generar ni cambiar la contraseÃ±a.');
  fs.mkdirSync(secretDir,{recursive:true,mode:0o700});
  if(!fs.existsSync(secret)){
   fs.writeFileSync(secret,randomBytes(36).toString('base64url'),{flag:'wx',mode:0o600});
   if(process.platform==='win32'){
    const account=run('whoami',[],{capture:true}).stdout.trim();
    run('icacls',[secret,'/inheritance:r','/grant:r',account+':(F)','/grant:r','*S-1-5-18:(F)'],{capture:true});
   }
  }
  if(!fs.existsSync(path.join(here,'.env')))fs.writeFileSync(path.join(here,'.env'),Object.entries(settings).map(([k,v])=>k+'='+v).join('\n')+'\n',{flag:'wx'});
  const backend=fs.realpathSync(path.join(root,'PRISALY-Backen')),frontend=fs.realpathSync(path.join(root,'PRISALY-Front'));
  if(git(backend,'diff','HEAD','--name-only','--','pom.xml',...['m1','m2','m3','m4','m5','m6','m7','app'].flatMap(m=>[m+'/src/main',m+'/pom.xml'])))throw Error('Fuentes Backend modificadas: revisar antes de reconstruir; no se sobrescribe trabajo.');
  const java=run('java',['-version'],{capture:true});if(!/version "21[.\"]/.test(java.stderr+java.stdout))throw Error('Se requiere Java 21 en PATH.');
  let previous={};try{previous=JSON.parse(fs.readFileSync(path.join(build,'sources.json'),'utf8'));}catch{}
  const backHash=digest(['pom.xml','.mvn','mvnw','mvnw.cmd',...['m1','m2','m3','m4','m5','m6','m7','app'].flatMap(m=>[m+'/pom.xml',m+'/src/main'])].map(p=>path.join(backend,p)));
  const frontHash=digest(['package.json','package-lock.json','angular.json','tsconfig.json','tsconfig.app.json','src','public'].map(p=>path.join(frontend,p)));
  if(previous.backend!==backHash||!fs.existsSync(path.join(build,'backend/app.jar'))){
   shellFile(path.join(backend,process.platform==='win32'?'mvnw.cmd':'mvnw'),['-B','-pl','app','-am','-DskipTests','package'],backend);
   const jars=fs.readdirSync(path.join(backend,'app/target')).filter(n=>n.endsWith('.jar')&&!n.endsWith('-sources.jar')&&!n.endsWith('-javadoc.jar'));
   if(jars.length!==1)throw Error('No se encontrÃ³ un Ãºnico JAR ejecutable.');
   fs.mkdirSync(path.join(build,'backend'),{recursive:true});fs.copyFileSync(path.join(backend,'app/target',jars[0]),path.join(build,'backend/app.jar'));
  }
  if(!fs.existsSync(path.join(frontend,'node_modules'))){shellFile(process.platform==='win32'?'npm.cmd':'npm',['ci'],frontend);}
  const dependencyLock=JSON.parse(fs.readFileSync(path.join(frontend,'package-lock.json'),'utf8'));
  for(const name of Object.keys({...dependencyLock.packages[''].dependencies,...dependencyLock.packages[''].devDependencies})){
   const installed=JSON.parse(fs.readFileSync(path.join(frontend,'node_modules',name,'package.json'),'utf8')).version;
   if(installed!==dependencyLock.packages['node_modules/'+name].version)throw Error('Dependencia distinta del lockfile: '+name+'. Revisar npm ci; no se reemplaza node_modules existente automÃ¡ticamente.');
  }
  if(previous.frontend!==frontHash||!fs.existsSync(path.join(build,'frontend/browser/index.html')))
   run(process.execPath,[path.join(frontend,'node_modules/@angular/cli/bin/ng.js'),'build','--configuration','production','--output-path',path.join(build,'frontend')],{cwd:frontend,env:{...process.env,NG_CLI_ANALYTICS:'false'}});
  fs.mkdirSync(path.join(build,'health'),{recursive:true});run('javac',['-d',path.join(build,'health'),path.join(here,'Healthcheck.java')]);
  fs.writeFileSync(path.join(build,'sources.json'),JSON.stringify({backend:backHash,frontend:frontHash,backendSha:git(backend,'rev-parse','HEAD'),frontendSha:git(frontend,'rev-parse','HEAD')},null,2));
  compose(['config','--quiet']);compose(['up','--build','--detach','--wait','--wait-timeout','240']);
  const response=await fetch('http://127.0.0.1:'+settings.WEB_PORT+'/api/health',{signal:AbortSignal.timeout(10000)});
  if(!response.ok)throw Error('La API a travÃ©s del proxy no estÃ¡ saludable. Contenedores conservados para diagnÃ³stico.');
  console.log(`PRISALY listo: http://localhost:${settings.WEB_PORT} | Mailpit: http://localhost:${settings.MAILPIT_PORT}`);
  console.log('AutenticaciÃ³n conservada. No se inicializaron cuentas ni se activaron flags.');
 }
}finally{fs.closeSync(handle);fs.unlinkSync(lock);}
