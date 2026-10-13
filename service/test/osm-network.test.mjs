import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { waterFeatures } from '../src/osm.mjs';

test('OSM timeout, shared requests, cooldown across cells, recovery and disabled mode', async () => {
  let calls=0, mode='hang';
  const feature={elements:[{type:'way',id:1,tags:{natural:'water'},geometry:[{lat:0,lon:0},{lat:0,lon:1},{lat:1,lon:0},{lat:0,lon:0}]}]};
  const server=http.createServer((req,res)=>{calls++; if(mode==='hang') return; if(mode==='body'){res.writeHead(200,{'Content-Type':'application/json'});res.write('{');return;} res.end(JSON.stringify(feature));});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const cacheDir=await fs.mkdtemp(path.join(os.tmpdir(),'rwmc-water-'));
  const cfg={cacheDir,overpassUrl:`http://127.0.0.1:${server.address().port}`,waterTimeoutMs:100,waterCooldownMs:200};
  try {
    const start=Date.now();
    const result=await Promise.all([waterFeatures(cfg,0,0,0,0),waterFeatures(cfg,0,0,0,0)]);
    assert.deepEqual(result,[[],[]]);assert.equal(calls,1);assert.ok(Date.now()-start<1500,'bounded timeout');
    await waterFeatures(cfg,1,1,1,1);assert.equal(calls,1,'cooldown covers different cells');
    mode='ok';await new Promise(r=>setTimeout(r,250));
    assert.equal((await waterFeatures(cfg,0,0,0,0)).length,1,'failed cell recovers');assert.equal(calls,2);
    assert.equal((await waterFeatures(cfg,0,0,0,0)).length,1);assert.equal(calls,2,'successful response cached');
    assert.deepEqual(await waterFeatures({...cfg,water:false},0,0,0,0),[],'disabled ignores cached water');
    assert.deepEqual(await waterFeatures({...cfg,water:false},2,2,2,2),[]);assert.equal(calls,2,'disabled makes no requests');
    mode='body';const bodyStart=Date.now();assert.deepEqual(await waterFeatures(cfg,3,3,3,3),[]);assert.ok(Date.now()-bodyStart<1500,'timeout includes response body');
    await waterFeatures(cfg,4,4,4,4);assert.equal(calls,3,'body timeout also activates cooldown');
  } finally {server.closeAllConnections();await new Promise(r=>server.close(r));await fs.rm(cacheDir,{recursive:true,force:true});}
});
