import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {loadConfig} from '../src/server.mjs';

test('current environment settings override compatible older names', t => {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'realworldmc-config-'));
  t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=path.join(dir,'config.json');fs.writeFileSync(file,'{}');
  const values={EARTHCRAFT_API_KEY:'OLD_TEST',EARTHCRAFT_PORT:'8123',EARTHCRAFT_DISK_CACHE:'1',
    REALWORLDMC_API_KEY:'NEW_TEST',REALWORLDMC_PORT:'8124',REALWORLDMC_DISK_CACHE:'0',
    RWMC_API_KEY:'RWMC_TEST',RWMC_PORT:'8125',RWMC_DISK_CACHE:'1',RWMC_CESIUM_TOKEN:'RWMC_TOKEN_TEST'};
  const previous=Object.fromEntries(Object.keys(values).map(k=>[k,process.env[k]]));
  t.after(()=>{for(const [k,v] of Object.entries(previous)){if(v===undefined)delete process.env[k];else process.env[k]=v;}});
  Object.assign(process.env,values);
  const rwmc=loadConfig(file);
  assert.equal(rwmc.apiKey,'RWMC_TEST');assert.equal(rwmc.port,8125);assert.equal(rwmc.diskCache,true);
  assert.equal(rwmc.cesiumToken,'RWMC_TOKEN_TEST');
  for(const k of Object.keys(values))if(k.startsWith('RWMC_'))delete process.env[k];
  const current=loadConfig(file);
  assert.equal(current.apiKey,'NEW_TEST');assert.equal(current.port,8124);assert.equal(current.diskCache,false);
  for(const k of Object.keys(values))if(k.startsWith('REALWORLDMC_'))delete process.env[k];
  const compatible=loadConfig(file);
  assert.equal(compatible.apiKey,'OLD_TEST');assert.equal(compatible.port,8123);assert.equal(compatible.diskCache,true);
});
