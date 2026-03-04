require('dotenv').config();
const express = require('express');
const { createServer } = require('node:http');
const { Server } = require('socket.io');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const morgan = require('morgan');
const path = require('path');
const fs = require('fs');

const db = require('./src/models');
const replayRoutes = require('./src/API/replay');
const resourcePackRoutes = require('./src/API/resourcePack');

const resourcePackService = require('./src/Services/resourcePackService');

const app = express();
const PORT = process.env.PORT || 4000;

// Middleware
app.use(helmet());
app.use(cors());
app.use(compression());
app.use(morgan('combined'));
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Ensure upload directories exist
const uploadDir = process.env.UPLOAD_DIR || './uploads';
const uploadDirs = {
  manual: path.join(uploadDir, 'manual'),
  api: path.join(uploadDir, 'api'),
  processed: path.join(uploadDir, 'processed')
};

Object.values(uploadDirs).forEach(dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
    console.log(`✓ Created directory: ${dir}`);
  }
});

// Ensure parsers directory exists
const parsersDir = path.join(__dirname, 'src/parsers');
if (!fs.existsSync(parsersDir)) {
  fs.mkdirSync(parsersDir, { recursive: true });
}

// Resource packs — drop extracted server resource packs here, served at /resource-pack
const resourcePacksDir = path.join(__dirname, 'resource-packs');
if (!fs.existsSync(resourcePacksDir)) {
  fs.mkdirSync(resourcePacksDir, { recursive: true });
  console.log(`✓ Created directory: ${resourcePacksDir}`);
}
app.use('/resource-pack', express.static(resourcePacksDir));

const server = createServer(app);
const io = new Server(server, {
  cors: { origin: "http://localhost:5173" } // Vite dev port
});

// Health check
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// API Routes
app.use('/api/replay', replayRoutes);
app.use('/api/rp', resourcePackRoutes);

// 1. ENDPOINT FOR JAVA MOD (HTTP POST)
app.post('/api/update', (req, res) => {
  // Broadast the entity data to all connected React clients
  io.emit('entity-data', req.body);
  res.status(200).send({ status: 'received' });
});

io.on('connection', (socket) => {
  console.log(`✅ User connected: ${socket.id}`);

  // Listen for the "ping" from the frontend
  socket.on('ping-test', (data) => {
    console.log('📩 Received ping:', data.message);

    // Send a "pong" back to JUST this specific client
    socket.emit('pong-test', {
      message: 'Pong! The backend hears you loud and clear.',
      timestamp: new Date().toLocaleTimeString()
    });
  });

  socket.on('disconnect', () => {
    console.log('❌ User disconnected');
  });
});

// 404 handler
app.use((req, res) => {
  res.status(404).json({ error: 'Route not found' });
});

// Error handler
app.use((err, req, res, next) => {
  console.error('Server error:', err);

  res.status(err.status || 500).json({
    error: err.message || 'Internal server error',
    details: process.env.NODE_ENV === 'development' ? err.stack : undefined
  });
});

// Database connection and server start
async function startServer() {
  try {

    await resourcePackService.init();
    console.log('✓ Resource pack manifest loaded');

    // Test database connection
    await db.sequelize.authenticate();
    console.log('✓ Database connected');

    // Sync models (development only)
    if (process.env.NODE_ENV === 'development') {
      await db.sequelize.sync({ alter: false });
      console.log('✓ Database models synced');
    }

    // Start server
    server.listen(PORT, () => {
      console.log(`✓ Server running on port ${PORT}`);
      console.log(`✓ Environment: ${process.env.NODE_ENV || 'development'}`);
      console.log(`✓ Upload directory: ${uploadDir}`);
      console.log(`\nAPI Endpoints:`);
      console.log(`  POST   http://localhost:${PORT}/api/update (Minecraft Mod)`);
      console.log(`  POST   http://localhost:${PORT}/api/replay/upload`);
      console.log(`  GET    http://localhost:${PORT}/api/replay`);
      console.log(`  GET    http://localhost:${PORT}/api/replay/:id`);
      console.log(`  DELETE http://localhost:${PORT}/api/replay/:id`);
      console.log(`  GET    http://localhost:${PORT}/api/rp/:assets`);
      console.log(`\nSocket.IO: Entity data streaming active`);
    });

  } catch (error) {
    console.error('Failed to start server:', error);
    process.exit(1);
  }
}

startServer();