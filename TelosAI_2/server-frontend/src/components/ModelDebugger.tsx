import { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import { loadMinecraftModel } from '../utils/minecraftModelLoader';

// ─── Helpers ──────────────────────────────────────────────────────────────────

function eulerToQuat(degX: number, degY: number, degZ: number): THREE.Quaternion {
  return new THREE.Quaternion().setFromEuler(
    new THREE.Euler(
      THREE.MathUtils.degToRad(degX),
      THREE.MathUtils.degToRad(degY),
      THREE.MathUtils.degToRad(degZ),
      'XYZ',
    ),
  );
}

function quatToEulerDeg(q: THREE.Quaternion): [number, number, number] {
  const e = new THREE.Euler().setFromQuaternion(q, 'XYZ');
  return [
    THREE.MathUtils.radToDeg(e.x),
    THREE.MathUtils.radToDeg(e.y),
    THREE.MathUtils.radToDeg(e.z),
  ];
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function SliderRow({
  label, value, min, max, step, color, onChange,
}: {
  label: string; value: number; min: number; max: number; step: number;
  color: string; onChange: (v: number) => void;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '5px' }}>
      <span style={{ fontSize: '11px', color, width: '40px', fontFamily: 'monospace', fontWeight: 600 }}>
        {label}
      </span>
      <input
        type="range" min={min} max={max} step={step} value={value}
        onChange={e => onChange(parseFloat(e.target.value))}
        style={{ flex: 1, accentColor: color }}
      />
      <input
        type="number" value={value.toFixed(3)} step={step}
        onChange={e => { const v = parseFloat(e.target.value); if (!isNaN(v)) onChange(v); }}
        style={{
          width: '72px', background: '#0d1117', border: '1px solid #30363d',
          color: '#e6edf3', padding: '2px 5px', borderRadius: '4px',
          fontSize: '11px', fontFamily: 'monospace',
        }}
      />
    </div>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

interface Rot { x: number; y: number; z: number }

export function ModelDebugger({ onClose }: { onClose: () => void }) {
  const mountRef = useRef<HTMLDivElement>(null);
  const sceneRef = useRef<THREE.Scene | null>(null);
  const meshRef = useRef<THREE.Mesh | null>(null);
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null);
  const cameraRef = useRef<THREE.PerspectiveCamera | null>(null);
  const distRef = useRef(3);
  const orbitRef = useRef({ theta: 0.6, phi: 1.1 });

  const [modelInput, setModelInput] = useState('');
  const [loadedPath, setLoadedPath] = useState<string | null>(null);
  const [loadError, setLoadError] = useState('');
  const [loading, setLoading] = useState(false);

  const [tx, setTx] = useState(0); const [ty, setTy] = useState(0); const [tz, setTz] = useState(0);
  const [sx, setSx] = useState(1); const [sy, setSy] = useState(1); const [sz, setSz] = useState(1);
  const [uniformScale, setUniformScale] = useState(false);
  const [lr, setLr] = useState<Rot>({ x: 0, y: 0, z: 0 });
  const [rr, setRr] = useState<Rot>({ x: 0, y: 0, z: 0 });

  // Paste raw quaternion states
  const [lrRaw, setLrRaw] = useState('');
  const [rrRaw, setRrRaw] = useState('');

  // ─── Computed quaternions for display ───────────────────────────────────────
  const lqDisplay = eulerToQuat(lr.x, lr.y, lr.z);
  const rqDisplay = eulerToQuat(rr.x, rr.y, rr.z);

  // ─── Three.js scene init ─────────────────────────────────────────────────────
  useEffect(() => {
    const mount = mountRef.current;
    if (!mount) return;

    const w = mount.clientWidth || 480;
    const h = 420;

    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(w, h);
    renderer.setPixelRatio(window.devicePixelRatio);
    mount.appendChild(renderer.domElement);
    rendererRef.current = renderer;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0d1117);
    sceneRef.current = scene;

    const camera = new THREE.PerspectiveCamera(60, w / h, 0.01, 200);
    cameraRef.current = camera;

    scene.add(new THREE.AmbientLight(0xffffff, 0.6));
    const dir = new THREE.DirectionalLight(0xffffff, 0.9);
    dir.position.set(5, 10, 7);
    scene.add(dir);
    scene.add(new THREE.GridHelper(4, 8, 0x30363d, 0x21262d));
    scene.add(new THREE.AxesHelper(0.5));

    function updateCamera() {
      const { theta, phi } = orbitRef.current;
      const d = distRef.current;
      camera.position.set(
        d * Math.sin(phi) * Math.sin(theta),
        d * Math.cos(phi),
        d * Math.sin(phi) * Math.cos(theta),
      );
      camera.lookAt(0, 0.5, 0);
    }
    updateCamera();

    let dragging = false, lastX = 0, lastY = 0;
    const onDown = (e: MouseEvent) => { dragging = true; lastX = e.clientX; lastY = e.clientY; };
    const onUp = () => { dragging = false; };
    const onMove = (e: MouseEvent) => {
      if (!dragging) return;
      orbitRef.current.theta -= (e.clientX - lastX) * 0.01;
      orbitRef.current.phi = Math.max(0.1, Math.min(Math.PI * 0.9, orbitRef.current.phi + (e.clientY - lastY) * 0.01));
      lastX = e.clientX; lastY = e.clientY;
      updateCamera();
    };
    const onWheel = (e: WheelEvent) => {
      distRef.current = Math.max(0.5, Math.min(20, distRef.current + e.deltaY * 0.01));
      updateCamera();
      e.preventDefault();
    };

    renderer.domElement.addEventListener('mousedown', onDown);
    renderer.domElement.addEventListener('mousemove', onMove);
    renderer.domElement.addEventListener('wheel', onWheel, { passive: false });
    window.addEventListener('mouseup', onUp);

    let animId: number;
    const animate = () => { animId = requestAnimationFrame(animate); renderer.render(scene, camera); };
    animate();

    return () => {
      cancelAnimationFrame(animId);
      renderer.domElement.removeEventListener('mousedown', onDown);
      renderer.domElement.removeEventListener('mousemove', onMove);
      renderer.domElement.removeEventListener('wheel', onWheel);
      window.removeEventListener('mouseup', onUp);
      renderer.dispose();
      if (mount.contains(renderer.domElement)) mount.removeChild(renderer.domElement);
    };
  }, []);

  // ─── Apply transforms to mesh ─────────────────────────────────────────────
  useEffect(() => {
    const mesh = meshRef.current;
    if (!mesh) return;
    mesh.position.set(tx, ty, tz);
    mesh.scale.set(sx, sy, sz);
    const lq = eulerToQuat(lr.x, lr.y, lr.z);
    const rq = eulerToQuat(rr.x, rr.y, rr.z);
    mesh.quaternion.multiplyQuaternions(lq, rq);
  }, [tx, ty, tz, sx, sy, sz, lr, rr]);

  // ─── Load model ────────────────────────────────────────────────────────────
  async function handleLoad() {
    const scene = sceneRef.current;
    if (!scene || !modelInput.trim()) return;

    setLoading(true);
    setLoadError('');

    if (meshRef.current) { scene.remove(meshRef.current); meshRef.current = null; }

    const path = modelInput.trim().replace('modelengine:', '');
    const loaded = await loadMinecraftModel(path);

    if (!loaded) {
      setLoadError(`Not found: ${path}`);
      setLoading(false);
      return;
    }

    const mesh = new THREE.Mesh(loaded.geometry, loaded.material);
    mesh.position.set(tx, ty, tz);
    mesh.scale.set(sx, sy, sz);
    const lq = eulerToQuat(lr.x, lr.y, lr.z);
    const rq = eulerToQuat(rr.x, rr.y, rr.z);
    mesh.quaternion.multiplyQuaternions(lq, rq);
    meshRef.current = mesh;
    scene.add(mesh);
    setLoadedPath(path);
    setLoading(false);
  }

  function resetAll() {
    setTx(0); setTy(0); setTz(0);
    setSx(1); setSy(1); setSz(1);
    setLr({ x: 0, y: 0, z: 0 });
    setRr({ x: 0, y: 0, z: 0 });
  }

  function setScale(v: number) {
    if (uniformScale) { setSx(v); setSy(v); setSz(v); }
  }

  // Paste raw quaternion (x,y,z,w) into euler sliders
  function applyRawQuat(raw: string, setter: (r: Rot) => void) {
    const parts = raw.split(',').map(s => parseFloat(s.trim()));
    if (parts.length !== 4 || parts.some(isNaN)) return;
    const q = new THREE.Quaternion(parts[0], parts[1], parts[2], parts[3]).normalize();
    const [ex, ey, ez] = quatToEulerDeg(q);
    setter({ x: ex, y: ey, z: ez });
  }

  // ─── Render ────────────────────────────────────────────────────────────────
  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      zIndex: 1000,
    }}>
      <div style={{
        background: '#161b22', border: '1px solid #30363d', borderRadius: '10px',
        width: '900px', maxWidth: '97vw', maxHeight: '96vh', overflow: 'auto',
        padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px',
      }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: '14px', fontWeight: 700, color: '#e6edf3' }}>
            Model Transform Debugger
          </span>
          <button onClick={onClose} style={{
            background: 'none', border: '1px solid #30363d', color: '#8b949e',
            borderRadius: '4px', padding: '2px 10px', cursor: 'pointer', fontSize: '12px',
          }}>✕ Close</button>
        </div>

        {/* Model path input */}
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <input
            value={modelInput}
            onChange={e => setModelInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleLoad()}
            placeholder="e.g. wumpus/head_1 or modelengine:wumpus/head_1"
            style={{
              flex: 1, background: '#0d1117', border: '1px solid #30363d',
              color: '#e6edf3', padding: '5px 10px', borderRadius: '5px', fontSize: '12px',
            }}
          />
          <button onClick={handleLoad} disabled={loading} style={{
            background: '#1f6feb', border: 'none', color: '#fff',
            padding: '5px 14px', borderRadius: '5px', cursor: 'pointer', fontSize: '12px',
          }}>
            {loading ? 'Loading…' : 'Load'}
          </button>
          <button onClick={resetAll} style={{
            background: '#21262d', border: '1px solid #30363d', color: '#8b949e',
            padding: '5px 10px', borderRadius: '5px', cursor: 'pointer', fontSize: '12px',
          }}>Reset All</button>
        </div>
        {loadedPath && !loadError && (
          <div style={{ fontSize: '11px', color: '#3fb950' }}>Loaded: {loadedPath}</div>
        )}
        {loadError && <div style={{ fontSize: '11px', color: '#f85149' }}>{loadError}</div>}

        {/* Main layout: canvas + controls */}
        <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>

          {/* Canvas */}
          <div style={{ flex: '0 0 480px', minWidth: 0 }}>
            <div ref={mountRef} style={{ width: '100%', borderRadius: '6px', overflow: 'hidden' }} />
            <div style={{ fontSize: '10px', color: '#484f58', marginTop: '4px', textAlign: 'center' }}>
              Drag to orbit · Scroll to zoom · Red=X Green=Y Blue=Z
            </div>
          </div>

          {/* Controls */}
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '10px' }}>

            {/* Translation */}
            <Section label="Translation">
              <SliderRow label="X" value={tx} min={-3} max={3} step={0.01} color="#f85149" onChange={setTx} />
              <SliderRow label="Y" value={ty} min={-3} max={3} step={0.01} color="#3fb950" onChange={setTy} />
              <SliderRow label="Z" value={tz} min={-3} max={3} step={0.01} color="#58a6ff" onChange={setTz} />
            </Section>

            {/* Scale */}
            <Section label="Scale">
              <label style={{ fontSize: '11px', color: '#8b949e', display: 'flex', alignItems: 'center', gap: '5px', marginBottom: '4px' }}>
                <input type="checkbox" checked={uniformScale} onChange={e => setUniformScale(e.target.checked)} />
                Uniform
              </label>
              {uniformScale ? (
                <SliderRow label="All" value={sx} min={0} max={4} step={0.01} color="#d29922"
                  onChange={v => { setSx(v); setSy(v); setSz(v); setScale(v); }} />
              ) : (
                <>
                  <SliderRow label="X" value={sx} min={0} max={4} step={0.01} color="#f85149" onChange={setSx} />
                  <SliderRow label="Y" value={sy} min={0} max={4} step={0.01} color="#3fb950" onChange={setSy} />
                  <SliderRow label="Z" value={sz} min={0} max={4} step={0.01} color="#58a6ff" onChange={setSz} />
                </>
              )}
            </Section>

            {/* Left Rotation */}
            <Section label="Left Rotation (Euler°)">
              <SliderRow label="Pitch" value={lr.x} min={-180} max={180} step={0.5} color="#f85149"
                onChange={v => setLr(p => ({ ...p, x: v }))} />
              <SliderRow label="Yaw" value={lr.y} min={-180} max={180} step={0.5} color="#3fb950"
                onChange={v => setLr(p => ({ ...p, y: v }))} />
              <SliderRow label="Roll" value={lr.z} min={-180} max={180} step={0.5} color="#58a6ff"
                onChange={v => setLr(p => ({ ...p, z: v }))} />
              <div style={{ fontSize: '10px', color: '#484f58', fontFamily: 'monospace', marginBottom: '4px' }}>
                → q({lqDisplay.x.toFixed(3)}, {lqDisplay.y.toFixed(3)}, {lqDisplay.z.toFixed(3)}, {lqDisplay.w.toFixed(3)})
              </div>
              <div style={{ display: 'flex', gap: '4px' }}>
                <input value={lrRaw} onChange={e => setLrRaw(e.target.value)} placeholder="paste x,y,z,w"
                  style={{ flex: 1, background: '#0d1117', border: '1px solid #30363d', color: '#e6edf3', padding: '2px 6px', borderRadius: '4px', fontSize: '10px', fontFamily: 'monospace' }} />
                <button onClick={() => applyRawQuat(lrRaw, setLr)} style={pasteBtn}>Apply</button>
                <button onClick={() => setLr({ x: 0, y: 0, z: 0 })} style={pasteBtn}>Reset</button>
              </div>
            </Section>

            {/* Right Rotation */}
            <Section label="Right Rotation (Euler°)">
              <SliderRow label="Pitch" value={rr.x} min={-180} max={180} step={0.5} color="#f85149"
                onChange={v => setRr(p => ({ ...p, x: v }))} />
              <SliderRow label="Yaw" value={rr.y} min={-180} max={180} step={0.5} color="#3fb950"
                onChange={v => setRr(p => ({ ...p, y: v }))} />
              <SliderRow label="Roll" value={rr.z} min={-180} max={180} step={0.5} color="#58a6ff"
                onChange={v => setRr(p => ({ ...p, z: v }))} />
              <div style={{ fontSize: '10px', color: '#484f58', fontFamily: 'monospace', marginBottom: '4px' }}>
                → q({rqDisplay.x.toFixed(3)}, {rqDisplay.y.toFixed(3)}, {rqDisplay.z.toFixed(3)}, {rqDisplay.w.toFixed(3)})
              </div>
              <div style={{ display: 'flex', gap: '4px' }}>
                <input value={rrRaw} onChange={e => setRrRaw(e.target.value)} placeholder="paste x,y,z,w"
                  style={{ flex: 1, background: '#0d1117', border: '1px solid #30363d', color: '#e6edf3', padding: '2px 6px', borderRadius: '4px', fontSize: '10px', fontFamily: 'monospace' }} />
                <button onClick={() => applyRawQuat(rrRaw, setRr)} style={pasteBtn}>Apply</button>
                <button onClick={() => setRr({ x: 0, y: 0, z: 0 })} style={pasteBtn}>Reset</button>
              </div>
            </Section>

            {/* Copy current values */}
            <button
              onClick={() => {
                const lq = eulerToQuat(lr.x, lr.y, lr.z);
                const rq = eulerToQuat(rr.x, rr.y, rr.z);
                const out = `translation: [${tx.toFixed(4)}, ${ty.toFixed(4)}, ${tz.toFixed(4)}]\nscale:       [${sx.toFixed(4)}, ${sy.toFixed(4)}, ${sz.toFixed(4)}]\nleftRot(q):  [${lq.x.toFixed(4)}, ${lq.y.toFixed(4)}, ${lq.z.toFixed(4)}, ${lq.w.toFixed(4)}]\nrightRot(q): [${rq.x.toFixed(4)}, ${rq.y.toFixed(4)}, ${rq.z.toFixed(4)}, ${rq.w.toFixed(4)}]`;
                console.log('=== ModelDebugger values ===\n' + out);
                navigator.clipboard?.writeText(out);
              }}
              style={{
                background: '#21262d', border: '1px solid #30363d', color: '#8b949e',
                padding: '5px 10px', borderRadius: '5px', cursor: 'pointer', fontSize: '11px',
                textAlign: 'left',
              }}
            >
              📋 Copy values to clipboard / console
            </button>

          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Tiny helpers ──────────────────────────────────────────────────────────────

const pasteBtn: React.CSSProperties = {
  background: '#21262d', border: '1px solid #30363d', color: '#8b949e',
  padding: '2px 7px', borderRadius: '4px', cursor: 'pointer', fontSize: '10px',
};

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ background: '#0d1117', border: '1px solid #21262d', borderRadius: '6px', padding: '8px 10px' }}>
      <div style={{ fontSize: '11px', fontWeight: 700, color: '#8b949e', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        {label}
      </div>
      {children}
    </div>
  );
}
