# Minecraft AI Backend

Backend server for processing ReplayMod files and training AI on boss fight patterns.

## Setup

### 1. Install Dependencies
```bash
npm install
```

### 2. Configure Environment
```bash
cp .env.example .env
# Edit .env with your database credentials
```

### 3. Create Database
```bash
createdb minecraft_ai
# Or: psql -c "CREATE DATABASE minecraft_ai;"
```

### 4. Run Migrations
```bash
npm run migrate
```

### 5. Add Java Parser
The server expects a JAR file at `src/parsers/replay-parser.jar`

Build the Java parser first:
```bash
cd ../replay-parser
./gradlew shadowJar
cp build/libs/replay-parser.jar ../server-backend/src/parsers/
```

### 6. Start Server
```bash
# Development (with auto-reload)
npm run dev

# Production
npm start
```

## Upload Folder Structure

```
uploads/
├── manual/       # Place .mcpr files here for manual processing
├── api/          # Temporary storage for API uploads
└── processed/    # Successfully processed files moved here
```

## API Endpoints

### Upload Replay (via API)
```bash
POST /api/replay/upload
Content-Type: multipart/form-data

curl -X POST http://localhost:3001/api/replay/upload \
  -F "replay=@path/to/replay.mcpr"
```

### Process Manual Uploads
```bash
POST /api/replay/process-manual

# Process all .mcpr files from uploads/manual/ folder
curl -X POST http://localhost:3001/api/replay/process-manual
```

### Get Replay
```bash
GET /api/replay/:id

curl http://localhost:3001/api/replay/550e8400-e29b-41d4-a716-446655440000
```

### List Replays
```bash
GET /api/replay?limit=10&offset=0

curl http://localhost:3001/api/replay
```

### Delete Replay
```bash
DELETE /api/replay/:id

curl -X DELETE http://localhost:3001/api/replay/550e8400-e29b-41d4-a716-446655440000
```

## Testing

### Option 1: Upload via API
```bash
curl -X POST http://localhost:3001/api/replay/upload \
  -F "replay=@~/.minecraft/replay_recordings/2026_02_14_15_30_45.mcpr"
```

### Option 2: Manual Upload & Batch Processing
```bash
# 1. Copy files to manual folder
cp ~/.minecraft/replay_recordings/*.mcpr ./uploads/manual/

# 2. Trigger processing
curl -X POST http://localhost:3001/api/replay/process-manual

# 3. Check results (files moved to uploads/processed/)
ls uploads/processed/
```

## Troubleshooting

### "Java parser not found"
- Build the Java parser project first
- Copy JAR to `src/parsers/replay-parser.jar`

### Database connection errors
- Ensure PostgreSQL is running
- Check credentials in `.env`
- Run migrations: `npm run migrate`

### Upload fails
- Check file size limit in `.env` (MAX_FILE_SIZE)
- Ensure .mcpr file is valid
- Check server logs for details
