// =============================================================================
//  ITEMS ROUTES — CRUD API for inventory items (using Auto ID and Categories)
// =============================================================================

const express = require('express');
const router = express.Router();
const db = require('../database');

// ---------------------------------------------------------------------------
//  GET /api/items — List all items (optional ?search= filter)
// ---------------------------------------------------------------------------
router.get('/', (req, res) => {
  try {
    const { search } = req.query;
    let items;

    if (search && search.trim()) {
      const q = `%${search.trim()}%`;
      items = db.prepare(
        'SELECT * FROM items WHERE name LIKE ? ORDER BY name ASC'
      ).all(q);
    } else {
      items = db.prepare('SELECT * FROM items ORDER BY name ASC').all();
    }

    // Convert DB column names to camelCase for frontend
    const result = items.map(i => ({
      id: i.id,
      name: i.name,
      category: i.category || '',
      salePrice: i.sale_price,
      purchasePrice: i.purchase_price,
      stock: i.stock,
      createdAt: i.created_at,
      updatedAt: i.updated_at
    }));

    res.json(result);
  } catch (err) {
    console.error('GET /api/items error:', err.message);
    res.status(500).json({ error: 'Failed to fetch items' });
  }
});

// ---------------------------------------------------------------------------
//  GET /api/items/:id — Get single item by ID
// ---------------------------------------------------------------------------
router.get('/:id', (req, res) => {
  try {
    const item = db.prepare('SELECT * FROM items WHERE id = ?').get(req.params.id);
    if (!item) {
      return res.status(404).json({ error: 'Item not found' });
    }
    res.json({
      id: item.id,
      name: item.name,
      category: item.category || '',
      salePrice: item.sale_price,
      purchasePrice: item.purchase_price,
      stock: item.stock,
      createdAt: item.created_at,
      updatedAt: item.updated_at
    });
  } catch (err) {
    console.error('GET /api/items/:id error:', err.message);
    res.status(500).json({ error: 'Failed to fetch item' });
  }
});

// ---------------------------------------------------------------------------
//  POST /api/items — Create a new item
// ---------------------------------------------------------------------------
router.post('/', (req, res) => {
  try {
    const { name, category, salePrice, purchasePrice, stock } = req.body;

    if (!name || !name.trim()) {
      return res.status(400).json({ error: 'Item name is required' });
    }
    if (salePrice === undefined || salePrice <= 0) {
      return res.status(400).json({ error: 'Sale price must be greater than 0' });
    }

    // Check for duplicate name
    const existing = db.prepare('SELECT id FROM items WHERE LOWER(name) = LOWER(?)').get(name.trim());
    if (existing) {
      return res.status(409).json({ error: 'Item name already exists' });
    }

    const result = db.prepare(
      'INSERT INTO items (name, category, sale_price, purchase_price, stock) VALUES (?, ?, ?, ?, ?)'
    ).run(name.trim(), category ? category.trim() : '', salePrice || 0, purchasePrice || 0, stock || 0);

    const itemId = result.lastInsertRowid;
    const newItem = db.prepare('SELECT * FROM items WHERE id = ?').get(itemId);

    res.status(201).json({
      id: newItem.id,
      name: newItem.name,
      category: newItem.category || '',
      salePrice: newItem.sale_price,
      purchasePrice: newItem.purchase_price,
      stock: newItem.stock
    });
  } catch (err) {
    console.error('POST /api/items error:', err.message);
    res.status(500).json({ error: 'Failed to create item' });
  }
});

// ---------------------------------------------------------------------------
//  PUT /api/items/:id — Update an existing item
// ---------------------------------------------------------------------------
router.put('/:id', (req, res) => {
  try {
    const { name, category, salePrice, purchasePrice, stock } = req.body;
    const { id } = req.params;

    const existing = db.prepare('SELECT * FROM items WHERE id = ?').get(id);
    if (!existing) {
      return res.status(404).json({ error: 'Item not found' });
    }

    // Check duplicate name if name is changing
    if (name && name.trim().toLowerCase() !== existing.name.toLowerCase()) {
      const duplicate = db.prepare('SELECT id FROM items WHERE LOWER(name) = LOWER(?)').get(name.trim());
      if (duplicate) {
        return res.status(409).json({ error: 'Another item with this name already exists' });
      }
    }

    db.prepare(
      `UPDATE items SET 
        name = ?, 
        category = ?,
        sale_price = ?, 
        purchase_price = ?, 
        stock = ?,
        updated_at = datetime('now')
      WHERE id = ?`
    ).run(
      name ? name.trim() : existing.name,
      category !== undefined ? category.trim() : existing.category,
      salePrice !== undefined ? salePrice : existing.sale_price,
      purchasePrice !== undefined ? purchasePrice : existing.purchase_price,
      stock !== undefined ? stock : existing.stock,
      id
    );

    const updated = db.prepare('SELECT * FROM items WHERE id = ?').get(id);
    res.json({
      id: updated.id,
      name: updated.name,
      category: updated.category || '',
      salePrice: updated.sale_price,
      purchasePrice: updated.purchase_price,
      stock: updated.stock
    });
  } catch (err) {
    console.error('PUT /api/items/:id error:', err.message);
    res.status(500).json({ error: 'Failed to update item' });
  }
});

// ---------------------------------------------------------------------------
//  DELETE /api/items/:id — Delete an item
// ---------------------------------------------------------------------------
router.delete('/:id', (req, res) => {
  try {
    const existing = db.prepare('SELECT * FROM items WHERE id = ?').get(req.params.id);
    if (!existing) {
      return res.status(404).json({ error: 'Item not found' });
    }

    db.prepare('DELETE FROM items WHERE id = ?').run(req.params.id);
    res.json({ message: 'Item deleted successfully' });
  } catch (err) {
    console.error('DELETE /api/items/:id error:', err.message);
    res.status(500).json({ error: 'Failed to delete item' });
  }
});

module.exports = router;
