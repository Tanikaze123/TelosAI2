const { exec } = require('child_process');
const util = require('util');
const path = require('path');
const fs = require('fs').promises;
const execPromise = util.promisify(exec);

class ReplayParserService {
  constructor() {
    this.jarPath = process.env.JAVA_PARSER_PATH ||
                   path.join(__dirname, '../parsers/replay-parser.jar');
  }

  /**
   * Parse a .mcpr file using Java ReplayStudio parser
   * @param {string} mcprPath - Path to .mcpr file
   * @returns {Promise<Object>} Parsed replay data
   */
  async parseReplayFile(mcprPath) {
    try {
      // Check if JAR exists
      await fs.access(this.jarPath);
    } catch (error) {
      throw new Error(
        `Java parser not found at ${this.jarPath}. ` +
        `Please build replay-parser and copy JAR to src/parsers/`
      );
    }

    try {
      console.log(`Parsing replay: ${mcprPath}`);
      console.log(`Using parser: ${this.jarPath}`);

      // Write JSON to a temp file to avoid stdout maxBuffer limits
      const tmpOut = path.join(require('os').tmpdir(), `replay-${Date.now()}.json`);

      const { stderr } = await execPromise(
        `java -jar "${this.jarPath}" "${mcprPath}" "${tmpOut}"`,
        { maxBuffer: 10 * 1024 * 1024 } // stderr only, 10MB is plenty
      );

      if (stderr && !stderr.includes('SLF4J')) {
        console.warn('Parser stderr:', stderr);
      }

      const jsonText = await fs.readFile(tmpOut, 'utf8');
      await fs.unlink(tmpOut).catch(() => {}); // clean up temp file

      if (!jsonText || jsonText.trim().length === 0) {
        throw new Error('Parser produced no output');
      }

      const parsed = JSON.parse(jsonText);

      console.log(`Parse successful: ${parsed.entities?.length || 0} entities, ${parsed.duration || 0} ticks`);

      return parsed;

    } catch (error) {
      console.error('Parse error:', error);

      if (error.message.includes('JSON')) {
        throw new Error(`Parser output is not valid JSON: ${error.message}`);
      }

      throw new Error(`Failed to parse replay: ${error.message}`);
    }
  }

  /**
   * Validate parsed data structure
   * @param {Object} data - Parsed data to validate
   * @returns {boolean} True if valid
   */
  validateParsedData(data) {
    if (!data || typeof data !== 'object') {
      throw new Error('Parsed data is not an object');
    }

    if (!data.metadata || typeof data.metadata !== 'object') {
      throw new Error('Missing metadata in parsed data');
    }

    if (!Array.isArray(data.entities)) {
      throw new Error('Entities must be an array');
    }

    return true;
  }
}

module.exports = new ReplayParserService();
