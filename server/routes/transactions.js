// =============================================================================
//  TRANSACTIONS ROUTES — Sales API with stock deduction and stats (ID-based)
// =============================================================================

const express = require('express');
const router = express.Router();
const db = require('../database');

// ---------------------------------------------------------------------------
//  GET /api/transactions/stats/summary — Dashboard statistics
// ---------------------------------------------------------------------------
router.get('/stats/summary', (req, res) => {
  try {
    // Total revenue
    const revenueRow = db.prepare('SELECT COALESCE(SUM(total), 0) as total FROM transactions').get();

    // Transaction count
    const countRow = db.prepare('SELECT COUNT(*) as count FROM transactions').get();

    // Average invoice
    const avgInvoice = countRow.count > 0 ? revenueRow.total / countRow.count : 0;

    // Total items sold (quantity)
    const soldRow = db.prepare('SELECT COALESCE(SUM(quantity), 0) as total FROM transaction_items').get();

    // Today's sales
    const todayRow = db.prepare(
      "SELECT COALESCE(SUM(total), 0) as total FROM transactions WHERE date(date) = date('now', 'localtime')"
    ).get();

    // Inventory value (sale_price * stock for positive stock)
    const invRow = db.prepare(
      'SELECT COALESCE(SUM(sale_price * CASE WHEN stock > 0 THEN stock ELSE 0 END), 0) as total FROM items'
    ).get();

    // Low stock count (stock <= 5)
    const lowStockRow = db.prepare('SELECT COUNT(*) as count FROM items WHERE stock <= 5').get();

    // Top selling items (top 5)
    const topItems = db.prepare(`
      SELECT ti.item_id, ti.item_name, SUM(ti.quantity) as total_qty
      FROM transaction_items ti
      GROUP BY ti.item_id
      ORDER BY total_qty DESC
      LIMIT 5
    `).all();

    // Low stock items
    const lowStockItems = db.prepare(
      'SELECT id, name, stock, category FROM items WHERE stock <= 5 ORDER BY stock ASC'
    ).all();

    res.json({
      totalRevenue: revenueRow.total,
      totalTransactions: countRow.count,
      avgInvoice: Math.round(avgInvoice),
      totalItemsSold: soldRow.total,
      todaySales: todayRow.total,
      inventoryValue: invRow.total,
      lowStockCount: lowStockRow.count,
      topItems: topItems.map(t => ({
        itemId: t.item_id,
        itemName: t.item_name,
        totalQty: t.total_qty
      })),
      lowStockItems: lowStockItems.map(i => ({
        id: i.id,
        name: i.name,
        stock: i.stock,
        category: i.category || ''
      }))
    });
  } catch (err) {
    console.error('GET /api/transactions/stats/summary error:', err.message);
    res.status(500).json({ error: 'Failed to fetch stats' });
  }
});

// ---------------------------------------------------------------------------
//  GET /api/transactions — List all transactions with line items
// ---------------------------------------------------------------------------
router.get('/', (req, res) => {
  try {
    const { limit } = req.query;
    let txns;

    if (limit) {
      txns = db.prepare('SELECT * FROM transactions ORDER BY id DESC LIMIT ?').all(parseInt(limit));
    } else {
      txns = db.prepare('SELECT * FROM transactions ORDER BY id DESC').all();
    }

    // Fetch line items for each transaction
    const getLineItems = db.prepare(
      'SELECT * FROM transaction_items WHERE transaction_id = ?'
    );

    const result = txns.map(t => ({
      id: t.id,
      type: t.type,
      date: t.date,
      total: t.total,
      balance: t.balance,
      items: getLineItems.all(t.id).map(li => ({
        itemId: li.item_id,
        itemName: li.item_name,
        quantity: li.quantity,
        rate: li.rate,
        amount: li.amount
      }))
    }));

    res.json(result);
  } catch (err) {
    console.error('GET /api/transactions error:', err.message);
    res.status(500).json({ error: 'Failed to fetch transactions' });
  }
});

// ---------------------------------------------------------------------------
//  GET /api/transactions/:id — Get single transaction with line items
// ---------------------------------------------------------------------------
router.get('/:id', (req, res) => {
  try {
    const txn = db.prepare('SELECT * FROM transactions WHERE id = ?').get(req.params.id);
    if (!txn) {
      return res.status(404).json({ error: 'Transaction not found' });
    }

    const lineItems = db.prepare(
      'SELECT * FROM transaction_items WHERE transaction_id = ?'
    ).all(txn.id);

    res.json({
      id: txn.id,
      type: txn.type,
      date: txn.date,
      total: txn.total,
      balance: txn.balance,
      items: lineItems.map(li => ({
        itemId: li.item_id,
        itemName: li.item_name,
        quantity: li.quantity,
        rate: li.rate,
        amount: li.amount
      }))
    });
  } catch (err) {
    console.error('GET /api/transactions/:id error:', err.message);
    res.status(500).json({ error: 'Failed to fetch transaction' });
  }
});

// ---------------------------------------------------------------------------
//  POST /api/transactions — Create a new sale transaction
//  Body: { items: [{ itemId, itemName, quantity, rate, amount }], balance }
//  Automatically deducts stock from items table
// ---------------------------------------------------------------------------
router.post('/', (req, res) => {
  try {
    const { items, balance } = req.body;

    if (!items || !Array.isArray(items) || items.length === 0) {
      return res.status(400).json({ error: 'At least one item is required' });
    }

    // Validate each line item
    for (const li of items) {
      if (!li.itemId) return res.status(400).json({ error: 'Item ID is required for all items' });
      if (li.quantity === undefined || li.quantity <= 0) return res.status(400).json({ error: 'Quantity must be greater than 0' });
      if (!li.rate || li.rate <= 0) return res.status(400).json({ error: 'Rate must be greater than 0' });
    }

    const total = items.reduce((sum, li) => sum + (li.quantity * li.rate), 0);
    const date = new Date().toISOString();
    const resolvedBalance = balance !== undefined ? balance : 0;

    // Use a database transaction for atomicity
    const insertTxn = db.transaction(() => {
      // Insert the transaction
      const result = db.prepare(
        'INSERT INTO transactions (type, date, total, balance) VALUES (?, ?, ?, ?)'
      ).run('SALE', date, total, resolvedBalance);

      const txnId = result.lastInsertRowid;

      // Insert line items and deduct stock
      const insertLineItem = db.prepare(
        'INSERT INTO transaction_items (transaction_id, item_id, item_name, quantity, rate, amount) VALUES (?, ?, ?, ?, ?, ?)'
      );
      const deductStock = db.prepare(
        "UPDATE items SET stock = stock - ?, updated_at = datetime('now') WHERE id = ?"
      );

      for (const li of items) {
        const amount = li.quantity * li.rate;
        insertLineItem.run(txnId, li.itemId, li.itemName, li.quantity, li.rate, amount);
        deductStock.run(li.quantity, li.itemId);
      }

      return txnId;
    });

    const txnId = insertTxn();

    // Fetch the created transaction
    const txn = db.prepare('SELECT * FROM transactions WHERE id = ?').get(txnId);
    const lineItems = db.prepare('SELECT * FROM transaction_items WHERE transaction_id = ?').all(txnId);

    res.status(201).json({
      id: txn.id,
      type: txn.type,
      date: txn.date,
      total: txn.total,
      balance: txn.balance,
      items: lineItems.map(li => ({
        itemId: li.item_id,
        itemName: li.item_name,
        quantity: li.quantity,
        rate: li.rate,
        amount: li.amount
      }))
    });
  } catch (err) {
    console.error('POST /api/transactions error:', err.message);
    res.status(500).json({ error: 'Failed to create transaction' });
  }
});

// ---------------------------------------------------------------------------
//  PUT /api/transactions/:id — Update an existing transaction
// ---------------------------------------------------------------------------
router.put('/:id', (req, res) => {
  try {
    const { items, balance } = req.body;
    const txnId = req.params.id;

    // Check if transaction exists
    const existingTxn = db.prepare('SELECT * FROM transactions WHERE id = ?').get(txnId);
    if (!existingTxn) {
      return res.status(404).json({ error: 'Transaction not found' });
    }

    if (!items || !Array.isArray(items) || items.length === 0) {
      return res.status(400).json({ error: 'At least one item is required' });
    }

    // Validate each line item
    for (const li of items) {
      if (!li.itemId) return res.status(400).json({ error: 'Item ID is required for all items' });
      if (li.quantity === undefined || li.quantity <= 0) return res.status(400).json({ error: 'Quantity must be greater than 0' });
      if (!li.rate || li.rate <= 0) return res.status(400).json({ error: 'Rate must be greater than 0' });
    }

    const total = items.reduce((sum, li) => sum + (li.quantity * li.rate), 0);
    const resolvedBalance = balance !== undefined ? balance : 0;

    // Use a database transaction to run all updates atomically
    const updateTxn = db.transaction(() => {
      // 1. Get current items to revert their stock
      const oldItems = db.prepare('SELECT * FROM transaction_items WHERE transaction_id = ?').all(txnId);
      
      const addStock = db.prepare(
        "UPDATE items SET stock = stock + ?, updated_at = datetime('now') WHERE id = ?"
      );
      for (const oldLi of oldItems) {
        addStock.run(oldLi.quantity, oldLi.item_id);
      }

      // 2. Delete old transaction items
      db.prepare('DELETE FROM transaction_items WHERE transaction_id = ?').run(txnId);

      // 3. Update the transaction record (total and balance)
      db.prepare(
        'UPDATE transactions SET total = ?, balance = ? WHERE id = ?'
      ).run(total, resolvedBalance, txnId);

      // 4. Insert new items and deduct stock
      const insertLineItem = db.prepare(
        'INSERT INTO transaction_items (transaction_id, item_id, item_name, quantity, rate, amount) VALUES (?, ?, ?, ?, ?, ?)'
      );
      const deductStock = db.prepare(
        "UPDATE items SET stock = stock - ?, updated_at = datetime('now') WHERE id = ?"
      );

      for (const li of items) {
        const amount = li.quantity * li.rate;
        insertLineItem.run(txnId, li.itemId, li.itemName, li.quantity, li.rate, amount);
        deductStock.run(li.quantity, li.itemId);
      }
    });

    updateTxn();

    // Fetch the updated transaction
    const txn = db.prepare('SELECT * FROM transactions WHERE id = ?').get(txnId);
    const lineItems = db.prepare('SELECT * FROM transaction_items WHERE transaction_id = ?').all(txnId);

    res.json({
      id: txn.id,
      type: txn.type,
      date: txn.date,
      total: txn.total,
      balance: txn.balance,
      items: lineItems.map(li => ({
        itemId: li.item_id,
        itemName: li.item_name,
        quantity: li.quantity,
        rate: li.rate,
        amount: li.amount
      }))
    });

  } catch (err) {
    console.error('PUT /api/transactions/:id error:', err.message);
    res.status(500).json({ error: 'Failed to update transaction' });
  }
});

// ---------------------------------------------------------------------------
//  DELETE /api/transactions/:id — Delete a transaction and revert stock
// ---------------------------------------------------------------------------
router.delete('/:id', (req, res) => {
  try {
    const txnId = req.params.id;

    // Check if transaction exists
    const existingTxn = db.prepare('SELECT * FROM transactions WHERE id = ?').get(txnId);
    if (!existingTxn) {
      return res.status(404).json({ error: 'Transaction not found' });
    }

    const deleteTxn = db.transaction(() => {
      // 1. Get current items to revert their stock
      const oldItems = db.prepare('SELECT * FROM transaction_items WHERE transaction_id = ?').all(txnId);
      
      const addStock = db.prepare(
        "UPDATE items SET stock = stock + ?, updated_at = datetime('now') WHERE id = ?"
      );
      for (const oldLi of oldItems) {
        addStock.run(oldLi.quantity, oldLi.item_id);
      }

      // 2. Delete transaction items
      db.prepare('DELETE FROM transaction_items WHERE transaction_id = ?').run(txnId);

      // 3. Delete the transaction record
      db.prepare('DELETE FROM transactions WHERE id = ?').run(txnId);
    });

    deleteTxn();

    res.json({ message: 'Transaction deleted successfully' });
  } catch (err) {
    console.error('DELETE /api/transactions/:id error:', err.message);
    res.status(500).json({ error: 'Failed to delete transaction' });
  }
});

module.exports = router;
