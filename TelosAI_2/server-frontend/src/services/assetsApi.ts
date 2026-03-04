import * as THREE from 'three';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:4000';

// ─── Minecraft model JSON types ───────────────────────────────────────────────

export interface MCFace {
  uv?: [number, number, number, number]; // [u1, v1, u2, v2] in pixel coords
  texture: string;                       // e.g. "#0"
}

export interface MCElementRotation {
  origin: [number, number, number];
  axis: 'x' | 'y' | 'z';
  angle: number;
  rescale?: boolean;
}

export interface MCElement {
  from: [number, number, number]; // in 1/16 block units
  to: [number, number, number];
  rotation?: MCElementRotation;
  faces: Partial<Record<'north' | 'south' | 'east' | 'west' | 'up' | 'down', MCFace>>;
}

export interface MCModel {
  texture_size?: [number, number];
  textures?: Record<string, string>; // e.g. { "0": "modelengine:entity/wumpus" }
  elements?: MCElement[];
}

// ─── Requests ─────────────────────────────────────────────────────────────────

export async function fetchModelJson(modelPath: string): Promise<MCModel | null> {
  const res = await fetch(`${API_BASE}/api/rp/assets/modelengine/models/${modelPath}.json`);
  if (!res.ok) return null;
  return res.json();
}

export function fetchTexture(textureRef: string): Promise<THREE.Texture | null> {
  const url = `${API_BASE}/api/rp/assets/${textureRef}.png`;
  return new Promise((resolve) => {
    new THREE.TextureLoader().load(url, resolve, undefined, () => resolve(null));
  });
}

export async function fetchModelAssets(modelPath: string): Promise<{ model: MCModel; texture: THREE.Texture } | null> {
  const model = await fetchModelJson(modelPath);
  if (!model) return null;

  if (model.textures == null) return null;
  const textureKeys = Object.keys(model.textures);
  let keys;
  for (keys of textureKeys) {
    if (isNaN(+keys)) return null;
    break;
  }
  //currently supports telos: and modelengine: namespaces
  const textureRef = model.textures?.[keys || '']?.replace(':', '/textures/'); 
  if (textureRef == null) return null;
  //TODO: possible block/vanilla rendering in the future
  console.log('textureRef:', textureRef);
  const texture = await fetchTexture(textureRef);
  if (!texture) return null;
  texture.magFilter = THREE.NearestFilter; // pixel-perfect Minecraft style
  texture.minFilter = THREE.NearestFilter;

  return { model, texture };
}
