// A fake "Google Photorealistic 3D Tiles" server for offline testing.
// Same structure as the real thing: root.json -> external tileset with a session
// parameter -> Draco-compressed, JPEG-textured glb tiles (y-up, ECEF node matrix).
//
// Scene (local ENU meters around the origin): 200x200 m sloped ground, a road
// along the x axis, a 20x30x40 m building and a tree.

import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Document, NodeIO } from '@gltf-transform/core';
import { ALL_EXTENSIONS, KHRDracoMeshCompression } from '@gltf-transform/extensions';
import draco3d from 'draco3dgltf';
import jpeg from 'jpeg-js';
import { Frame, mul4 } from './geo.mjs';

export const MOCK_ORIGIN = { lat: 52.516275, lon: 13.377704, ground: 80 };
const Z_UP_TO_Y_UP = [1, 0, 0, 0, 0, 0, -1, 0, 0, 1, 0, 0, 0, 0, 0, 1];

// Ground height above the origin's ground (local frame is anchored at MOCK_ORIGIN.ground).
export const groundHeight = (e /*, n */) => 0.03 * e;

// 4 colored quadrants in a 64x64 atlas.
const ATLAS = { grass: [0.25, 0.25], road: [0.75, 0.25], plaster: [0.25, 0.75], roof: [0.75, 0.75] };
function atlasJpeg() {
  const w = 64, data = Buffer.alloc(w * w * 4);
  const colors = [[70, 140, 50], [55, 55, 58], [215, 210, 200], [170, 60, 40]];
  for (let y = 0; y < w; y++) for (let x = 0; x < w; x++) {
    const c = colors[(y < 32 ? 0 : 2) + (x < 32 ? 0 : 1)];
    data.set([...c, 255], (y * w + x) * 4);
  }
  return jpeg.encode({ data, width: w, height: w }, 95).data;
}

class MeshBuilder {
  constructor() { this.pos = []; this.uv = []; this.idx = []; }
  vert(e, n, u, uv) { this.pos.push(e, n, u); this.uv.push(uv[0], uv[1]); return this.pos.length / 3 - 1; }
  quad(a, b, c, d, uv) {
    const i = [a, b, c, d].map((p) => this.vert(p[0], p[1], p[2], uv));
    this.idx.push(i[0], i[1], i[2], i[0], i[2], i[3]);
  }
}

function addGround(mb, e0, n0, e1, n1, step) {
  for (let e = e0; e < e1; e += step) for (let n = n0; n < n1; n += step) {
    // Like real photogrammetry there is no ground under buildings.
    if (e >= 10 && e + step <= 30 && n >= -20 && n + step <= 10) continue;
    const road = n + step / 2 > -5 && n + step / 2 < 5;
    const uv = road ? ATLAS.road : ATLAS.grass;
    mb.quad([e, n, groundHeight(e)], [e + step, n, groundHeight(e + step)], [e + step, n + step, groundHeight(e + step)], [e, n + step, groundHeight(e)], uv);
  }
}

function addBuilding(mb, e0, n0, e1, n1, h) {
  const g = groundHeight(e0) - 1;
  const top = groundHeight(e0) + h;
  mb.quad([e0, n0, g], [e1, n0, g], [e1, n0, top], [e0, n0, top], ATLAS.plaster);
  mb.quad([e1, n0, g], [e1, n1, g], [e1, n1, top], [e1, n0, top], ATLAS.plaster);
  mb.quad([e1, n1, g], [e0, n1, g], [e0, n1, top], [e1, n1, top], ATLAS.plaster);
  mb.quad([e0, n1, g], [e0, n0, g], [e0, n0, top], [e0, n1, top], ATLAS.plaster);
  mb.quad([e0, n0, top], [e1, n0, top], [e1, n1, top], [e0, n1, top], ATLAS.roof);
}

function addTree(mb, e, n, r) {
  const cu = groundHeight(e) + 6;
  const pts = [[r, 0, 0], [-r, 0, 0], [0, r, 0], [0, -r, 0], [0, 0, r], [0, 0, -r]].map((p) => [e + p[0], n + p[1], cu + p[2]]);
  const faces = [[0, 2, 4], [2, 1, 4], [1, 3, 4], [3, 0, 4], [2, 0, 5], [1, 2, 5], [3, 1, 5], [0, 3, 5]];
  for (const f of faces) {
    const i = f.map((k) => mb.vert(...pts[k], ATLAS.grass));
    mb.idx.push(...i);
  }
}

async function buildGlb(io, frame, mb, jpegBytes) {
  const doc = new Document();
  doc.getRoot().getAsset().copyright = 'Mock Data;Mock Imagery';
  const buf = doc.createBuffer();
  const tex = doc.createTexture().setImage(jpegBytes).setMimeType('image/jpeg');
  const mat = doc.createMaterial().setBaseColorTexture(tex);
  // Positions are stored in the glTF's y-up convention: (E, U, -N).
  const p = new Float32Array(mb.pos.length);
  for (let i = 0; i < mb.pos.length; i += 3) { p[i] = mb.pos[i]; p[i + 1] = mb.pos[i + 2]; p[i + 2] = -mb.pos[i + 1]; }
  const prim = doc.createPrimitive()
    .setAttribute('POSITION', doc.createAccessor().setType('VEC3').setArray(p).setBuffer(buf))
    .setAttribute('TEXCOORD_0', doc.createAccessor().setType('VEC2').setArray(new Float32Array(mb.uv)).setBuffer(buf))
    .setIndices(doc.createAccessor().setType('SCALAR').setArray(new Uint32Array(mb.idx)).setBuffer(buf))
    .setMaterial(mat);
  const mesh = doc.createMesh().addPrimitive(prim);
  // node: y-up local -> z-up ENU -> ECEF, then the 3D Tiles loader applies y-up -> z-up again,
  // so store (Z_UP_TO_Y_UP * ENU->ECEF * Y_UP_TO_Z_UP) here.
  const yUpLocalToEcef = mul4(frame.enuToEcefMatrix(), [1, 0, 0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1]);
  const node = doc.createNode().setMesh(mesh).setMatrix(mul4(Z_UP_TO_Y_UP, yUpLocalToEcef));
  doc.createScene().addChild(node);
  doc.createExtension(KHRDracoMeshCompression).setRequired(true).setEncoderOptions({ method: KHRDracoMeshCompression.EncoderMethod.EDGEBREAKER });
  return io.writeBinary(doc);
}

function enuBox(frame, ce, cn, cu, he, hn, hu) {
  // cu is meters above the origin's ground.
  const c = frame.mcToEcef(ce, cu + 64, -cn);
  const s = (v, k) => v.map((x) => x * k);
  return [...c, ...s(frame.e, he), ...s(frame.n, hn), ...s(frame.u, hu)];
}

export async function buildMockFiles() {
  const frame = new Frame(MOCK_ORIGIN.lat, MOCK_ORIGIN.lon, MOCK_ORIGIN.ground);
  const io = new NodeIO().registerExtensions(ALL_EXTENSIONS).registerDependencies({
    'draco3d.decoder': await draco3d.createDecoderModule(),
    'draco3d.encoder': await draco3d.createEncoderModule(),
  });
  const tex = atlasJpeg();
  const files = new Map();

  const coarse = new MeshBuilder();
  addGround(coarse, -100, -100, 100, 100, 50);
  addBuilding(coarse, 10, -20, 30, 10, 40);
  files.set('coarse.glb', await buildGlb(io, frame, coarse, tex));

  const quads = [[-100, -100], [0, -100], [-100, 0], [0, 0]];
  const children = [];
  for (let q = 0; q < 4; q++) {
    const [e0, n0] = quads[q];
    const mb = new MeshBuilder();
    addGround(mb, e0, n0, e0 + 100, n0 + 100, 2);
    if (q === 1 || q === 3) addBuilding(mb, Math.max(10, e0), Math.max(-20, n0), 30, Math.min(10, n0 + 100), 40);
    if (q === 2) addTree(mb, -30, 20, 3);
    files.set(`q${q}.glb`, await buildGlb(io, frame, mb, tex));
    children.push({ boundingVolume: { box: enuBox(frame, e0 + 50, n0 + 50, 20, 50, 50, 30) }, geometricError: 0, content: { uri: `q${q}.glb` } });
  }
  files.set('sub.json', JSON.stringify({
    asset: { version: '1.0' }, geometricError: 8,
    root: { boundingVolume: { box: enuBox(frame, 0, 0, 20, 100, 100, 30) }, geometricError: 4, refine: 'REPLACE', content: { uri: 'coarse.glb' }, children },
  }));
  files.set('root.json', JSON.stringify({
    asset: { version: '1.0' }, geometricError: 1e7,
    root: {
      boundingVolume: { box: enuBox(frame, 0, 0, 0, 2000, 2000, 2000) }, geometricError: 1e6, refine: 'REPLACE',
      children: [{ boundingVolume: { box: enuBox(frame, 0, 0, 20, 100, 100, 30) }, geometricError: 8, content: { uri: '/v1/3dtiles/datasets/mock/files/sub.json?session=MOCKSESSION' } }],
    },
  }));
  return files;
}

export async function startMockServer(port = 8766, { requireKey = 'TEST' } = {}) {
  const files = await buildMockFiles();
  const log = [];
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    log.push(url.pathname + url.search);
    if (requireKey && url.searchParams.get('key') !== requireKey) { res.writeHead(403); return res.end('bad key'); }
    const name = path.posix.basename(url.pathname);
    if (name !== 'root.json' && url.searchParams.get('session') !== 'MOCKSESSION') { res.writeHead(400); return res.end('missing session'); }
    const body = files.get(name);
    if (!body) { res.writeHead(404); return res.end(); }
    res.writeHead(200);
    res.end(body);
  });
  await new Promise((r) => server.listen(port, '127.0.0.1', r));
  return { server, log, url: `http://127.0.0.1:${server.address().port}/v1/3dtiles/root.json` };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { url } = await startMockServer(Number(process.argv[2] ?? 8766));
  console.log(`Mock 3D tiles at ${url} (key=TEST). Origin ${MOCK_ORIGIN.lat}, ${MOCK_ORIGIN.lon}`);
}
