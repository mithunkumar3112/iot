const express = require('express');
const router = express.Router();
const multer = require('multer');
const fileController = require('../controllers/fileController');

const storage = multer.memoryStorage();
const maxUploadBytes = Number(process.env.MAX_UPLOAD_BYTES || 100 * 1024 * 1024);

// Intentionally no fileFilter: documents and images are accepted, and MIME is
// inferred in the controller before upload to Supabase.
const upload = multer({
  storage: storage,
  limits: { fileSize: maxUploadBytes }
});

router.post('/upload', (req, res, next) => {
  upload.single('file')(req, res, function (err) {
    if (err) {
      console.error('[files] Multer upload error:', err);
      return res.status(400).json({ error: err.message, code: err.code });
    }
    next();
  });
}, fileController.uploadFile);

router.get('/files/all', fileController.getAllFiles);
router.get('/files/device/:deviceId', fileController.getFilesByDevice);
router.get('/files/recent', fileController.getRecentFiles);
router.get('/files/:deviceId', fileController.getFilesByDevice);

module.exports = router;
