const { Replay } = require('../models');

class ReplayStorageService {
  /**
   * Create a new replay record
   * @param {Object} parsedData - Parsed replay data
   * @param {Object} metadata - Additional metadata
   * @returns {Promise<Object>} Created replay record
   */
  async storeReplay(parsedData, metadata) {
    try {
      const replay = await Replay.create({
        filename: metadata.filename,
        duration: parsedData.metadata?.duration || parsedData.duration,
        recordedAt: parsedData.metadata?.recordedAt || new Date(),
        mcVersion: parsedData.metadata?.mcVersion || 'unknown',
        serverName: parsedData.metadata?.serverName || 'unknown',
        parsedData: parsedData,
        metadata: {
          ...parsedData.metadata,
          uploadedAt: new Date(),
          fileSize: metadata.fileSize,
          filePath: metadata.filePath || null
        },
        parseStatus: 'completed'
      });

      return replay;
    } catch (error) {
      console.error('Storage error:', error);
      throw new Error(`Failed to store replay: ${error.message}`);
    }
  }

  /**
   * Get replay by ID
   * @param {string} replayId - UUID of replay
   * @returns {Promise<Object>} Replay record
   */
  async getReplay(replayId) {
    const replay = await Replay.findByPk(replayId);

    if (!replay) {
      throw new Error('Replay not found');
    }

    return replay;
  }

  /**
   * List all replays
   * @param {Object} options - Query options
   * @returns {Promise<Array>} List of replays
   */
  async listReplays(options = {}) {
    const {
      limit = 50,
      offset = 0,
      orderBy = 'recordedAt',
      orderDir = 'DESC'
    } = options;

    const replays = await Replay.findAll({
      attributes: [
        'id',
        'filename',
        'duration',
        'recordedAt',
        'mcVersion',
        'serverName',
        'parseStatus',
        'createdAt'
      ],
      order: [[orderBy, orderDir]],
      limit,
      offset
    });

    const total = await Replay.count();

    return {
      replays,
      total,
      limit,
      offset
    };
  }

  /**
   * Update replay parse status
   * @param {string} replayId - UUID of replay
   * @param {string} status - New status
   * @param {string} error - Error message if failed
   */
  async updateParseStatus(replayId, status, error = null) {
    await Replay.update(
      {
        parseStatus: status,
        parseError: error
      },
      { where: { id: replayId } }
    );
  }

  /**
   * Update replay's parsedData after a reparse
   * @param {string} replayId - UUID of replay
   * @param {Object} parsedData - New parsed data
   */
  async updateParsedData(replayId, parsedData) {
    const [count] = await Replay.update(
      {
        parsedData: parsedData,
        duration: parsedData.metadata?.duration || parsedData.duration,
        mcVersion: parsedData.metadata?.mcVersion || 'unknown',
        serverName: parsedData.metadata?.serverName || 'unknown',
        parseStatus: 'completed'
      },
      { where: { id: replayId } }
    );
    if (count === 0) throw new Error('Replay not found');
  }

  /**
   * Delete replay
   * @param {string} replayId - UUID of replay
   */
  async deleteReplay(replayId) {
    const result = await Replay.destroy({
      where: { id: replayId }
    });

    if (result === 0) {
      throw new Error('Replay not found');
    }

    return true;
  }
}

module.exports = new ReplayStorageService();
