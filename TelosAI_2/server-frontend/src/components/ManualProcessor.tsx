import { useState } from 'react';
import { replayApi } from '../services/api';
import type { ProcessManualResponse } from '../types/replay';

interface Props {
  onProcessComplete?: () => void;
}

export function ManualProcessor({ onProcessComplete }: Props) {
  const [processing, setProcessing] = useState(false);
  const [result, setResult] = useState<ProcessManualResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleProcess = async () => {
    setProcessing(true);
    setError(null);
    setResult(null);

    try {
      const response = await replayApi.processManual();
      setResult(response);
      onProcessComplete?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Processing failed');
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div style={{
      padding: '20px',
      border: '2px solid #30363d',
      borderRadius: '8px',
    }}>
      <h3>Manual Upload Processor</h3>
      <p style={{ fontSize: '14px', color: '#8b949e' }}>
        Process .mcpr files from <code>server-backend/uploads/manual/</code>
      </p>

      <button
        onClick={handleProcess}
        disabled={processing}
        style={{
          padding: '10px 20px',
          fontSize: '16px',
          marginTop: '10px',
        }}
      >
        {processing ? 'Processing...' : 'Process Manual Uploads'}
      </button>

      {error && (
        <div style={{
          marginTop: '15px',
          padding: '10px',
          backgroundColor: '#3d1a1a',
          color: '#f85149',
          borderRadius: '4px',
          border: '1px solid #f8514933',
        }}>
          {error}
        </div>
      )}

      {result && (
        <div style={{
          marginTop: '15px',
          padding: '10px',
          backgroundColor: '#122117',
          borderRadius: '4px',
          border: '1px solid #23863633',
        }}>
          <p style={{ color: '#3fb950', margin: '0 0 10px 0' }}>
            {result.message}
          </p>
          <p style={{ margin: '5px 0' }}>
            <strong>Processed:</strong> {result.processed}
          </p>
          <p style={{ margin: '5px 0' }}>
            <strong>Failed:</strong> {result.failed}
          </p>

          {result.details.processed.length > 0 && (
            <details style={{ marginTop: '10px' }}>
              <summary style={{ cursor: 'pointer' }}>
                View processed files ({result.details.processed.length})
              </summary>
              <ul style={{ marginTop: '10px', paddingLeft: '20px' }}>
                {result.details.processed.map((item, i) => (
                  <li key={i} style={{ marginBottom: '5px' }}>
                    <strong>{item.filename}</strong> - {item.entityCount} entities
                    <br />
                    <code style={{ fontSize: '12px' }}>
                      ID: {item.replayId}
                    </code>
                  </li>
                ))}
              </ul>
            </details>
          )}

          {result.details.failed.length > 0 && (
            <details style={{ marginTop: '10px' }}>
              <summary style={{ cursor: 'pointer', color: '#f85149' }}>
                View failed files ({result.details.failed.length})
              </summary>
              <ul style={{ marginTop: '10px', paddingLeft: '20px' }}>
                {result.details.failed.map((item, i) => (
                  <li key={i} style={{ marginBottom: '5px' }}>
                    <strong>{item.filename}</strong>
                    <br />
                    <span style={{ fontSize: '12px', color: '#f85149' }}>
                      Error: {item.error}
                    </span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
    </div>
  );
}
