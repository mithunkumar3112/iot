const express = require('express');
const router = express.Router();
const multer = require('multer');
const fileController = require('../controllers/fileController');

// Multer storage configuration (memory storage for direct Supabase upload)
const storage = multer.memoryStorage();
const upload = multer({ storage: storage });

// Routes
router.post('/upload', upload.single('file'), fileController.uploadFile);
router.get('/files/:deviceId', fileController.getFilesByDevice);

module.exports = router;
