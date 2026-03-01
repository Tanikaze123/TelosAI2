import * as THREE from 'three';
import { BufferGeometryUtils } from 'three/examples/jsm/Addons.js';
import { fetchModelAssets } from '../services/assetsApi';

export interface LoadedModel {
  geometry: THREE.BufferGeometry;
  material: THREE.MeshStandardMaterial;
}

// ─── Cache ────────────────────────────────────────────────────────────────────

const cache = new Map<string, Promise<LoadedModel | null>>();

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Load a Minecraft model JSON from the resource pack and return Three.js geometry + material.
 * Returns null if the file doesn't exist in the resource pack.
 *
 * @param modelPath  Full path after "modelengine:" e.g. "wumpus/head_1"
 */
export function loadMinecraftModel(modelPath: string): Promise<LoadedModel | null> {
  if (cache.has(modelPath)) return cache.get(modelPath)!;
  const promise = _load(modelPath);
  cache.set(modelPath, promise);
  return promise;
}

export function clearModelCache() {
  cache.clear();
}

// ─── Implementation ───────────────────────────────────────────────────────────

async function _load(modelPath: string): Promise<LoadedModel | null> {
  const assets = await fetchModelAssets(modelPath);
  if (!assets) return null;
  const { model, texture } = assets;

  const material = new THREE.MeshStandardMaterial({ map: texture, transparent: true, alphaTest: 0.1, side: THREE.DoubleSide });

  // Build geometry from elements — one PlaneGeometry per face with correct UVs
  const [tw, th] = model.texture_size ?? [16, 16];
  const geometries: THREE.BufferGeometry[] = [];

  for (const element of model.elements ?? []) {
    const w = (element.to[0] - element.from[0]) / 16;
    const h = (element.to[1] - element.from[1]) / 16;
    const d = (element.to[2] - element.from[2]) / 16;
    const cx = (element.from[0] + element.to[0]) / 2 / 16;
    const cy = (element.from[1] + element.to[1]) / 2 / 16;
    const cz = (element.from[2] + element.to[2]) / 2 / 16;

    // Each face: plane size, rotation to face the right direction, offset from element center
    const faceDefs: Record<string, { pw: number; ph: number; rot: THREE.Euler; ox: number; oy: number; oz: number }> = {
      south: { pw: w, ph: h, rot: new THREE.Euler(0, 0, 0), ox: cx, oy: cy, oz: cz + d / 2 },
      north: { pw: w, ph: h, rot: new THREE.Euler(0, Math.PI, 0), ox: cx, oy: cy, oz: cz - d / 2 },
      east: { pw: d, ph: h, rot: new THREE.Euler(0, Math.PI / 2, 0), ox: cx + w / 2, oy: cy, oz: cz },
      west: { pw: d, ph: h, rot: new THREE.Euler(0, -Math.PI / 2, 0), ox: cx - w / 2, oy: cy, oz: cz },
      up: { pw: w, ph: d, rot: new THREE.Euler(-Math.PI / 2, 0, 0), ox: cx, oy: cy + h / 2, oz: cz },
      down: { pw: w, ph: d, rot: new THREE.Euler(Math.PI / 2, 0, 0), ox: cx, oy: cy - h / 2, oz: cz },
    };

    const elementGeos: THREE.BufferGeometry[] = [];
    for (const [faceName, def] of Object.entries(faceDefs)) {
      const face = element.faces[faceName as keyof typeof element.faces];
      if (!face?.uv) continue;

      const geo = new THREE.PlaneGeometry(def.pw, def.ph);

      // Map face UV [x1,y1,x2,y2] (pixel space) → normalized Three.js UVs
      // Three.js V=0 is bottom, Minecraft Y=0 is top, so V is flipped
      const u1 = face.uv[0] / tw, v1 = 1 - face.uv[1] / th;
      const u2 = face.uv[2] / tw, v2 = 1 - face.uv[3] / th;
      const uvAttr = geo.attributes.uv as THREE.BufferAttribute;
      uvAttr.setXY(0, u1, v1); // top-left
      uvAttr.setXY(1, u2, v1); // top-right
      uvAttr.setXY(2, u1, v2); // bottom-left
      uvAttr.setXY(3, u2, v2); // bottom-right
      uvAttr.needsUpdate = true;

      geo.applyMatrix4(new THREE.Matrix4().makeRotationFromEuler(def.rot));
      geo.translate(def.ox, def.oy, def.oz);
      elementGeos.push(geo);
    }

    if (elementGeos.length === 0) continue;
    const elementGeo = elementGeos.length === 1
      ? elementGeos[0]
      : BufferGeometryUtils.mergeGeometries(elementGeos);

    // Apply element-level rotation (pivot → rotate → un-pivot)
    if (element.rotation) {
      const { origin, axis, angle } = element.rotation;
      const ox = origin[0] / 16, oy = origin[1] / 16, oz = origin[2] / 16;
      const rad = angle * Math.PI / 180;
      const rotMat = axis === 'x' ? new THREE.Matrix4().makeRotationX(rad)
        : axis === 'y' ? new THREE.Matrix4().makeRotationY(rad)
          : new THREE.Matrix4().makeRotationZ(rad);
      elementGeo.translate(-ox, -oy, -oz);
      elementGeo.applyMatrix4(rotMat);
      elementGeo.translate(ox, oy, oz);
    }

    geometries.push(elementGeo);
  }

  if (geometries.length === 0) return null;
  const geometry = BufferGeometryUtils.mergeGeometries(geometries);

  // item_display pivots at exactly [8,8,8] in model space = [0.5,0.5,0.5] in block units
  geometry.translate(-0.5, -0.5, -0.5);
  // Flip Z: Minecraft +Z=south, Three.js +Z=toward viewer (opposite convention)
  geometry.applyMatrix4(new THREE.Matrix4().makeScale(-1, 1, -1));

  return { geometry, material };
}

