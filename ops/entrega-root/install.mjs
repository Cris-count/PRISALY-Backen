import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
const base=path.dirname(fileURLToPath(import.meta.url));
const hash=content=>createHash('sha256').update(content).digest('hex');
function rejectLinks(target,root){for(let p=target;p!==root;p=path.dirname(p))if(fs.existsSync(p)&&fs.lstatSync(p).isSymbolicLink())throw Error('Destino con enlace: '+path.relative(root,target));}
export function plan(root){
 root=fs.realpathSync(root);
 for(const repo of ['PRISALY-Backen','PRISALY-Front'])if(!fs.statSync(path.join(root,repo)).isDirectory())throw Error('Se requieren ambos repositorios existentes en la raíz seleccionada.');
 const receiptPath=path.join(root,'ops/local/.installed.json');let receipt={files:{}};
 rejectLinks(receiptPath,root);
 if(fs.existsSync(receiptPath))receipt=JSON.parse(fs.readFileSync(receiptPath,'utf8'));
 const names=JSON.parse(fs.readFileSync(path.join(base,'payload-files.json'),'utf8'));
 const rows=names.map(name=>{
  if(path.isAbsolute(name)||name.split(/[\\/]/).includes('..')||name.includes('\\'))throw Error('Ruta no permitida en el paquete.');
  const target=path.resolve(root,name),source=path.join(base,'payload',name);
  if(!target.startsWith(root+path.sep))throw Error('Ruta fuera de la raíz.');
  rejectLinks(target,root);
  const sha256=hash(fs.readFileSync(source));const current=fs.existsSync(target)?hash(fs.readFileSync(target)):null;
  return {name,sha256,current,action:current===sha256?'igual':current===null?'crear':receipt.files?.[name]===current?'actualizar':'CONFLICTO'};
 });
 return {root,rows,conflicts:rows.filter(r=>r.action==='CONFLICTO').map(r=>r.name)};
}
export function install(root){
 const p=plan(root);
 if(p.conflicts.length)throw Error('No se escribió ningún archivo. Conflictos: '+p.conflicts.join(', ')+'. Solicitar autorización y revisar manualmente; no existe opción force.');
 const stamp=new Date().toISOString().replaceAll(':','-');
 for(const r of p.rows){
  if(r.action==='igual')continue;
  const target=path.join(p.root,r.name);fs.mkdirSync(path.dirname(target),{recursive:true});
  if(r.action==='actualizar'){
   const backup=path.join(p.root,'ops/local/.install-backups',stamp,r.name);rejectLinks(backup,p.root);fs.mkdirSync(path.dirname(backup),{recursive:true});fs.copyFileSync(target,backup,fs.constants.COPYFILE_EXCL);
  }
  // Verificación inmediatamente anterior a escribir: no sobrescribir un cambio concurrente.
  if(r.current!==null&&hash(fs.readFileSync(target))!==r.current)throw Error('El archivo cambió durante la instalación: '+r.name);
  if(r.current===null)fs.copyFileSync(path.join(base,'payload',r.name),target,fs.constants.COPYFILE_EXCL);
  else fs.copyFileSync(path.join(base,'payload',r.name),target);
 }
 const receipt=path.join(p.root,'ops/local/.installed.json');fs.mkdirSync(path.dirname(receipt),{recursive:true});
 fs.writeFileSync(receipt,JSON.stringify({version:1,installedUtc:new Date().toISOString(),files:Object.fromEntries(p.rows.map(r=>[r.name,r.sha256]))},null,2));
 return p;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  const args=process.argv.slice(2),index=args.indexOf('--root');
  if(index<0||!args[index+1]||args.some((a,i)=>i!==index+1&&!['--root','--check','--apply'].includes(a)))throw Error('Uso: node install.mjs --root RUTA_RAIZ --check | --apply');
  if(args.includes('--check')&&args.includes('--apply'))throw Error('Elegir check o apply.');
  const result=args.includes('--apply')?install(args[index+1]):plan(args[index+1]);console.log(JSON.stringify(result,null,2));if(result.conflicts.length)process.exitCode=2;
 }catch(e){console.error(e.message);process.exitCode=1;}
}
