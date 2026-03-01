import { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import type { EntityData, EntitySnapshot, TransformSnapshot } from '../types/replay';
import { loadMinecraftModel } from '../utils/minecraftModelLoader';

interface Props {
  selected: EntityData;
  allEntities: EntityData[];
  radius?: number;
  selectedTick?: number;
}

const ENTITY_SIZE: Record<string, [number, number, number]> = {
  item_display: [0.5, 0.5, 0.5],
  interaction: [1.0, 1.8, 1.0],
  armor_stand: [0.5, 1.975, 0.5],
  text_display: [0.5, 0.5, 0.5],
  block_display: [1.0, 1.0, 1.0],
  player: [0.6, 1.8, 0.6],
  zombie: [0.6, 1.95, 0.6],
  skeleton: [0.6, 1.99, 0.6],
};
const DEFAULT_SIZE: [number, number, number] = [0.6, 1.0, 0.6];

const ENTITY_COLOR: Record<string, number> = {
  item_display: 0xf0883e,
  interaction: 0xa371f7,
  armor_stand: 0xda3633,
  text_display: 0x3fb950,
  block_display: 0xd29922,
  player: 0x1f6feb,
};
const DEFAULT_COLOR = 0x8b949e;

interface MeshEntry {
  mesh: THREE.Mesh;
  color: number;
  baseOpacity: number;
  isModel: boolean;
}

// ─── Timeline helpers ─────────────────────────────────────────────────────────

/** Returns the last snapshot with tick ≤ target, or the first snapshot. */
function getSnapshotAtTick(timeline: EntitySnapshot[], tick: number): EntitySnapshot | null {
  if (!timeline.length) return null;
  let last = timeline[0];
  for (const s of timeline) {
    if (s.tick > tick) break;
    last = s;
  }
  return last;
}

/** Returns the last transform snapshot with tick ≤ target. */
function getTransformAtTick(tl: TransformSnapshot[] | undefined, tick: number): TransformSnapshot | null {
  if (!tl?.length) return null;
  let last = tl[0];
  for (const t of tl) {
    if (t.tick > tick) break;
    last = t;
  }
  return last;
}

function getNearby(allEntities: EntityData[], selected: EntityData, radius: number) {
  const origin = selected.timeline[0];
  if (!origin) return [];
  return allEntities.filter(e => {
    if (e.id === selected.id) return false;
    const s = e.timeline[0];
    if (!s) return false;
    const dx = s.x - origin.x, dy = s.y - origin.y, dz = s.z - origin.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz) <= radius;
  });
}

// ─── Component ────────────────────────────────────────────────────────────────

export function EntityViewer3D({ selected, allEntities, radius = 3, selectedTick = 0 }: Props) {
  const mountRef = useRef<HTMLDivElement>(null);
  const hoveredIdRef = useRef<string | null>(null);
  const meshMapRef = useRef<Map<string, MeshEntry>>(new Map());
  const entityByIdRef = useRef<Map<string, EntityData>>(new Map());
  const selectedTickRef = useRef(selectedTick);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [clickedId, setClickedId] = useState<string | null>(null);

  const nearby = getNearby(allEntities, selected, radius);
  const renderedEntities: Array<{ entity: EntityData; isSelected: boolean }> = [
    { entity: selected, isSelected: true },
    ...nearby.map(e => ({ entity: e, isSelected: false })),
  ];

  // Keep entity lookup map and selectedTick ref always current
  useEffect(() => {
    const m = new Map<string, EntityData>();
    for (const e of allEntities) m.set(e.id, e);
    m.set(selected.id, selected);
    entityByIdRef.current = m;
  }, [allEntities, selected]);

  useEffect(() => {
    selectedTickRef.current = selectedTick;
  }, [selectedTick]);

  useEffect(() => {
    hoveredIdRef.current = hoveredId;
  }, [hoveredId]);

  // ─── Tick effect — update mesh positions and transforms ─────────────────────
  useEffect(() => {
    const tick = selectedTick;
    const originSnap = getSnapshotAtTick(selected.timeline, tick) ?? selected.timeline[0] ?? { x: 0, y: 0, z: 0 };

    for (const [entityId, entry] of meshMapRef.current) {
      const entity = entityByIdRef.current.get(entityId);
      if (!entity) continue;

      const snap = getSnapshotAtTick(entity.timeline, tick) ?? entity.timeline[0];
      if (!snap) continue;

      const isSelected = entityId === selected.id;
      const rx = isSelected ? 0 : snap.x - originSnap.x;
      const ry = isSelected ? 0 : snap.y - originSnap.y;
      const rz = isSelected ? 0 : snap.z - originSnap.z;

      if (entry.isModel) {
        const tf = getTransformAtTick(entity.transformTimeline, tick);
        const mat = new THREE.Matrix4().makeTranslation(rx, ry, rz);
        if (tf) {
          const tr = tf.translation;
          if (tr?.length === 3)
            mat.multiply(new THREE.Matrix4().makeTranslation(tr[0], tr[1], tr[2]));
          const lr = tf.leftRotation;
          if (lr?.length === 4)
            mat.multiply(new THREE.Matrix4().makeRotationFromQuaternion(
              new THREE.Quaternion(lr[0], lr[1], lr[2], lr[3])));
          const sc = tf.scale;
          if (sc?.length === 3)
            mat.multiply(new THREE.Matrix4().makeScale(sc[0] || 1, sc[1] || 1, sc[2] || 1));
          const rr = tf.rightRotation;
          if (rr?.length === 4)
            mat.multiply(new THREE.Matrix4().makeRotationFromQuaternion(
              new THREE.Quaternion(rr[0], rr[1], rr[2], rr[3])));
        }
        entry.mesh.matrixAutoUpdate = false;
        entry.mesh.matrix.copy(mat);
        entry.mesh.matrixWorldNeedsUpdate = true;
      } else {
        const [, sh] = ENTITY_SIZE[entity.type] ?? DEFAULT_SIZE;
        entry.mesh.position.set(rx, ry + sh / 2, rz);
      }
    }
  }, [selectedTick, selected]);

  // ─── Main Three.js scene ─────────────────────────────────────────────────────
  useEffect(() => {
    const mount = mountRef.current;
    if (!mount) return;

    const w = mount.clientWidth;
    const h = mount.clientHeight || 340;

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setSize(w, h);
    renderer.setPixelRatio(window.devicePixelRatio);
    mount.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0d1117);

    const camera = new THREE.PerspectiveCamera(60, w / h, 0.1, 200);
    scene.add(new THREE.AmbientLight(0xffffff, 0.5));
    const dirLight = new THREE.DirectionalLight(0xffffff, 0.9);
    dirLight.position.set(5, 10, 7);
    scene.add(dirLight);

    const origin0 = selected.timeline[0] ?? { x: 0, y: 0, z: 0 };

    const meshMap = new Map<string, MeshEntry>();
    meshMapRef.current = meshMap;

    function makeBox(type: string, rx: number, ry: number, rz: number, color: number, opacity: number) {
      const [sw, sh, sd] = ENTITY_SIZE[type] ?? DEFAULT_SIZE;
      const geo = new THREE.BoxGeometry(sw, sh, sd);
      const mat = new THREE.MeshStandardMaterial({
        color, transparent: true, opacity, depthWrite: false,
      });
      const mesh = new THREE.Mesh(geo, mat);
      mesh.position.set(rx, ry + sh / 2, rz);
      const edges = new THREE.EdgesGeometry(geo);
      const lineMat = new THREE.LineBasicMaterial({ color });
      mesh.add(new THREE.LineSegments(edges, lineMat));
      return mesh;
    }

    const selMesh = makeBox(selected.type, 0, 0, 0, 0xffffff, 0.4);
    scene.add(selMesh);
    meshMap.set(selected.id, { mesh: selMesh, color: 0xffffff, baseOpacity: 0.4, isModel: false });

    nearby.forEach(e => {
      const snap = e.timeline[0]!;
      const rx = snap.x - origin0.x;
      const ry = snap.y - origin0.y;
      const rz = snap.z - origin0.z;
      const color = ENTITY_COLOR[e.type] ?? DEFAULT_COLOR;
      const mesh = makeBox(e.type, rx, ry, rz, color, 0.22);
      scene.add(mesh);
      meshMap.set(e.id, { mesh, color, baseOpacity: 0.22, isModel: false });
    });

    const grid = new THREE.GridHelper(radius * 2 + 2, radius * 2 + 2, 0x30363d, 0x21262d);
    scene.add(grid);

    // Async model loading — applies transforms at current tick via ref
    let destroyed = false;
    async function loadModels() {
      for (const { entity } of renderedEntities) {
        if (!entity.modelPath) continue;
        const pathPart = entity.modelPath.replace('modelengine:', '');
        const loaded = await loadMinecraftModel(pathPart);
        if (destroyed || !loaded) continue;

        const existing = meshMap.get(entity.id);
        if (existing) scene.remove(existing.mesh);

        const snap0 = entity.timeline[0];
        if (!snap0) continue;
        const rx = entity.id === selected.id ? 0 : snap0.x - origin0.x;
        const ry = entity.id === selected.id ? 0 : snap0.y - origin0.y;
        const rz = entity.id === selected.id ? 0 : snap0.z - origin0.z;

        const mesh = new THREE.Mesh(loaded.geometry, loaded.material);

        // Build transform matrix: world pos → T → LR → S → RR (Minecraft display entity order)
        const curTick = selectedTickRef.current;
        const tf = getTransformAtTick(entity.transformTimeline, curTick);
        const mat = new THREE.Matrix4().makeTranslation(rx, ry, rz);
        if (tf) {
          const tr = tf.translation;
          if (tr?.length === 3)
            mat.multiply(new THREE.Matrix4().makeTranslation(tr[0], tr[1], tr[2]));
          const lr = tf.leftRotation;
          if (lr?.length === 4)
            mat.multiply(new THREE.Matrix4().makeRotationFromQuaternion(new THREE.Quaternion(lr[0], lr[1], lr[2], lr[3])));
          const sc = tf.scale;
          if (sc?.length === 3)
            mat.multiply(new THREE.Matrix4().makeScale(sc[0] || 1, sc[1] || 1, sc[2] || 1));
          const rr = tf.rightRotation;
          if (rr?.length === 4)
            mat.multiply(new THREE.Matrix4().makeRotationFromQuaternion(new THREE.Quaternion(rr[0], rr[1], rr[2], rr[3])));
        }
        mesh.matrixAutoUpdate = false;
        mesh.matrix.copy(mat);
        mesh.matrixWorldNeedsUpdate = true;

        scene.add(mesh);
        const color = entity.id === selected.id ? 0xffffff : (ENTITY_COLOR[entity.type] ?? DEFAULT_COLOR);
        meshMap.set(entity.id, { mesh, color, baseOpacity: 1.0, isModel: true });
      }
    }
    loadModels();

    // Camera orbit
    const dist = radius * 2.2 + 3;
    let theta = 0.8, phi = 1.0, camDist = dist;
    function updateCamera() {
      camera.position.set(
        camDist * Math.sin(phi) * Math.sin(theta),
        camDist * Math.cos(phi),
        camDist * Math.sin(phi) * Math.cos(theta),
      );
      camera.lookAt(0, 1, 0);
    }
    updateCamera();

    const raycaster = new THREE.Raycaster();
    const pointer = new THREE.Vector2();

    let isDragging = false, lastX = 0, lastY = 0;
    const onMouseDown = (e: MouseEvent) => { isDragging = true; lastX = e.clientX; lastY = e.clientY; };
    const onMouseUp = () => { isDragging = false; };
    const onMouseMove = (e: MouseEvent) => {
      if (isDragging) {
        theta -= (e.clientX - lastX) * 0.01;
        phi = Math.max(0.1, Math.min(Math.PI * 0.9, phi + (e.clientY - lastY) * 0.01));
        lastX = e.clientX; lastY = e.clientY;
        updateCamera();
        return;
      }
      const rect = renderer.domElement.getBoundingClientRect();
      pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
      pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
      raycaster.setFromCamera(pointer, camera);
      const hits = raycaster.intersectObjects([...meshMap.values()].map(e => e.mesh));
      if (hits.length > 0) {
        const hitMesh = hits[0].object;
        for (const [id, entry] of meshMap) {
          if (entry.mesh === hitMesh) {
            if (hoveredIdRef.current !== id) { hoveredIdRef.current = id; setHoveredId(id); }
            return;
          }
        }
      }
      if (hoveredIdRef.current !== null) { hoveredIdRef.current = null; setHoveredId(null); }
    };
    const onWheel = (e: WheelEvent) => {
      camDist = Math.max(2, Math.min(40, camDist + e.deltaY * 0.02));
      updateCamera();
      e.preventDefault();
    };

    renderer.domElement.addEventListener('mousedown', onMouseDown);
    renderer.domElement.addEventListener('mousemove', onMouseMove);
    renderer.domElement.addEventListener('wheel', onWheel, { passive: false });
    window.addEventListener('mouseup', onMouseUp);

    let animId: number;
    const animate = () => {
      animId = requestAnimationFrame(animate);
      const hid = hoveredIdRef.current;
      for (const [id, entry] of meshMap) {
        const mat = entry.mesh.material as THREE.MeshStandardMaterial;
        const isHovered = id === hid;
        mat.opacity = isHovered ? Math.min(entry.baseOpacity + 0.45, 0.9) : entry.baseOpacity;
        mat.emissive?.setHex(isHovered ? entry.color : 0x000000);
        mat.emissiveIntensity = isHovered ? 0.4 : 0;
        const wire = entry.mesh.children[0] as THREE.LineSegments;
        if (wire) {
          (wire.material as THREE.LineBasicMaterial).color.setHex(isHovered ? 0xffffff : entry.color);
        }
      }
      renderer.render(scene, camera);
    };
    animate();

    return () => {
      destroyed = true;
      cancelAnimationFrame(animId);
      renderer.domElement.removeEventListener('mousedown', onMouseDown);
      renderer.domElement.removeEventListener('mousemove', onMouseMove);
      renderer.domElement.removeEventListener('wheel', onWheel);
      window.removeEventListener('mouseup', onMouseUp);
      renderer.dispose();
      if (mount.contains(renderer.domElement)) mount.removeChild(renderer.domElement);
    };
  }, [selected, allEntities, radius]); // eslint-disable-line react-hooks/exhaustive-deps

  const clickedEntity = clickedId ? renderedEntities.find(r => r.entity.id === clickedId)?.entity : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
    <div style={{ display: 'flex', gap: '10px', alignItems: 'flex-start' }}>
      {/* Canvas */}
      <div style={{ position: 'relative', flex: '1 1 auto', minWidth: 0 }}>
        <div ref={mountRef} style={{ width: '100%', height: '340px', borderRadius: '6px', overflow: 'hidden' }} />
        <div style={{
          position: 'absolute', bottom: 8, left: 8,
          fontSize: '11px', color: '#8b949e', pointerEvents: 'none',
        }}>
          Drag to orbit · Scroll to zoom
        </div>
      </div>

      {/* Entity list */}
      <div style={{
        width: '200px', flexShrink: 0,
        background: '#161b22', border: '1px solid #30363d',
        borderRadius: '6px', overflow: 'hidden',
        maxHeight: '340px', overflowY: 'auto',
      }}>
        <div style={{ padding: '6px 10px', borderBottom: '1px solid #30363d', fontSize: '11px', color: '#8b949e', fontWeight: 600 }}>
          {renderedEntities.length} entities
        </div>
        {renderedEntities.map(({ entity, isSelected }) => {
          const isHovered = hoveredId === entity.id;
          const color = isSelected ? '#ffffff' : colorHex(ENTITY_COLOR[entity.type] ?? DEFAULT_COLOR);
          return (
            <div
              key={entity.id}
              onMouseEnter={() => setHoveredId(entity.id)}
              onMouseLeave={() => setHoveredId(null)}
              onClick={() => setClickedId(id => id === entity.id ? null : entity.id)}
              style={{
                padding: '5px 10px',
                cursor: 'default',
                background: isHovered ? '#1f2937' : 'transparent',
                borderLeft: `3px solid ${isHovered ? color : 'transparent'}`,
                transition: 'background 0.1s',
              }}
            >
              <div style={{ fontSize: '11px', color, fontWeight: isSelected ? 700 : 400 }}>
                {isSelected ? '★ ' : ''}{entity.type}
              </div>
              <div style={{ fontSize: '10px', color: '#8b949e', fontFamily: 'monospace' }}>
                {entity.id}
              </div>
              {entity.modelId && (
                <div style={{ fontSize: '10px', color: '#f0883e', marginTop: '1px' }}>
                  {entity.modelId}
                </div>
              )}
              {entity.groupId && (
                <div style={{ fontSize: '10px', color: '#79c0ff', marginTop: '1px' }}>
                  {entity.groupId}
                </div>
              )}
              {hasTransform(entity.translation) && (
                <div style={{ fontSize: '9px', color: '#8b949e', fontFamily: 'monospace', marginTop: '2px' }}>
                  T {fmt3(entity.translation)}
                </div>
              )}
              {hasTransform(entity.scale) && (
                <div style={{ fontSize: '9px', color: '#8b949e', fontFamily: 'monospace' }}>
                  S {fmt3(entity.scale)}
                </div>
              )}
              {hasTransform(entity.leftRotation) && (
                <div style={{ fontSize: '9px', color: '#8b949e', fontFamily: 'monospace' }}>
                  LR {fmt4(entity.leftRotation)}
                </div>
              )}
              {hasTransform(entity.rightRotation) && (
                <div style={{ fontSize: '9px', color: '#8b949e', fontFamily: 'monospace' }}>
                  RR {fmt4(entity.rightRotation)}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>

    {/* JSON panel */}
    {clickedEntity && (
      <div style={{
        background: '#0d1117', border: '1px solid #30363d',
        borderRadius: '6px', overflow: 'hidden',
      }}>
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          padding: '6px 10px', borderBottom: '1px solid #30363d',
          fontSize: '11px', color: '#8b949e', fontWeight: 600,
        }}>
          <span>{clickedEntity.type} · {clickedEntity.id}</span>
          <span
            onClick={() => setClickedId(null)}
            style={{ cursor: 'pointer', color: '#6e7681', fontSize: '13px', lineHeight: 1 }}
          >✕</span>
        </div>
        <pre style={{
          margin: 0, padding: '10px', fontSize: '11px', color: '#c9d1d9',
          fontFamily: 'monospace', overflowX: 'auto', maxHeight: '300px', overflowY: 'auto',
        }}>
          {JSON.stringify({
            ...clickedEntity,
            timeline: `[${clickedEntity.timeline.length} snapshots]`,
            transformTimeline: clickedEntity.transformTimeline
              ? `[${clickedEntity.transformTimeline.length} snapshots]`
              : undefined,
          }, null, 2)}
        </pre>
      </div>
    )}
  </div>
  );
}

function colorHex(n: number): string {
  return '#' + n.toString(16).padStart(6, '0');
}

function hasTransform(arr: number[] | undefined): boolean {
  return !!arr && arr.some(v => v !== 0);
}

function fmt3(arr: number[]): string {
  return arr.slice(0, 3).map(v => v.toFixed(2)).join(', ');
}

function fmt4(arr: number[]): string {
  return arr.slice(0, 4).map(v => v.toFixed(2)).join(', ');
}
