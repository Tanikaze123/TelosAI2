import { useState } from 'react';
import { ReplayUpload } from './components/ReplayUpload';
import { ReplayList } from './components/ReplayList';
import { ReplayDetail } from './components/ReplayDetail';
import { ManualProcessor } from './components/ManualProcessor';
import { ModelDebugger } from './components/ModelDebugger';
import './App.css';

function App() {
  const [selectedReplayId, setSelectedReplayId] = useState<string | null>(null);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [showDebugger, setShowDebugger] = useState(false);

  const handleUploadComplete = () => {
    setRefreshTrigger(prev => prev + 1);
    setSelectedReplayId(null);
  };

  const handleProcessComplete = () => {
    setRefreshTrigger(prev => prev + 1);
  };

  return (
    <div className="app">
      <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
        <div>
          <h1>🎮 Minecraft Replay Parser - Phase 1 Tester</h1>
          <p>Upload and test .mcpr replay file parsing</p>
        </div>
        <button
          onClick={() => setShowDebugger(true)}
          style={{
            background: '#21262d', border: '1px solid #30363d', color: '#f0883e',
            padding: '6px 14px', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: 600,
          }}
        >
          🔧 Model Debugger
        </button>
      </header>

      <main>
        <div className="grid">
          {/* Upload Section */}
          <div className="card">
            <ReplayUpload onUploadComplete={handleUploadComplete} />
          </div>

          {/* Manual Processor */}
          <div className="card">
            <ManualProcessor onProcessComplete={handleProcessComplete} />
          </div>
        </div>

        {/* Replay List */}
        <div className="card">
          <ReplayList
            onSelectReplay={setSelectedReplayId}
            refreshTrigger={refreshTrigger}
          />
        </div>

        {/* Replay Detail */}
        {selectedReplayId && (
          <div className="card">
            <ReplayDetail
              replayId={selectedReplayId}
              onClose={() => setSelectedReplayId(null)}
            />
          </div>
        )}
      </main>

      {showDebugger && <ModelDebugger onClose={() => setShowDebugger(false)} />}

      <footer style={{
        marginTop: '40px',
        padding: '20px',
        textAlign: 'center',
        borderTop: '1px solid #eee',
        color: '#666'
      }}>
        <p>Phase 1: Parser Testing | Backend: http://localhost:4000</p>
      </footer>
    </div>
  );
}

export default App;
