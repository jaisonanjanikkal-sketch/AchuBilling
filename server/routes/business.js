// =============================================================================
//  BUSINESS ROUTES — Business profile API
// =============================================================================

const express = require('express');
const router = express.Router();
const db = require('../database');

// ---------------------------------------------------------------------------
//  GET /api/business — Get business profile
// ---------------------------------------------------------------------------
router.get('/', (req, res) => {
  try {
    const biz = db.prepare('SELECT * FROM business WHERE id = 1').get();
    res.json({
      name: biz ? biz.name : 'My Business',
      phone: biz ? biz.phone : '',
      address: biz ? biz.address : ''
    });
  } catch (err) {
    console.error('GET /api/business error:', err.message);
    res.status(500).json({ error: 'Failed to fetch business profile' });
  }
});

// ---------------------------------------------------------------------------
//  PUT /api/business — Update business profile
// ---------------------------------------------------------------------------
router.put('/', (req, res) => {
  try {
    const { name, phone, address } = req.body;

    db.prepare(
      'UPDATE business SET name = ?, phone = ?, address = ? WHERE id = 1'
    ).run(
      name || 'My Business',
      phone || '',
      address || ''
    );

    const updated = db.prepare('SELECT * FROM business WHERE id = 1').get();
    res.json({
      name: updated.name,
      phone: updated.phone,
      address: updated.address
    });
  } catch (err) {
    console.error('PUT /api/business error:', err.message);
    res.status(500).json({ error: 'Failed to update business profile' });
  }
});

module.exports = router;
