import { useState } from 'react';
import { replayApi } from '../services/api';
import type { UploadResponse } from '../types/replay';

interface Props {
  onUploadComplete?: (response: UploadResponse) => void;
}

export function ReplayUpload({ onUploadComplete }: Props) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<UploadResponse | null>(null);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.name.endsWith('.mcpr')) {
      setError('Please select a .mcpr file');
      return;
    }

    setUploading(true);
    setError(null);
    setResult(null);

    try {
      const response = await replayApi.upload(file);
      setResult(response);
      onUploadComplete?.(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  return (
    <div style={{
      padding: '20px',
      border: '2px dashed #30363d',
      borderRadius: '8px',
    }}>
      <h3>Upload Replay</h3>

      <input
        type="file"
        accept=".mcpr"
        onChange={handleFileChange}
        disabled={uploading}
        style={{ marginBottom: '10px' }}
      />

      {uploading && <p style={{ color: '#58a6ff' }}>Uploading and parsing...</p>}

      {error && (
        <div style={{
          padding: '10px',
          backgroundColor: '#3d1a1a',
          color: '#f85149',
          borderRadius: '4px',
          border: '1px solid #f8514933',
          marginTop: '10px'
        }}>
          {error}
        </div>
      )}

      {result && (
        <div style={{
          padding: '10px',
          backgroundColor: '#122117',
          borderRadius: '4px',
          border: '1px solid #23863633',
          marginTop: '10px'
        }}>
          <p style={{ color: '#3fb950', margin: 0 }}>Upload successful!</p>
          <p style={{ margin: '5px 0' }}>
            <strong>File:</strong> {result.filename}
          </p>
          <p style={{ margin: '5px 0' }}>
            <strong>Server:</strong> {result.serverName}
          </p>
          <p style={{ margin: '5px 0' }}>
            <strong>Version:</strong> {result.mcVersion}
          </p>
          <p style={{ margin: '5px 0' }}>
            <strong>Duration:</strong> {Math.floor(result.duration / 20)} seconds
          </p>
          <p style={{ margin: '5px 0' }}>
            <strong>Entities:</strong> {result.entityCount} (Phase 1: empty)
          </p>
          <p style={{ margin: '5px 0', fontSize: '12px', color: '#8b949e' }}>
            Replay ID: <code>{result.replayId}</code>
          </p>
        </div>
      )}
    </div>
  );
}
