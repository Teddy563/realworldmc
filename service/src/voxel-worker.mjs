import { isMainThread, parentPort } from 'node:worker_threads';
import { Frame } from './geo.mjs';
import { voxelizeArea } from './voxel.mjs';

if (!isMainThread && parentPort) {
  parentPort.on('message', (msg) => {
    const { id, frameData, meshes, x0, z0, w, minY, height, opts } = msg;
    try {
      const frame = new Frame(frameData.lat, frameData.lon, frameData.h0);
      const res = voxelizeArea(frame, meshes, x0, z0, w, minY, height, opts);
      const transfers = [];
      if (res.vox?.buffer) transfers.push(res.vox.buffer);
      if (res.ground?.buffer) transfers.push(res.ground.buffer);
      parentPort.postMessage({ id, success: true, res }, transfers);
    } catch (err) {
      parentPort.postMessage({
        id,
        success: false,
        error: err?.message ?? String(err),
        status: err?.status,
      });
    }
  });
}
