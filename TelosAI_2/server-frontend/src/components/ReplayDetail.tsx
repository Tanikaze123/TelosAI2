import { useState, useEffect, useMemo } from 'react';
import { replayApi } from '../services/api';
import type { ReplayRecord, EntityData } from '../types/replay';
import { EntityViewer3D } from './EntityViewer3D';
import { buildEntityTimelines, type EntityState } from '../utils/tickUtils';

interface Props {
  replayId: string;
  onClose?: () => void;
}

export function ReplayDetail({ replayId, onClose }: Props) {
  const [record, setRecord] = useState<ReplayRecord | null>(null);       // full replay data from the API
  const [loading, setLoading] = useState(true);                          // waiting on API response
  const [error, setError] = useState<string | null>(null);               // API error message
  const [typeFilter, setTypeFilter] = useState<string>('all');           // entity type filter on site
  const [modelFilter, setModelFilter] = useState<boolean>(false);        // filter show only entities with a ModelEngine model
  const [selectedEntity, setSelectedEntity] = useState<EntityData | null>(null); // entity row clicked in the table
  const [reparsing, setReparsing] = useState(false);                     // reparse button in-flight
  const [sortByGroup, setSortByGroup] = useState(false);                 // group entities by groupId in the table
  const [selectedTick, setSelectedTick] = useState(0);                   // current tick shown in the slider / 3D viewer
  const ticks = useMemo(() => record?.parsedData?.ticks ?? [], [record]) // raw tick-change array from parsed replay
  const entityTimelines = useMemo(() => buildEntityTimelines(ticks), [ticks]) // per-entity state history built from ticks
  const entityStats = useMemo(() => {
    const stats = new Map<string, {
      firstTick: number,
      lastTick: number, 
      count: number, 
      spawnX?: number, 
      spawnY?: number, 
      spawnZ?: number 
    }>();
    if (!entityTimelines) return stats;
    for (const [id, tl] of entityTimelines) {
      const firstPos = tl.find(s => s.x !== undefined)
      stats.set(id, {
        firstTick: tl[0].tick,
        lastTick: tl[tl.length - 1].tick,
        count: tl.length,
        spawnX: firstPos?.x,
        spawnY: firstPos?.y,
        spawnZ: firstPos?.z
      })
    }
    return stats
  }, [entityTimelines])

  useEffect(() => {
    loadReplay();
  }, [replayId]);

  // Reset tick to the entity's first tick whenever a new entity is selected
  useEffect(() => {
    if (selectedEntity) {
      setSelectedTick(entityTimelines?.get(selectedEntity.id)?.[0].tick ?? 0);
    }
  }, [selectedEntity?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleReparse = async () => {
    setReparsing(true);
    try {
      const result = await replayApi.reparse(replayId);
      alert(`Reparse complete: ${result.entityCount} entities, ${result.modelCount} with model`);
      await loadReplay();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Reparse failed');
    } finally {
      setReparsing(false);
    }
  };

  const loadReplay = async () => {
    setLoading(true);
    setError(null);
    setSelectedEntity(null);

    try {
      const data = await replayApi.getById(replayId);
      setRecord(data as unknown as ReplayRecord);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load replay');
    } finally {
      setLoading(false);
    }
  };

  // Extract entities from parsedData
  const entities = useMemo(() => {
    return record?.parsedData?.entities || [];
  }, [record]);

  // Get unique entity types for filter
  const entityTypes = useMemo(() => {
    const types = new Set(entities.map(e => e.type));
    return Array.from(types).sort();
  }, [entities]);

  // Count by type
  const typeCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    entities.forEach(e => {
      counts[e.type] = (counts[e.type] || 0) + 1;
    });
    return counts;
  }, [entities]);

  // Filtered entities
  const filteredEntities = useMemo(() => {
    let result = typeFilter === 'all' ? entities : entities.filter(e => e.type === typeFilter);
    if (modelFilter) result = result.filter(e => !!e.modelId);
    if (sortByGroup) {
      result = [...result].sort((a, b) => {
        const ag = a.groupId ?? '';
        const bg = b.groupId ?? '';
        if (ag && bg) return ag.localeCompare(bg) || a.id.localeCompare(b.id);
        if (ag) return -1;
        if (bg) return 1;
        return a.id.localeCompare(b.id);
      });
    }
    return result;
  }, [entities, typeFilter, modelFilter, sortByGroup]);

  const metadata = record?.parsedData?.metadata || record?.metadata;

  if (loading) return <p>Loading replay details...</p>;
  if (error) return <p style={{ color: '#f85149' }}>Error: {error}</p>;
  if (!record) return <p>Replay not found</p>;

  return (
    <div style={{
      padding: '20px',
      border: '1px solid #30363d',
      borderRadius: '8px',
    }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>{record.filename}</h3>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button onClick={handleReparse} disabled={reparsing} style={{
            background: '#1f6feb',
            border: '1px solid #388bfd',
            color: '#fff',
            padding: '6px 16px',
            borderRadius: '6px',
            cursor: reparsing ? 'not-allowed' : 'pointer',
            opacity: reparsing ? 0.6 : 1,
          }}>{reparsing ? 'Reparsing...' : 'Reparse'}</button>
          {onClose && (
            <button onClick={onClose} style={{
              background: '#21262d',
              border: '1px solid #30363d',
              color: '#c9d1d9',
              padding: '6px 16px',
              borderRadius: '6px',
              cursor: 'pointer'
            }}>Close</button>
          )}
        </div>
      </div>

      {/* Metadata */}
      <div style={{ marginTop: '16px' }}>
        <h4>Metadata</h4>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <tbody>
            {[
              ['Duration', metadata?.duration ? `${metadata.duration} ticks (${Math.floor(metadata.duration / 20)}s)` : 'N/A'],
              ['Minecraft Version', metadata?.mcVersion || record.mcVersion],
              ['Server', metadata?.serverName || record.serverName],
              ['Recorded At', record.recordedAt ? new Date(record.recordedAt).toLocaleString() : 'N/A'],
              ['Protocol Version', metadata?.protocolVersion?.toString() || 'N/A'],
              ['Parse Status', record.parseStatus],
            ].map(([label, value]) => (
              <tr key={label}>
                <td style={{ padding: '4px 8px', fontWeight: 'bold', color: '#8b949e', width: '160px' }}>{label}:</td>
                <td style={{ padding: '4px 8px' }}>{value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Entity Summary */}
      <div style={{ marginTop: '20px' }}>
        <h4>Entities ({entities.length} total)</h4>

        {entities.length === 0 ? (
          <p style={{
            padding: '10px',
            backgroundColor: '#2a1d00',
            borderRadius: '4px',
            border: '1px solid #d29922',
            color: '#e3b341'
          }}>
            No entities found in parsed data.
          </p>
        ) : (
          <>
            {/* Type breakdown */}
            <div style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: '8px',
              marginBottom: '12px',
            }}>
              <button
                onClick={() => setTypeFilter('all')}
                style={{
                  padding: '4px 12px',
                  borderRadius: '16px',
                  border: '1px solid #30363d',
                  background: typeFilter === 'all' ? '#238636' : '#21262d',
                  color: '#c9d1d9',
                  cursor: 'pointer',
                  fontSize: '12px',
                }}
              >
                All ({entities.length})
              </button>
              <button
                onClick={() => setModelFilter(f => !f)}
                style={{
                  padding: '4px 12px',
                  borderRadius: '16px',
                  border: '1px solid #30363d',
                  background: modelFilter ? '#6e40c9' : '#21262d',
                  color: '#c9d1d9',
                  cursor: 'pointer',
                  fontSize: '12px',
                }}
              >
                Has Model ({entities.filter(e => !!e.modelId).length})
              </button>
              <button
                onClick={() => setSortByGroup(f => !f)}
                style={{
                  padding: '4px 12px',
                  borderRadius: '16px',
                  border: '1px solid #30363d',
                  background: sortByGroup ? '#d29922' : '#21262d',
                  color: '#c9d1d9',
                  cursor: 'pointer',
                  fontSize: '12px',
                }}
              >
                Sort by Group
              </button>
              {entityTypes.map(type => (
                <button
                  key={type}
                  onClick={() => setTypeFilter(type)}
                  style={{
                    padding: '4px 12px',
                    borderRadius: '16px',
                    border: '1px solid #30363d',
                    background: typeFilter === type ? '#238636' : '#21262d',
                    color: '#c9d1d9',
                    cursor: 'pointer',
                    fontSize: '12px',
                  }}
                >
                  {type} ({typeCounts[type]})
                </button>
              ))}
            </div>

            {/* Entity table */}
            <div style={{ maxHeight: '400px', overflow: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid #30363d', position: 'sticky', top: 0, background: '#161b22' }}>
                    <th style={thStyle}>ID</th>
                    <th style={thStyle}>Type</th>
                    <th style={thStyle}>Model</th>
                    <th style={thStyle}>Group</th>
                    <th style={thStyle}>Snapshots</th>
                    <th style={thStyle}>First Tick</th>
                    <th style={thStyle}>Last Tick</th>
                    <th style={thStyle}>Spawn Position</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredEntities.map(entity => {
                    const stat = entityStats.get(entity.id);
                    return (
                      <tr
                        key={entity.id}
                        onClick={() => setSelectedEntity(selectedEntity?.id === entity.id ? null : entity)}
                        style={{
                          borderBottom: '1px solid #21262d',
                          cursor: 'pointer',
                          background: selectedEntity?.id === entity.id ? '#1f6feb22' : 'transparent',
                        }}
                      >
                        <td style={tdStyle}>{entity.id}</td>
                        <td style={tdStyle}>
                          <span style={{
                            padding: '2px 8px',
                            borderRadius: '12px',
                            background: getTypeColor(entity.type),
                            fontSize: '11px',
                          }}>
                            {entity.type}
                          </span>
                        </td>
                        <td style={tdStyle}>
                          {entity.modelPath
                            ? <span style={{ fontFamily: 'monospace', color: '#f0883e', fontSize: '11px' }}>{entity.modelPath}</span>
                            : entity.modelId
                              ? <span style={{ color: '#f0883e', fontSize: '11px' }}>{entity.modelId}</span>
                              : '-'}
                        </td>
                        <td style={tdStyle}>
                          {entity.groupId
                            ? <span style={{ fontFamily: 'monospace', color: '#79c0ff', fontSize: '11px' }}>{entity.groupId}</span>
                            : '-'}
                        </td>
                        <td style={tdStyle}>{stat?.count}</td>
                        <td style={tdStyle}>{stat?.firstTick ?? '-'}</td>
                        <td style={tdStyle}>{stat?.lastTick ?? '-'}</td>
                        <td style={tdStyle}>
                          {stat?.firstTick ? `${stat.spawnX?.toFixed(1)}, ${stat.spawnY?.toFixed(1)}, ${stat.spawnZ?.toFixed(1)}` : '-'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>

      {/* Selected Entity Detail */}
      {selectedEntity && (
        <div style={{
          marginTop: '16px',
          padding: '12px',
          border: '1px solid #1f6feb',
          borderRadius: '6px',
          background: '#0d1117',
        }}>
          <h4 style={{ margin: '0 0 8px 0' }}>
            {selectedEntity.type} - {selectedEntity.id}
            <span style={{ fontWeight: 'normal', color: '#8b949e', fontSize: '12px', marginLeft: '8px' }}>
              UUID: {selectedEntity.uuid}
            </span>
          </h4>

          {selectedEntity.modelId && (
            <p style={{ margin: '4px 0', fontSize: '13px', color: '#f0883e' }}>
              Model: {selectedEntity.modelId}
              {selectedEntity.modelPath && (
                <span style={{ fontFamily: 'monospace', color: '#d2a8ff', marginLeft: '8px', fontSize: '12px' }}>
                  ({selectedEntity.modelPath})
                </span>
              )}
            </p>
          )}
          {selectedEntity.groupId && (
            <p style={{ margin: '4px 0', fontSize: '13px', color: '#79c0ff' }}>
              Group: {selectedEntity.groupId}
            </p>
          )}

          {selectedEntity.modelId && (() => {
            const tl = entityTimelines?.get(selectedEntity.id);
            // Find the keyframe at/before selectedTick
            let kfIdx = -1;
            if (tl?.length) {
              for (let i = 0; i < tl.length; i++) {
                if (tl[i].tick <= selectedTick) kfIdx = i; else break;
              }
            }
            const tf   = kfIdx >= 0 && tl ? tl[kfIdx]     : null;
            const prev = kfIdx >  0 && tl ? tl[kfIdx - 1] : null;
            // Use tick-based values if available, else fall back to static entity fields
            const T  = tf?.translation  ?? selectedEntity.translation;
            const S  = tf?.scale        ?? selectedEntity.scale;
            const LR = tf?.leftRotation ?? selectedEntity.leftRotation;
            const RR = tf?.rightRotation ?? selectedEntity.rightRotation;
            const changed = (a: number[] | undefined, b: number[] | undefined) =>
              a && b && a.some((v, i) => Math.abs(v - (b[i] ?? 0)) > 0.0001);
            return (
              <div style={{ margin: '6px 0' }}>
                <div style={{ fontFamily: 'monospace', fontSize: '12px', display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
                  <span style={{ color: changed(T,  prev?.translation)  ? '#58a6ff' : '#8b949e' }}>T: {fmtVec3(T)}</span>
                  <span style={{ color: changed(S,  prev?.scale)        ? '#58a6ff' : '#8b949e' }}>S: {fmtVec3(S)}</span>
                  <span style={{ color: changed(LR, prev?.leftRotation) ? '#58a6ff' : '#8b949e' }}>LR: {fmtVec4(LR)}</span>
                  <span style={{ color: changed(RR, prev?.rightRotation)? '#58a6ff' : '#8b949e' }}>RR: {fmtVec4(RR)}</span>
                </div>
                {tl?.length ? (
                  <div style={{ fontSize: '10px', color: '#484f58', fontFamily: 'monospace', marginTop: '3px' }}>
                    keyframe {kfIdx + 1}/{tl.length}
                    {tf && <span style={{ marginLeft: '8px', color: '#30363d' }}>tick {tf.tick}</span>}
                    {prev && <span style={{ marginLeft: '8px', color: '#58a6ff' }}>· blue = changed from prev</span>}
                  </div>
                ) : (
                  <div style={{ fontSize: '10px', color: '#484f58', marginTop: '2px' }}>no transform timeline (reparse needed)</div>
                )}
              </div>
            );
          })()}

          <p style={{ margin: '4px 0 12px 0', fontSize: '13px', color: '#8b949e' }}>
            {(() => {
              const stat = entityStats.get(selectedEntity.id);
              return <>
                {stat?.count ?? 0} snapshots
                {stat && stat.count >= 2 && (
                  <> | Ticks {stat.firstTick} - {stat.lastTick}</>
                )}
              </>;
            })()}
          </p>

          {/* Tick slider */}
          {(() => {
            const stat = entityStats.get(selectedEntity.id);
            const maxTick = metadata?.duration ?? (stat?.lastTick ?? 1000);
            const firstTick = stat?.firstTick ?? 0;
            const tfCount = stat?.count ?? 0;
            return (
              <div style={{ marginBottom: '8px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '4px' }}>
                  <span style={{ fontSize: '11px', color: '#8b949e', whiteSpace: 'nowrap' }}>
                    Tick: <strong style={{ color: '#e6edf3' }}>{selectedTick}</strong>
                    <span style={{ color: '#484f58', marginLeft: '6px' }}>/ {maxTick}</span>
                  </span>
                  {tfCount > 0 && (
                    <span style={{ fontSize: '10px', color: '#f0883e' }}>
                      {tfCount} transform keyframes
                    </span>
                  )}
                  <button
                    onClick={() => setSelectedTick(firstTick)}
                    style={{ background: 'none', border: '1px solid #30363d', color: '#8b949e', borderRadius: '4px', padding: '1px 7px', cursor: 'pointer', fontSize: '10px' }}
                  >
                    ⏮ Reset
                  </button>
                </div>
                <input
                  type="range"
                  min={0}
                  max={maxTick}
                  step={1}
                  value={selectedTick}
                  onChange={e => setSelectedTick(Number(e.target.value))}
                  style={{ width: '100%', accentColor: '#1f6feb' }}
                />
              </div>
            );
          })()}

          {/* 3D Viewer */}
          <EntityViewer3D selected={selectedEntity} allEntities={entities} radius={3} selectedTick={selectedTick} ticks={ticks} />

          {/* Timeline preview (first/last 5 snapshots) */}
          <div style={{ maxHeight: '250px', overflow: 'auto', marginTop: '8px' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #30363d' }}>
                  <th style={thStyle}>Tick</th>
                  <th style={thStyle}>X</th>
                  <th style={thStyle}>Y</th>
                  <th style={thStyle}>Z</th>
                  <th style={thStyle}>Yaw</th>
                  <th style={thStyle}>Pitch</th>
                </tr>
              </thead>
              <tbody>
                {getTimelinePreview(entityTimelines?.get(selectedEntity.id) ?? []).map((snap, i) => (
                  <tr key={i} style={{ borderBottom: '1px solid #21262d' }}>
                    <td style={tdStyle}>{snap.tick}</td>
                    <td style={tdStyle}>{snap.x?.toFixed(2) ?? '-'}</td>
                    <td style={tdStyle}>{snap.y?.toFixed(2) ?? '-'}</td>
                    <td style={tdStyle}>{snap.z?.toFixed(2) ?? '-'}</td>
                    <td style={tdStyle}>{snap.yaw?.toFixed(1) ?? '-'}</td>
                    <td style={tdStyle}>{snap.pitch?.toFixed(1) ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {(entityTimelines?.get(selectedEntity.id)?.length ?? 0) > 10 && (
              <p style={{ textAlign: 'center', color: '#8b949e', fontSize: '11px', margin: '4px 0' }}>
                Showing first 5 and last 5 of {entityTimelines?.get(selectedEntity.id)?.length} snapshots
              </p>
            )}
          </div>
        </div>
      )}

      {/* Raw JSON */}
      <div style={{ marginTop: '20px' }}>
        <details>
          <summary style={{ cursor: 'pointer', padding: '5px', color: '#8b949e' }}>
            View Raw JSON
          </summary>
          <pre style={{
            padding: '10px',
            borderRadius: '4px',
            overflow: 'auto',
            maxHeight: '400px',
            fontSize: '11px',
            background: '#0d1117',
            border: '1px solid #30363d',
          }}>
            {JSON.stringify(record.parsedData, null, 2)}
          </pre>
        </details>
      </div>
    </div>
  );
}

const thStyle: React.CSSProperties = {
  padding: '6px 8px',
  textAlign: 'left',
  color: '#8b949e',
  fontWeight: 600,
  fontSize: '12px',
};

const tdStyle: React.CSSProperties = {
  padding: '4px 8px',
};

function getTypeColor(type: string): string {
  const colors: Record<string, string> = {
    armor_stand: '#da3633',
    player: '#1f6feb',
    interaction: '#a371f7',
    item_display: '#f0883e',
    text_display: '#3fb950',
    block_display: '#d29922',
    arrow: '#8b949e',
    item: '#d2a8ff',
    area_effect_cloud: '#79c0ff',
    slime: '#3fb950',
    minecart: '#d29922',
  };
  return colors[type] || '#30363d';
}

function fmtVec3(arr: number[] | undefined): string {
  if (!arr || arr.length < 3) return '—';
  return arr.slice(0, 3).map(v => v.toFixed(3)).join(', ');
}

function fmtVec4(arr: number[] | undefined): string {
  if (!arr || arr.length < 4) return '—';
  return arr.slice(0, 4).map(v => v.toFixed(3)).join(', ');
}

function getTimelinePreview(tl: EntityState[]) {
  if (tl.length <= 10) return tl;
  return [...tl.slice(0, 5), ...tl.slice(-5)];
}
