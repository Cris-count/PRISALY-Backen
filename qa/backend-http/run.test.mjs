import test from 'node:test';
import assert from 'node:assert/strict';
import {redact,cleanEnv,ownsContainer,SCENARIOS,spawnPlan} from './run.mjs';
test('P2-5: newman uses a shell only on Windows and quotes arguments with spaces',()=>{
 assert.deepEqual(spawnPlan('newman',['run','a b.json'],'linux'),{file:'newman',args:['run','a b.json'],shell:false});
 assert.deepEqual(spawnPlan('git',['status'],'win32'),{file:'git',args:['status'],shell:false});
 const plan=spawnPlan('newman',['run','C:\\x y\\c.json','--folder','01-m1'],'win32');
 assert.equal(plan.shell,true);assert.deepEqual(plan.args,['run','"C:\\x y\\c.json"','--folder','01-m1']);
});
test('redacts request/response secrets and split-stream assembled log',()=>{
 const actual=redact({headers:{Authorization:'Bearer abc'},body:{password:'value',accessToken:'abc',code:'AUTH_FAILED'},nested:[{cookie:'secret'}]},['abc']);
 assert.equal(actual.headers.Authorization,'[REDACTED]');assert.equal(actual.body.accessToken,'[REDACTED]');
 assert.equal(actual.body.code,'AUTH_FAILED');assert.ok(!JSON.stringify(actual).includes('value'));
 assert.equal(redact(Buffer.concat([Buffer.from('secretTo'),Buffer.from('ken')]).toString(),['secretToken']),'[REDACTED]');
});
test('does not inherit application configuration or JVM injection',()=>{
 const env=cleanEnv({PATH:'bin',SystemRoot:'win',DATABASE_URL:'shared',SPRING_PROFILES_ACTIVE:'prod',JAVA_TOOL_OPTIONS:'injected',HTTP_PROXY:'proxy'});
 assert.deepEqual(env,{PATH:'bin',SystemRoot:'win'});
});
test('cleanup requires exact container ID, name and per-run label',()=>{
 const id='a'.repeat(64), info={Id:id,Name:'/qa',Config:{Labels:{'prisaly.qa.run':'run'}}};
 assert.ok(ownsContainer(info,id,'qa','run'));
 assert.ok(!ownsContainer(info,id,'qa','other'));
 assert.ok(!ownsContainer(info,'b'.repeat(64),'qa','run'));
 assert.ok(!ownsContainer(info,id,'shared','run'));
});
test('scenario inventory has unique mandatory IDs and no invented RF/CU',()=>{
 assert.equal(SCENARIOS.length,32);assert.equal(new Set(SCENARIOS.map(s=>s.id)).size,32);
 for(const spec of SCENARIOS){assert.equal(spec.rf,null);assert.equal(spec.cu,null);assert.equal(spec.mandatory,true);}
 assert.equal(SCENARIOS.find(s=>s.id==='HTTP-27').expectedStatus,501);
});
