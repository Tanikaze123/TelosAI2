import { useState, useEffect } from 'react';
import { replayApi } from '../services/api';
import type { ReplayListItem } from '../types/replay';

interface Props {
  onSelectReplay?: (id: string) => void;
  refreshTrigger?: number;
}

export function ReplayList({ onSelectReplay, refreshTrigger }: Props) {
  const [replays, setReplays] = useState<ReplayListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadReplays = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await replayApi.list();
      setReplays(data.replays);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load replays');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReplays();
  }, [refreshTrigger]);

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this replay?')) return;

    try {
      await replayApi.delete(id);
      await loadReplays();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to delete');
    }
  };

  if (loading) return <p>Loading replays...</p>;
  if (error) return <p style={{ color: 'red' }}>Error: {error}</p>;
  if (replays.length === 0) return <p>No replays uploaded yet.</p>;

  return (
    <div>
      <h3>Replays ({replays.length})</h3>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ borderBottom: '2px solid #ddd' }}>
            <th style={{ padding: '10px', textAlign: 'left' }}>Filename</th>
            <th style={{ padding: '10px', textAlign: 'left' }}>Server</th>
            <th style={{ padding: '10px', textAlign: 'left' }}>Version</th>
            <th style={{ padding: '10px', textAlign: 'right' }}>Duration</th>
            <th style={{ padding: '10px', textAlign: 'left' }}>Recorded</th>
            <th style={{ padding: '10px', textAlign: 'left' }}>Parsed</th>
            <th style={{ padding: '10px', textAlign: 'center' }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {replays.map((replay) => (
            <tr key={replay.id} style={{ borderBottom: '1px solid #eee' }}>
              <td style={{ padding: '10px' }}>{replay.filename}</td>
              <td style={{ padding: '10px' }}>{replay.serverName}</td>
              <td style={{ padding: '10px' }}>{replay.mcVersion}</td>
              <td style={{ padding: '10px', textAlign: 'right' }}>
                {Math.floor(replay.duration / 20)}s
              </td>
              <td style={{ padding: '10px' }}>
                {new Date(replay.recordedAt).toLocaleDateString()}
              </td>
              <td style={{ padding: '10px' }}>
                {new Date(replay.createdAt).toLocaleString()}
              </td>
              <td style={{ padding: '10px', textAlign: 'center' }}>
                <button
                  onClick={() => onSelectReplay?.(replay.id)}
                  style={{ marginRight: '5px' }}
                >
                  View
                </button>
                <button
                  onClick={() => handleDelete(replay.id)}
                  style={{ color: 'red' }}
                >
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
