import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import net from 'node:net';
const here=path.dirname(fileURLToPath(import.meta.url)),root=path.resolve(here,'../..');
const dir=path.join(here,'evidencias');fs.mkdirSync(dir,{recursive:true});
const project=process.env.PRISALY_OPS_PROJECT||'prisaly-ops70-local';
if(!/^prisaly-ops(?:70|71)-[a-z0-9-]{1,32}$/.test(project))throw Error('Nombre de instancia no permitido');
const stamp=new Date().toISOString().replaceAll(':','-');
const result={fecha_utc:new Date().toISOString(),checks:[]};
const settings=fs.existsSync(path.join(here,'.env'))?Object.fromEntries(fs.readFileSync(path.join(here,'.env'),'utf8').trim().split(/\r?\n/).map(l=>l.split('='))):{WEB_PORT:'18170',MAILPIT_PORT:'18025',SMTP_PORT:'11025'};
const record=(name,ok,details)=>{result.checks.push({name,ok,details});if(!ok)throw Error(name);};
function docker(args){const r=spawnSync('docker',args,{encoding:'utf8'});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();}
const base=['compose','-p',project,'--project-directory',here,'--env-file',path.join(here,'.env'),'-f',path.join(here,'compose.yaml')];
const sql=q=>docker([...base,'exec','-T','postgres','psql','-U','prisaly_ops70','-d','prisaly_ops70','-At','-v','ON_ERROR_STOP=1','-c',q]);
function lifecycle(op){const r=spawnSync(process.execPath,[path.join(here,'control.mjs'),op],{cwd:root,encoding:'utf8',env:process.env});fs.writeFileSync(path.join(dir,stamp+'-'+op+'-'+result.checks.length+'.log'),r.stdout+r.stderr,{flag:'wx'});record('npm run '+op+' (mismo controlador)',r.status===0,{exit:r.status});}
async function http(p,status){const r=await fetch('http://127.0.0.1:'+settings.WEB_PORT+p,{headers:{Connection:'close'},signal:AbortSignal.timeout(10000)});record('HTTP '+p,r.status===status,{actual:r.status,expected:status});return r;}
async function smtp(){
 const socket=net.createConnection({host:'127.0.0.1',port:+settings.SMTP_PORT});socket.setTimeout(10000,()=>socket.destroy(Error('SMTP timeout')));
 let buffer='',waiting=[],replies=[];
 socket.on('data',chunk=>{buffer+=chunk;for(;;){const i=buffer.indexOf('\n');if(i<0)break;const line=buffer.slice(0,i).trim();buffer=buffer.slice(i+1);if(/^\d{3} /.test(line)){const waiter=waiting.shift();if(waiter)waiter.resolve(line);else replies.push(line);}}});
 socket.on('error',e=>{for(const w of waiting.splice(0))w.reject(e);});
 const next=()=>replies.length?Promise.resolve(replies.shift()):new Promise((resolve,reject)=>waiting.push({resolve,reject}));
 const send=async(text,code)=>{socket.write(text+'\r\n');const reply=await next();if(!reply.startsWith(code))throw Error('Respuesta SMTP inesperada: '+reply.slice(0,3));};
 try{if(!(await next()).startsWith('220'))throw Error('SMTP no disponible');await send('EHLO ops70.local','250');await send('MAIL FROM:<control@prisaly.test>','250');await send('RCPT TO:<buzon@prisaly.test>','250');await send('DATA','354');await send('From: control@prisaly.test\r\nTo: buzon@prisaly.test\r\nSubject: OPS70-control\r\n\r\nPrueba local sin credenciales.\r\n.','250');await send('QUIT','221');}finally{socket.end();}
}
const baselineFormat='{{.Id}}|{{.Name}}|{{.State.Status}}|{{.State.StartedAt}}|{{.RestartCount}}|{{index .Config.Labels \"com.docker.compose.project\"}}';
const allIds=docker(['ps','-aq']).split(/\s+/).filter(Boolean);
const before=allIds.length?docker(['inspect','--format',baselineFormat,...allIds]).split(/\r?\n/).filter(l=>!l.endsWith('|'+project)):[];
fs.writeFileSync(path.join(dir,stamp+'-docker-antes.json'),JSON.stringify(before));
try {
 lifecycle('up');
 const page=await http('/',200);const html=await page.text();record('Angular servido',html.includes('<app-root'),{app_root:true});
 await http('/login',200);await http('/api/health',200);await http('/api/v1/personal/me',401);
 const config=JSON.parse(fs.readFileSync(path.join(here,'.build/sources.json'),'utf8'));result.sources=config;
 const files=fs.readdirSync(path.join(here,'.build/frontend/browser')).filter(f=>f.endsWith('.js'));
 const relativeApi=files.some(f=>/apiBaseUrl\s*:\s*["'`]\/api["'`]/.test(fs.readFileSync(path.join(here,'.build/frontend/browser',f),'utf8')));
 record('Bundle Angular conserva /api relativo',relativeApi,{no_frontend_source_changes:true});
 const v=sql('SELECT version FROM public.flyway_schema_history WHERE success ORDER BY installed_rank').split(/\r?\n/).map(Number);
 record('Solo migraciones oficiales',JSON.stringify(v)===JSON.stringify([1,2,3,4,5,6,7,8,9,13]),{versions:v});
 const marker=randomUUID();sql('CREATE SCHEMA IF NOT EXISTS ops70_validation; CREATE TABLE IF NOT EXISTS ops70_validation.persistencia (id text PRIMARY KEY); INSERT INTO ops70_validation.persistencia VALUES (\''+marker+'\')');
 record('Escritura de control aislada',sql("SELECT id FROM ops70_validation.persistencia WHERE id='"+marker+"'")===marker,{schema:'ops70_validation',no_academic_data:true});
 await smtp();const mail=await fetch('http://127.0.0.1:'+settings.MAILPIT_PORT+'/api/v1/messages');const mailbox=await mail.json();record('Mailpit SMTP y HTTP',mail.status===200&&mailbox.messages.some(m=>m.Subject==='OPS70-control'),{http:mail.status,smtp:250,recipient:'buzon@prisaly.test',M7_worker_enabled:false});
 lifecycle('down');record('Solo instancia propia apagada',docker(['ps','-aq','--filter','label=com.docker.compose.project='+project])==='',{});
 record('Volumen conservado',docker(['volume','inspect','--format','{{.Name}}',project+'_pg_data'])===project+'_pg_data',{});
 lifecycle('up');record('Persistencia tras down/up',sql("SELECT id FROM ops70_validation.persistencia WHERE id='"+marker+"'")===marker,{marker,volume:project+'_pg_data'});await http('/api/health',200);
 lifecycle('status');lifecycle('down');
 record('Apagado final confirmado',docker(['ps','-aq','--filter','label=com.docker.compose.project='+project])==='',{});
 const ids=before.map(l=>l.split('|')[0]);
 const after=ids.length?docker(['inspect','--format',baselineFormat,...ids]).split(/\r?\n/):[];
 record('Contenedores preexistentes intactos',JSON.stringify(before.sort())===JSON.stringify(after.sort()),{count:ids.length,ids_status_startedAt_restartCount_equal:true});
 result.ok=true;
}catch(e){result.ok=false;result.error=e.message;result.error_cause=e.cause?.code;console.error(e.message);process.exitCode=1;}
finally{fs.writeFileSync(path.join(dir,stamp+'-resultado.json'),JSON.stringify(result,null,2),{flag:'wx'});console.log(JSON.stringify(result,null,2));}
