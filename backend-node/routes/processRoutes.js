const express = require('express');
const router = express.Router();
const processController = require('../controllers/processController');

// Routes
router.post('/processes', processController.trackProcesses);
router.get('/history', processController.getHistory);

module.exports = router;
