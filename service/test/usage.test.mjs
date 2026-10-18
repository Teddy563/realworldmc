import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {UsageMeter} from '../src/usage.mjs';

test('local usage persists, separates tile/root/other requests and rolls over at Pacific month',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rwmc-usage-'));const file=path.join(dir,'usage.json');
 let now=new Date('2026-10-31T23:00:00Z');const clock=()=>now;
 try {
  const meter=new UsageMeter(file,clock);meter.record('tiles');meter.record('roots');meter.record('metadata');meter.flush();
  const saved=new UsageMeter(file,clock);assert.equal(saved.snapshot().requests,3);assert.equal(saved.snapshot().tiles,1);assert.equal(saved.snapshot().roots,1);
  now=new Date('2026-11-01T01:00:00Z');assert.equal(saved.snapshot().month,'2026-10');
  now=new Date('2026-11-01T08:01:00Z');assert.equal(saved.snapshot().month,'2026-11');assert.equal(saved.snapshot().requests,0);
  fs.writeFileSync(file,'broken');const broken=new UsageMeter(file,clock);broken.record('tiles');broken.flush();assert.equal(broken.snapshot().available,false);assert.equal(fs.readFileSync(file,'utf8'),'broken');
 } finally {fs.rmSync(dir,{recursive:true,force:true});}
});
