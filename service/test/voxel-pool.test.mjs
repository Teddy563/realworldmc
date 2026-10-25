import test from 'node:test';
import assert from 'node:assert/strict';
import { VoxelPool } from '../src/voxel-pool.mjs';
import { Frame } from '../src/geo.mjs';

function makeMesh(xOffset = 0) {
  const frame = {};
  const pos = [xOffset, 64.6, 0, xOffset + 16, 64.6, 0, xOffset, 64.6, 16];
  const prim = {
    positions: Float64Array.from(pos),
    indices: new Uint32Array([0, 1, 2]),
    uvs: new Float32Array([0, 0, 1, 0, 0, 1]),
    color: [70, 140, 50],
    _mcFrame: frame,
    _mc: Float64Array.from(pos),
  };
  return { frame, meshes: [{ primitives: [prim] }] };
}

test('VoxelPool executes voxelization in worker threads and handles concurrency', async () => {
  const pool = new VoxelPool(2);
  const { frame, meshes } = makeMesh(0);

  // Dispatch 4 tasks concurrently across 2 workers
  const tasks = Array.from({ length: 4 }, (_, i) =>
    pool.voxelize(frame, meshes, i * 16, 0, 16, 0, 80)
  );

  const results = await Promise.all(tasks);
  assert.equal(results.length, 4);
  for (const res of results) {
    assert.ok(res.vox instanceof Uint8Array);
    assert.ok(res.vox.length > 0);
    assert.equal(typeof res.surfaceColumns, 'number');
  }

  pool.close();
});

test('VoxelPool fallback to synchronous mode when size is 0', async () => {
  const pool = new VoxelPool(0);
  assert.equal(pool.size, 0);
  assert.equal(pool.workers.length, 0);

  const { frame, meshes } = makeMesh(0);
  const res = await pool.voxelize(frame, meshes, 0, 0, 16, 0, 80);
  assert.ok(res.vox instanceof Uint8Array);
  assert.ok(res.surfaceColumns > 0);
  pool.close();
});
