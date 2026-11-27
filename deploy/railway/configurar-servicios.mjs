// DC-DEPLOY-03 · configuración de los servicios de Railway guardada en el propio servicio (API pública).
// Sustituye a railway.json (Config as Code, que Railway deja de leer el 01/12/2026). Idempotente.
// Uso (con la CLI autenticada): node deploy/railway/configurar-servicios.mjs [proyecto] [entorno]
// No lee ni escribe variables ni secretos.
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

// En Windows se invoca el binario de la CLI sin shell: cmd.exe alteraría los documentos GraphQL.
const exeWindows = join(process.env.APPDATA ?? '', 'npm', 'node_modules', '@railway', 'cli', 'bin', 'railway.exe');
const RAILWAY = process.platform === 'win32' && existsSync(exeWindows) ? exeWindows : 'railway';

const PROYECTO = process.argv[2] ?? 'a5def864-7d16-4211-9dea-e87ef72272f5'; // prisaly-staging
const ENTORNO = process.argv[3] ?? 'staging';

const CONFIG = {
  backend: {
    dockerfilePath: 'Dockerfile.railway', // con dockerfilePath, Railway construye con el Dockerfile
    healthcheckPath: '/api/health', healthcheckTimeout: 300,
    restartPolicyType: 'ON_FAILURE', restartPolicyMaxRetries: 5, sleepApplication: true,
  },
  frontend: {
    dockerfilePath: 'Dockerfile.railway', // con dockerfilePath, Railway construye con el Dockerfile
    healthcheckPath: '/salud', healthcheckTimeout: 120,
    restartPolicyType: 'ON_FAILURE', restartPolicyMaxRetries: 5, sleepApplication: true,
  },
  Postgres: { restartPolicyType: 'ON_FAILURE', restartPolicyMaxRetries: 10, sleepApplication: true },
};

function api(documento, variables) {
  const args = ['api', documento];
  for (const [k, v] of Object.entries(variables)) args.push('--var', `${k}=${typeof v === 'string' ? v : JSON.stringify(v)}`);
  const salida = execFileSync(RAILWAY, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
  const json = JSON.parse(salida.slice(salida.indexOf('{'), salida.lastIndexOf('}') + 1));
  if (json.errors?.length) throw new Error(json.errors.map((e) => e.message).join('; '));
  return json.data;
}

const datos = api(`query($p:String!){ project(id:$p){ name environments{ edges{ node{ id name serviceInstances{ edges{ node{ serviceId serviceName } } } } } } } }`, { p: PROYECTO });
const entorno = datos.project.environments.edges.map((e) => e.node).find((e) => e.name === ENTORNO);
if (!entorno) throw new Error(`Entorno ${ENTORNO} no encontrado en ${datos.project.name}`);
for (const { node } of entorno.serviceInstances.edges) {
  const config = CONFIG[node.serviceName];
  if (!config) { console.log(`- ${node.serviceName}: sin configuración declarada, no se toca`); continue; }
  api(`mutation($s:String!,$e:String!,$i:ServiceInstanceUpdateInput!){ serviceInstanceUpdate(serviceId:$s, environmentId:$e, input:$i) }`,
    { s: node.serviceId, e: entorno.id, i: config });
  console.log(`- ${node.serviceName}: ${Object.entries(config).map(([k, v]) => `${k}=${v}`).join(', ')}`);
}
console.log(`Configuración aplicada en ${datos.project.name}/${ENTORNO}. Surte efecto en el próximo despliegue.`);
