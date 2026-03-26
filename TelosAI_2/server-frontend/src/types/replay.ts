export interface ReplayMetadata {
  duration: number;
  mcVersion: string;
  serverName: string;
  recordedAt: number;
  fileFormat: string;
  protocolVersion: number;
}

export interface EntitySnapshot {
  tick: number;
  x: number;
  y: number;
  z: number;
  vx: number;
  vy: number;
  vz: number;
  yaw: number;
  pitch: number;
  animation?: string;
}

export interface TransformSnapshot {
  tick: number;
  translation: number[];   // [x, y, z]
  scale: number[];         // [x, y, z]
  leftRotation: number[];  // [x, y, z, w]
  rightRotation: number[]; // [x, y, z, w]
}

export interface EntityData {
  id: string;
  uuid: string;
  type: string;
  modelId?: string;
  modelPath?: string;
  groupId?: string;

  translation: number[];    // [x, y, z]
  scale: number[];          // [x, y, z]
  leftRotation: number[];   // [x, y, z, w]
  rightRotation: number[];  // [x, y, z, w]
}

export interface ParsedReplay {
  metadata: ReplayMetadata;
  entities: EntityData[];
  duration: number;

  ticks: TickData[]
}

/** Shape returned by GET /api/replay/:id (Sequelize model) */
export interface ReplayRecord {
  id: string;
  filename: string;
  duration: number;
  recordedAt: string;
  mcVersion: string;
  serverName: string;
  parsedData: ParsedReplay;
  metadata: ReplayMetadata & { uploadedAt: string; fileSize: number };
  parseStatus: string;
  parseError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReplayListItem {
  id: string;
  filename: string;
  duration: number;
  recordedAt: string;
  mcVersion: string;
  serverName: string;
  parseStatus: string;
  createdAt: string;
}

export interface UploadResponse {
  success: boolean;
  replayId: string;
  filename: string;
  duration: number;
  entityCount: number;
  mcVersion: string;
  serverName: string;
}

export interface ProcessManualResponse {
  success: boolean;
  message: string;
  processed: number;
  failed: number;
  details: {
    processed: Array<{
      filename: string;
      replayId: string;
      entityCount: number;
    }>;
    failed: Array<{
      filename: string;
      error: string;
    }>;
  };
}

export interface TickChange {
  id: string;
  x?: number;
  y?: number;
  z?: number;
  yaw?: number;
  pitch?: number;

  translation?: number[];   // [x, y, z]
  scale?: number[];         // [x, y, z]
  leftRotation?: number[];  // [x, y, z, w]
  rightRotation?: number[]; // [x, y, z, w]
}

export interface TickData {
  tick: number;
  changes: Array<TickChange>;
}
