const AdmZip = require('adm-zip');
const fs = require('fs');
const path = require('path');

const PACKS_DIR = path.join(__dirname, '../../resource-packs');
const MANIFEST_PATH = path.join(PACKS_DIR, '.manifest.json');

class ResourcePackService {
    constructor() {
        this.manifest = null; // { version, files: { 'assets/...': 'hash.zip' } }
    }

    async init() { /* load or build manifest */
        try {
            this.manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH));
        } catch (e) {
            console.log(e);
            this.manifest = null;
        }
        const version = this._detectVersion();
        if (this.manifest != null && this.manifest.version == version) {
            return;
        }
        if (!version) return;
        const packDir = path.join(PACKS_DIR, version);
        this.manifest = await this._buildManifest(version, packDir);

        fs.writeFileSync(MANIFEST_PATH, JSON.stringify(this.manifest));
    }

    _detectVersion() {
        /* find the pack folder name, e.g. 'telos_1.4.2' */
        const entries = fs.readdirSync(PACKS_DIR); //lists all items in resource-packs/
        for (const entry of entries) {
            if (entry.startsWith('.') || entry.startsWith('README')) continue;
            const full = path.join(PACKS_DIR, entry)
            if (fs.statSync(full).isDirectory()) return entry;
        }
    }

    async _buildManifest(version, packDir) { /* scan all ZIPs, populate files map */
        const result = { version, files: {} };
        const zips = fs.readdirSync(packDir).filter(f => f.endsWith('.zip'));
        if (zips == null) return;
        for (const zip of zips) { //zip is just filename
            const zipPath = path.join(packDir, zip);
            const admZip = new AdmZip(zipPath); //open

            const entries = admZip.getEntries()
            if (entries == null) continue;
            for (const entry of entries) {
                if (entry.isDirectory) continue;
                result.files[entry.entryName] = zip;
            }
        }
        return result;
    }

    getFileBuffer(assetPath) { /* look up manifest, open ZIP, return buffer */
        const filename = this.manifest.files[assetPath];
        if (filename == null) return null;
        const zipPath = path.join(path.join(PACKS_DIR, this.manifest.version), filename);
        const admZip = new AdmZip(zipPath);
        return admZip.readFile(assetPath)
    }
}

module.exports = new ResourcePackService();
