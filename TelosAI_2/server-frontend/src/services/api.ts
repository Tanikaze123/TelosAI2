import type { ReplayListItem, ReplayRecord, UploadResponse, ProcessManualResponse } from '../types/replay';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:4000';

export const replayApi = {
  /**
   * Upload a .mcpr file
   */
  async upload(file: File): Promise<UploadResponse> {
    const formData = new FormData();
    formData.append('replay', file);

    const response = await fetch(`${API_BASE}/api/replay/upload`, {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error || 'Upload failed');
    }

    return response.json();
  },

  /**
   * Get list of all replays
   */
  async list(): Promise<{ replays: ReplayListItem[]; total: number }> {
    const response = await fetch(`${API_BASE}/api/replay`);

    if (!response.ok) {
      throw new Error('Failed to fetch replays');
    }

    return response.json();
  },

  /**
   * Get a specific replay by ID
   */
  async getById(id: string): Promise<ReplayRecord> {
    const response = await fetch(`${API_BASE}/api/replay/${id}`);

    if (!response.ok) {
      throw new Error('Replay not found');
    }

    return response.json();
  },

  /**
   * Delete a replay
   */
  async delete(id: string): Promise<void> {
    const response = await fetch(`${API_BASE}/api/replay/${id}`, {
      method: 'DELETE',
    });

    if (!response.ok) {
      throw new Error('Failed to delete replay');
    }
  },

  /**
   * Re-run the Java parser on the stored file and update parsedData
   */
  async reparse(id: string): Promise<{ success: boolean; entityCount: number; modelCount: number }> {
    const response = await fetch(`${API_BASE}/api/replay/${id}/reparse`, {
      method: 'POST',
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error || 'Reparse failed');
    }

    return response.json();
  },

  /**
   * Process manually uploaded files
   */
  async processManual(): Promise<ProcessManualResponse> {
    const response = await fetch(`${API_BASE}/api/replay/process-manual`, {
      method: 'POST',
    });

    if (!response.ok) {
      throw new Error('Failed to process manual uploads');
    }

    return response.json();
  },
};
