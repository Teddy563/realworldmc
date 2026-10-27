import os from 'node:os';
import { Worker } from 'node:worker_threads';
import { voxelizeArea } from './voxel.mjs';

const workerUrl = new URL('./voxel-worker.mjs', import.meta.url);

export class VoxelPool {
  constructor(size = Math.max(1, Math.min(2, os.availableParallelism() - 1))) {
    if (!Number.isInteger(size) || size < 0 || size > 8) throw new RangeError('Voxel workers must be an integer from 0 to 8');
    this.size = size;
    this.workers = [];
    this.queue = [];
    this.nextId = 0;
    this.closing = false;
    this.maxQueue = Math.max(4, size * 4);
    for (let i = 0; i < size; i++) this._spawnWorker();
  }

  _spawnWorker() {
    if (this.closing) return;
    try {
      const worker = new Worker(workerUrl);
      const state = { worker, task: null, retired: false };
      this.workers.push(state);
      worker.on('message', msg => {
        if (state.retired || !state.task || state.task.id !== msg.id) return;
        const task = state.task;
        state.task = null;
        worker.unref();
        if (msg.success) task.resolve(msg.res);
        else task.reject(Object.assign(new Error(msg.error), { status: msg.status }));
        this._drain();
      });
      worker.on('error', error => this._retire(state, error));
      worker.on('exit', () => this._retire(state, new Error('Voxel worker exited')));
      worker.unref();
      return state;
    } catch (error) {
      // Fail queued requests if there is no worker left, rather than hanging the HTTP request.
      if (!this.workers.length) this._rejectQueue(error);
    }
  }

  _rejectQueue(error) {
    for (const task of this.queue.splice(0)) task.reject(error);
  }

  _retire(state, error) {
    if (state.retired) return; // error and exit can both fire for the same worker.
    state.retired = true;
    this.workers.splice(this.workers.indexOf(state), 1);
    if (state.task) state.task.reject(error);
    state.task = null;
    void state.worker.terminate();
    // Respawn only on demand. A broken worker module must not cause an idle restart loop.
    if (!this.closing && this.queue.length) {
      this._spawnWorker();
      this._drain();
    }
  }

  _drain() {
    for (const state of this.workers) {
      if (state.task || state.retired) continue;
      while (this.queue.length && !state.task) {
        const task = this.queue.shift();
        state.task = task;
        state.worker.ref();
        try {
          const { frame, meshes, x0, z0, w, minY, height, opts, id } = task;
          state.worker.postMessage({ id, frameData: { lat: frame.lat, lon: frame.lon, h0: frame.h0 }, meshes, x0, z0, w, minY, height, opts });
        } catch (error) {
          state.task = null;
          state.worker.unref();
          task.reject(error);
        }
      }
    }
  }

  async voxelize(frame, meshes, x0, z0, w, minY, height, opts = {}) {
    if (this.closing) throw new Error('Voxel pool closed');
    if (this.size === 0) return voxelizeArea(frame, meshes, x0, z0, w, minY, height, opts);
    if (this.workers.length < this.size) this._spawnWorker();
    if (!this.workers.length) throw Object.assign(new Error('Voxel workers unavailable'), { status: 503 });
    if (this.queue.length >= this.maxQueue) throw Object.assign(new Error('Terrain queue full; retry shortly'), { status: 503 });
    return new Promise((resolve, reject) => {
      this.queue.push({ id: ++this.nextId, frame, meshes, x0, z0, w, minY, height, opts, resolve, reject });
      this._drain();
    });
  }

  close() {
    if (this.closing) return;
    this.closing = true;
    const error = new Error('Voxel pool closed');
    this._rejectQueue(error);
    for (const state of [...this.workers]) this._retire(state, error);
  }
}
