import test from 'node:test';
import assert from 'node:assert/strict';
import { VoxelPool } from '../src/voxel-pool.mjs';
import { Frame } from '../src/geo.mjs';
import { voxelizeArea } from '../src/voxel.mjs';

function fixture() {
  const frame = new Frame(44.427454, 26.087287, 80);
  const positions = Float64Array.from([[0,64.6,0], [16,64.6,0], [0,64.6,16]].flatMap(p => frame.mcToEcef(...p)));
  const meshes = [{ primitives: [{ positions, indices: new Uint32Array([0,1,2]), uvs: new Float32Array([0,0,1,0,0,1]), color: [70,140,50] }] }];
  return [frame, meshes, 0, 0, 16, 0, 80];
}

test('worker output equals synchronous terrain byte for byte', async t => {
  const pool = new VoxelPool(2); t.after(() => pool.close());
  const args = fixture(); const expected = voxelizeArea(...args);
  assert.ok(expected.surfaceColumns > 0);
  for (const result of await Promise.all(Array.from({length:6}, () => pool.voxelize(...args)))) assert.deepEqual(result, expected);
});

test('worker crash drains queued work without assigning a worker twice', {timeout:10000}, async t => {
  const pool = new VoxelPool(1); t.after(() => pool.close());
  const args = fixture();
  const first = pool.voxelize(...args);
  const rejected = assert.rejects(first, /exited|worker/i);
  const next = pool.voxelize(...args);
  const state = pool.workers[0];
  await state.worker.terminate();
  await rejected;
  pool._retire(state, new Error('duplicate exit'));
  const third = pool.voxelize(...args);
  const expected = voxelizeArea(...args);
  assert.deepEqual(await next, expected); assert.deepEqual(await third, expected);
  assert.equal(pool.workers.length, 1);
});

test('queue is bounded and shutdown rejects queued and new work', async () => {
  const pool = new VoxelPool(1); const args = fixture();
  const work = Array.from({length:6}, () => pool.voxelize(...args));
  const settled = Promise.allSettled(work);
  pool.close(); pool.close();
  const results = await settled;
  assert.ok(results.every(r => r.status === 'rejected'));
  assert.equal(results[5].reason.status, 503);
  await assert.rejects(pool.voxelize(...args), /closed/);
});

test('uncloneable request rejects without poisoning the worker', async t => {
  const pool = new VoxelPool(1); t.after(() => pool.close()); const args = fixture();
  await assert.rejects(pool.voxelize(...args, {callback: () => {}}));
  assert.deepEqual(await pool.voxelize(...args), voxelizeArea(...args));
});

test('invalid sizes rejected and explicit zero preserves synchronous mode', async () => {
  for (const size of [-1, .5, Infinity, NaN, 9, '2', null]) assert.throws(() => new VoxelPool(size), RangeError);
  const pool = new VoxelPool(0); const args = fixture();
  assert.deepEqual(await pool.voxelize(...args), voxelizeArea(...args));
  pool.close(); await assert.rejects(pool.voxelize(...args), /closed/);
});
