const express = require('express');
const path = require('path');
const resPackService = require('../Services/resourcePackService');

const router = express.Router();

/**
 * GET /api/resource
 * Get res data
 */
router.get('/*splat', async (req, res) => {
    try {
        const assetPath = req.params.splat.join('/');
        console.log(assetPath);
        const buffer = await resPackService.getFileBuffer(assetPath);
        if (buffer != null) {
            const ext = path.extname(assetPath);
            res.type(ext);
            return res.send(buffer);
        }
        res.status(404).json({ error: "Asset not found" });
    } catch (error) {
        console.error('Get asset error:', error);

        if (error.message === 'Asset not found') {
            return res.status(404).json({ error: error.message });
        }

        res.status(500).json({ error: error.message });
    }
});

module.exports = router; 