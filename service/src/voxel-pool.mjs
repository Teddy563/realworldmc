import os from 'node:os';
import path from 'node:path';
import { Worker } from 'node:worker_threads';
import { fileURLToPath } from 'node:url';
import { voxelizeArea } from './voxel.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const workerPath = path.join(here, 'voxel-worker.mjs');

export class VoxelPool {
  constructor(size) {
    if (size === undefined || size === null) {
      const cpus = typeof os.availableParallelism === 'function' ? os.availableParallelism() : (os.cpus()?.length || 4);
      // Leave at least 1 core for Minecraft / Node HTTP event loop
      this.size = Math.max(1, Math.min(cpus > 1 ? cpus - 1 : 1, 8));
    } else {
      this.size = Math.max(0, Number(size) || 0);
    }

    this.workers = [];
    this.freeWorkers = [];
    this.queue = [];
    this.inFlight = new Map();
    this.nextId = 1;
    this.closing = false;

    if (this.size > 0) {
      this._initWorkers();
    }
  }

  _initWorkers() {
    for (let i = 0; i < this.size; i++) {
      this._spawnWorker();
    }
  }

  _spawnWorker() {
    if (this.closing) return null;
    let worker;
    try {
      worker = new Worker(workerPath);
      worker.unref(); // Do not block process exit when idle
    } catch (e) {
      console.warn(`[voxel-pool] Failed to spawn worker thread: ${e.message}. Falling back to sync execution.`);
      return null;
    }

    const state = { worker, activeId: null };
    this.workers.push(state);

    worker.on('message', (msg) => {
      worker.unref();
      const { id, success, res, error, status } = msg;
      const task = this.inFlight.get(id);
      if (task) {
        this.inFlight.delete(id);
        if (success) {
          task.resolve(res);
        } else {
          const err = new Error(error);
          if (status) err.status = status;
          task.reject(err);
        }
      }
      state.activeId = null;
      this._processNext(state);
    });

    worker.on('error', (err) => {
      console.warn(`[voxel-pool] Worker error: ${err.message}`);
      this._handleWorkerExit(state);
    });

    worker.on('exit', (code) => {
      if (code !== 0 && !this.closing) {
        console.warn(`[voxel-pool] Worker stopped with exit code ${code}`);
      }
      this._handleWorkerExit(state);
    });

    this.freeWorkers.push(state);
    return state;
  }

  _handleWorkerExit(state) {
    const idx = this.workers.indexOf(state);
    if (idx !== -1) this.workers.splice(idx, 1);
    const freeIdx = this.freeWorkers.indexOf(state);
    if (freeIdx !== -1) this.freeWorkers.splice(freeIdx, 1);

    if (state.activeId != null) {
      const task = this.inFlight.get(state.activeId);
      if (task) {
        this.inFlight.delete(state.activeId);
        task.reject(new Error('Worker thread crashed during voxelization'));
      }
    }

    if (!this.closing && this.workers.length < this.size) {
      const replacement = this._spawnWorker();
      if (replacement) {
        this._processNext(replacement);
      }
    }
  }

  _processNext(state) {
    if (this.queue.length === 0) {
      if (!this.freeWorkers.includes(state)) {
        this.freeWorkers.push(state);
      }
      return;
    }

    const task = this.queue.shift();
    const id = this.nextId++;
    state.activeId = id;
    this.inFlight.set(id, task);

    state.worker.ref();
    state.worker.postMessage({
      id,
      frameData: { lat: task.frame.lat, lon: task.frame.lon, h0: task.frame.h0 },
      meshes: task.meshes,
      x0: task.x0,
      z0: task.z0,
      w: task.w,
      minY: task.minY,
      height: task.height,
      opts: task.opts,
    });
  }

  async voxelize(frame, meshes, x0, z0, w, minY, height, opts = {}) {
    // If worker threads are disabled or no workers are available, run synchronously
    if (this.size === 0 || this.workers.length === 0) {
      return voxelizeArea(frame, meshes, x0, z0, w, minY, height, opts);
    }

    return new Promise((resolve, reject) => {
      const task = { frame, meshes, x0, z0, w, minY, height, opts, resolve, reject };
      const workerState = this.freeWorkers.pop();
      if (workerState) {
        const id = this.nextId++;
        workerState.activeId = id;
        this.inFlight.set(id, task);
        workerState.worker.ref();
        workerState.worker.postMessage({
          id,
          frameData: { lat: frame.lat, lon: frame.lon, h0: frame.h0 },
          meshes,
          x0,
          z0,
          w,
          minY,
          height,
          opts,
        });
      } else {
        this.queue.push(task);
      }
    });
  }

  close() {
    this.closing = true;
    for (const { worker } of this.workers) {
      try {
        worker.terminate();
      } catch (_) {}
    }
    this.workers = [];
    this.freeWorkers = [];
    for (const [, task] of this.inFlight) {
      task.reject(new Error('Voxel pool closed'));
    }
    this.inFlight.clear();
    for (const task of this.queue) {
      task.reject(new Error('Voxel pool closed'));
    }
    this.queue = [];
  }
}
