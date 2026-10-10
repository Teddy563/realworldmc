// WGS84 / ECEF / local frame math.
//
// Minecraft frame (per world "origin"):
//   MC x = East, MC y = Up + Y_BASE, MC z = South (= -North)
//   1 block = 1 meter. The origin is (lat, lon, h0) where h0 is the ellipsoid
//   height of the ground at the origin, which maps to MC y = Y_BASE.

export const Y_BASE = 64;

const A = 6378137.0;
const F = 1 / 298.257223563;
const E2 = F * (2 - F);
const B = A * (1 - F);
const EP2 = (A * A - B * B) / (B * B);
const DEG = Math.PI / 180;

export function geodeticToEcef(latDeg, lonDeg, h) {
  const lat = latDeg * DEG, lon = lonDeg * DEG;
  const sl = Math.sin(lat), cl = Math.cos(lat);
  const n = A / Math.sqrt(1 - E2 * sl * sl);
  return [(n + h) * cl * Math.cos(lon), (n + h) * cl * Math.sin(lon), (n * (1 - E2) + h) * sl];
}

export function ecefToGeodetic(x, y, z) {
  // Bowring's method, accurate to well below a millimeter near the surface.
  const p = Math.hypot(x, y);
  const th = Math.atan2(z * A, p * B);
  const st = Math.sin(th), ct = Math.cos(th);
  const lat = Math.atan2(z + EP2 * B * st * st * st, p - E2 * A * ct * ct * ct);
  const lon = Math.atan2(y, x);
  const sl = Math.sin(lat);
  const n = A / Math.sqrt(1 - E2 * sl * sl);
  const h = p / Math.cos(lat) - n;
  return [lat / DEG, lon / DEG, h];
}

/**
 * A local frame anchored at a geodetic origin. Converts between ECEF and
 * Minecraft block coordinates.
 */
export class Frame {
  constructor(latDeg, lonDeg, h0) {
    this.lat = latDeg; this.lon = lonDeg; this.h0 = h0;
    const lat = latDeg * DEG, lon = lonDeg * DEG;
    const sl = Math.sin(lat), cl = Math.cos(lat), so = Math.sin(lon), co = Math.cos(lon);
    // ENU basis vectors expressed in ECEF.
    this.e = [-so, co, 0];
    this.n = [-sl * co, -sl * so, cl];
    this.u = [cl * co, cl * so, sl];
    this.o = geodeticToEcef(latDeg, lonDeg, h0);
  }

  /** ECEF -> MC (writes into out[off..off+2]). */
  ecefToMc(x, y, z, out = [0, 0, 0], off = 0) {
    const dx = x - this.o[0], dy = y - this.o[1], dz = z - this.o[2];
    const e = this.e, n = this.n, u = this.u;
    out[off] = dx * e[0] + dy * e[1] + dz * e[2];
    out[off + 1] = dx * u[0] + dy * u[1] + dz * u[2] + Y_BASE;
    out[off + 2] = -(dx * n[0] + dy * n[1] + dz * n[2]);
    return out;
  }

  /** MC -> ECEF. */
  mcToEcef(mx, my, mz) {
    const E = mx, U = my - Y_BASE, N = -mz;
    const e = this.e, n = this.n, u = this.u, o = this.o;
    return [
      o[0] + E * e[0] + N * n[0] + U * u[0],
      o[1] + E * e[1] + N * n[1] + U * u[1],
      o[2] + E * e[2] + N * n[2] + U * u[2],
    ];
  }

  /** Rotate an ECEF direction vector into the MC frame (no translation). */
  dirToMc(x, y, z) {
    const e = this.e, n = this.n, u = this.u;
    return [x * e[0] + y * e[1] + z * e[2], x * u[0] + y * u[1] + z * u[2], -(x * n[0] + y * n[1] + z * n[2])];
  }

  /** 4x4 column-major matrix taking local ENU coordinates to ECEF. */
  enuToEcefMatrix() {
    const e = this.e, n = this.n, u = this.u, o = this.o;
    return [e[0], e[1], e[2], 0, n[0], n[1], n[2], 0, u[0], u[1], u[2], 0, o[0], o[1], o[2], 1];
  }
}

/**
 * Oriented bounding box in ECEF: center + 3 half-axis vectors (3D Tiles "box").
 * Returns the axis-aligned bounds of the box in MC coordinates.
 */
export function boxToMcAabb(frame, box) {
  const c = frame.ecefToMc(box[0], box[1], box[2]);
  const ext = [0, 0, 0];
  for (let i = 0; i < 3; i++) {
    const d = frame.dirToMc(box[3 + i * 3], box[4 + i * 3], box[5 + i * 3]);
    ext[0] += Math.abs(d[0]); ext[1] += Math.abs(d[1]); ext[2] += Math.abs(d[2]);
  }
  return { min: [c[0] - ext[0], c[1] - ext[1], c[2] - ext[2]], max: [c[0] + ext[0], c[1] + ext[1], c[2] + ext[2]], center: c, radius: Math.hypot(...ext) };
}

// --- tiny column-major 4x4 helpers -------------------------------------------------

export const IDENTITY = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];

// glTF is y-up, 3D Tiles is z-up: x' = x, y' = -z, z' = y.
export const Y_UP_TO_Z_UP = [1, 0, 0, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1];

export function mul4(a, b) {
  const r = new Array(16);
  for (let c = 0; c < 4; c++) {
    for (let rr = 0; rr < 4; rr++) {
      r[c * 4 + rr] = a[rr] * b[c * 4] + a[4 + rr] * b[c * 4 + 1] + a[8 + rr] * b[c * 4 + 2] + a[12 + rr] * b[c * 4 + 3];
    }
  }
  return r;
}

export function invertRigid4(m) {
  // Inverse of a rotation+translation matrix.
  const r = [m[0], m[4], m[8], 0, m[1], m[5], m[9], 0, m[2], m[6], m[10], 0, 0, 0, 0, 1];
  const t = [m[12], m[13], m[14]];
  r[12] = -(r[0] * t[0] + r[4] * t[1] + r[8] * t[2]);
  r[13] = -(r[1] * t[0] + r[5] * t[1] + r[9] * t[2]);
  r[14] = -(r[2] * t[0] + r[6] * t[1] + r[10] * t[2]);
  return r;
}

export function transformBox(m, box) {
  const c = [box[0], box[1], box[2]];
  const out = [
    m[0] * c[0] + m[4] * c[1] + m[8] * c[2] + m[12],
    m[1] * c[0] + m[5] * c[1] + m[9] * c[2] + m[13],
    m[2] * c[0] + m[6] * c[1] + m[10] * c[2] + m[14],
  ];
  for (let i = 0; i < 3; i++) {
    const v = [box[3 + i * 3], box[4 + i * 3], box[5 + i * 3]];
    out.push(m[0] * v[0] + m[4] * v[1] + m[8] * v[2], m[1] * v[0] + m[5] * v[1] + m[9] * v[2], m[2] * v[0] + m[6] * v[1] + m[10] * v[2]);
  }
  return out;
}

export function isIdentity(m) {
  for (let i = 0; i < 16; i++) if (Math.abs(m[i] - IDENTITY[i]) > 1e-12) return false;
  return true;
}
