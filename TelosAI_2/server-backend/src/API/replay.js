const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs').promises;
const replayParser = require('../Services/replayParser');
const replayStorage = require('../Services/replayStorage');

const router = express.Router();

// Configure multer for file uploads
const storage = multer.diskStorage({
  destination: path.join(process.env.UPLOAD_DIR || './uploads', 'api'),
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'replay-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({
  storage: storage,
  limits: {
    fileSize: parseInt(process.env.MAX_FILE_SIZE) || Infinity // 0 = no limit
  },
  fileFilter: (req, file, cb) => {
    if (path.extname(file.originalname).toLowerCase() !== '.mcpr') {
      return cb(new Error('Only .mcpr files are allowed'));
    }
    cb(null, true);
  }
});

/**
 * POST /api/replay/upload
 * Upload and parse a .mcpr file
 */
router.post('/upload', upload.single('replay'), async (req, res) => {
  let filePath = null;

  try {
    if (!req.file) {
      return res.status(400).json({
        error: 'No file uploaded. Please upload a .mcpr file.'
      });
    }

    filePath = req.file.path;
    const filename = req.file.originalname;
    const fileSize = req.file.size;

    console.log(`Processing upload: ${filename} (${fileSize} bytes)`);

    // Parse the replay file
    const parsedData = await replayParser.parseReplayFile(filePath);

    // Validate parsed data
    replayParser.validateParsedData(parsedData);

    // Move to processed folder first so we can store the final path
    const processedPath = path.join(process.env.UPLOAD_DIR || './uploads', 'processed', path.basename(filePath));
    await fs.rename(filePath, processedPath);

    // Store in database with file path for future reparse
    const replay = await replayStorage.storeReplay(parsedData, {
      filename,
      fileSize,
      filePath: processedPath
    });

    res.json({
      success: true,
      replayId: replay.id,
      filename: replay.filename,
      duration: replay.duration,
      entityCount: parsedData.entities?.length || 0,
      mcVersion: replay.mcVersion,
      serverName: replay.serverName
    });

  } catch (error) {
    console.error('Upload error:', error);

    // Clean up file on error
    if (filePath) {
      try {
        await fs.unlink(filePath);
      } catch (unlinkError) {
        console.error('Failed to delete temp file:', unlinkError);
      }
    }

    res.status(500).json({
      error: error.message,
      details: process.env.NODE_ENV === 'development' ? error.stack : undefined
    });
  }
});

/**
 * POST /api/replay/process-manual
 * Process all .mcpr files from the manual upload folder
 */
router.post('/process-manual', async (req, res) => {
  try {
    const manualDir = path.join(process.env.UPLOAD_DIR || './uploads', 'manual');
    const processedDir = path.join(process.env.UPLOAD_DIR || './uploads', 'processed');

    // Get all .mcpr files in manual folder
    const files = await fs.readdir(manualDir);
    const mcprFiles = files.filter(file => path.extname(file).toLowerCase() === '.mcpr');

    if (mcprFiles.length === 0) {
      return res.json({
        success: true,
        message: 'No files to process',
        processed: 0,
        failed: 0
      });
    }

    const results = {
      processed: [],
      failed: []
    };

    // Process each file
    for (const filename of mcprFiles) {
      const filePath = path.join(manualDir, filename);

      try {
        console.log(`Processing manual upload: ${filename}`);

        const stats = await fs.stat(filePath);
        const fileSize = stats.size;

        // Parse the replay file
        const parsedData = await replayParser.parseReplayFile(filePath);

        // Validate parsed data
        replayParser.validateParsedData(parsedData);

        // Move to processed folder first so we can store the final path
        const processedPath = path.join(processedDir, filename);
        await fs.rename(filePath, processedPath);

        // Store in database with file path for future reparse
        const replay = await replayStorage.storeReplay(parsedData, {
          filename,
          fileSize,
          filePath: processedPath
        });

        results.processed.push({
          filename,
          replayId: replay.id,
          entityCount: parsedData.entities?.length || 0
        });

        console.log(`✓ Processed: ${filename} → ${replay.id}`);

      } catch (error) {
        console.error(`✗ Failed to process ${filename}:`, error.message);

        results.failed.push({
          filename,
          error: error.message
        });
      }
    }

    res.json({
      success: true,
      message: `Processed ${results.processed.length} files, ${results.failed.length} failed`,
      processed: results.processed.length,
      failed: results.failed.length,
      details: results
    });

  } catch (error) {
    console.error('Process manual error:', error);
    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/replay/:id
 * Get replay data by ID
 */
router.get('/:id', async (req, res) => {
  try {
    const replay = await replayStorage.getReplay(req.params.id);
    res.json(replay);
  } catch (error) {
    console.error('Get replay error:', error);

    if (error.message === 'Replay not found') {
      return res.status(404).json({ error: error.message });
    }

    res.status(500).json({ error: error.message });
  }
});

/**
 * GET /api/replay
 * List all replays with pagination
 */
router.get('/', async (req, res) => {
  try {
    const options = {
      limit: parseInt(req.query.limit) || 50,
      offset: parseInt(req.query.offset) || 0,
      orderBy: req.query.orderBy || 'recordedAt',
      orderDir: req.query.orderDir || 'DESC'
    };

    const result = await replayStorage.listReplays(options);
    res.json(result);
  } catch (error) {
    console.error('List replays error:', error);
    res.status(500).json({ error: error.message });
  }
});

/**
 * POST /api/replay/:id/reparse
 * Re-run the Java parser on the stored .mcpr file and update parsedData in DB.
 * Requires that the replay was uploaded after filePath storage was added.
 */
router.post('/:id/reparse', async (req, res) => {
  try {
    const replay = await replayStorage.getReplay(req.params.id);
    const filePath = replay.metadata?.filePath;

    if (!filePath) {
      return res.status(400).json({
        error: 'No file path stored for this replay. Delete and re-upload to reparse.'
      });
    }

    // Verify file still exists
    await fs.access(filePath);

    console.log(`Reparsing replay ${req.params.id}: ${filePath}`);
    const parsedData = await replayParser.parseReplayFile(filePath);
    replayParser.validateParsedData(parsedData);

    await replayStorage.updateParsedData(req.params.id, parsedData);

    res.json({
      success: true,
      entityCount: parsedData.entities?.length || 0,
      modelCount: parsedData.entities?.filter(e => e.modelId).length || 0
    });
  } catch (error) {
    console.error('Reparse error:', error);
    if (error.message === 'Replay not found') {
      return res.status(404).json({ error: error.message });
    }
    if (error.code === 'ENOENT') {
      return res.status(400).json({ error: 'Original .mcpr file no longer exists on disk.' });
    }
    res.status(500).json({ error: error.message });
  }
});

/**
 * DELETE /api/replay/:id
 * Delete a replay
 */
router.delete('/:id', async (req, res) => {
  try {
    await replayStorage.deleteReplay(req.params.id);
    res.json({ success: true });
  } catch (error) {
    console.error('Delete replay error:', error);

    if (error.message === 'Replay not found') {
      return res.status(404).json({ error: error.message });
    }

    res.status(500).json({ error: error.message });
  }
});

module.exports = router;
