// =============================================================================
//  ANJANIKKAL SERVER — Express entry point
// =============================================================================

require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');

// Initialize database (creates tables on first run)
require('./database');

const app = express();
const PORT = process.env.PORT || 5000;

// =============================================================================
//  MIDDLEWARE
// =============================================================================

// CORS — allow requests from Vite dev server
app.use(cors({
  origin: ['http://localhost:5173', 'http://localhost:5174', 'http://localhost:3000'],
  credentials: true
}));

// Parse JSON bodies
app.use(express.json());

// Request logging (development)
app.use((req, res, next) => {
  const timestamp = new Date().toLocaleTimeString();
  console.log(`[${timestamp}] ${req.method} ${req.url}`);
  next();
});

// =============================================================================
//  API ROUTES
// =============================================================================

app.use('/api/items', require('./routes/items'));
app.use('/api/transactions', require('./routes/transactions'));
app.use('/api/business', require('./routes/business'));

// ---------------------------------------------------------------------------
//  DATA MANAGEMENT ENDPOINTS
// ---------------------------------------------------------------------------

// POST /api/seed — Load demo data
app.post('/api/seed', (req, res) => {
  try {
    const db = require('./database');

    const demoItems = [
      { name: 'BISCUITS (20-20)', category: 'Snacks', salePrice: 10, purchasePrice: 7, stock: 150 },
      { name: 'PARLE-G GLUCOSE', category: 'Snacks', salePrice: 5, purchasePrice: 3.5, stock: 200 },
      { name: 'MAGGI 2-MIN NOODLES', category: 'Groceries', salePrice: 14, purchasePrice: 11, stock: 80 },
      { name: 'THUMS UP 500ML', category: 'Beverages', salePrice: 40, purchasePrice: 32, stock: 60 },
      { name: 'COCA COLA CAN 300ML', category: 'Beverages', salePrice: 35, purchasePrice: 28, stock: 45 },
      { name: 'DAIRY MILK SILK', category: 'Snacks', salePrice: 80, purchasePrice: 65, stock: 30 },
      { name: 'LUX BEAUTY SOAP', category: 'Personal Care', salePrice: 45, purchasePrice: 35, stock: 70 },
      { name: 'SURF EXCEL 1KG', category: 'Groceries', salePrice: 120, purchasePrice: 95, stock: 25 },
      { name: 'AMUL BUTTER 100G', category: 'Groceries', salePrice: 56, purchasePrice: 48, stock: 40 },
      { name: 'TATA TEA GOLD 250G', category: 'Groceries', salePrice: 95, purchasePrice: 78, stock: 35 },
      { name: 'BRITANNIA BREAD', category: 'Groceries', salePrice: 35, purchasePrice: 28, stock: 20 },
      { name: 'LAYS MAGIC MASALA', category: 'Snacks', salePrice: 20, purchasePrice: 15, stock: 100 },
      { name: 'KURKURE MASALA MUNCH', category: 'Snacks', salePrice: 20, purchasePrice: 15, stock: 90 },
      { name: 'COLGATE STRONG TEETH', category: 'Personal Care', salePrice: 55, purchasePrice: 42, stock: 50 },
      { name: 'DETTOL HANDWASH 250ML', category: 'Personal Care', salePrice: 65, purchasePrice: 50, stock: 3 }
    ];

    // Insert items (skip duplicates)
    const insertItem = db.prepare(
      'INSERT OR IGNORE INTO items (name, category, sale_price, purchase_price, stock) VALUES (?, ?, ?, ?, ?)'
    );
    let itemsAdded = 0;
    for (const item of demoItems) {
      const result = insertItem.run(item.name, item.category, item.salePrice, item.purchasePrice, item.stock);
      if (result.changes > 0) itemsAdded++;
    }

    // Create demo transactions
    const demoTxns = [
      { hoursAgo: 1, lineItems: [
        { name: 'PARLE-G GLUCOSE', qty: 10 },
        { name: 'MAGGI 2-MIN NOODLES', qty: 5 },
        { name: 'LAYS MAGIC MASALA', qty: 8 }
      ]},
      { hoursAgo: 3, lineItems: [
        { name: 'THUMS UP 500ML', qty: 3 },
        { name: 'DAIRY MILK SILK', qty: 2 }
      ]},
      { hoursAgo: 25, lineItems: [
        { name: 'SURF EXCEL 1KG', qty: 1 },
        { name: 'LUX BEAUTY SOAP', qty: 3 },
        { name: 'COLGATE STRONG TEETH', qty: 2 }
      ]},
      { hoursAgo: 30, lineItems: [
        { name: 'BISCUITS (20-20)', qty: 20 },
        { name: 'TATA TEA GOLD 250G', qty: 2 }
      ]},
      { hoursAgo: 50, lineItems: [
        { name: 'AMUL BUTTER 100G', qty: 5 },
        { name: 'BRITANNIA BREAD', qty: 3 },
        { name: 'DETTOL HANDWASH 250ML', qty: 2 }
      ]},
      { hoursAgo: 72, lineItems: [
        { name: 'KURKURE MASALA MUNCH', qty: 12 },
        { name: 'COCA COLA CAN 300ML', qty: 6 },
        { name: 'DAIRY MILK SILK', qty: 1 }
      ]}
    ];

    const insertTxn = db.prepare(
      'INSERT INTO transactions (type, date, total, balance) VALUES (?, ?, ?, ?)'
    );
    const insertLineItem = db.prepare(
      'INSERT INTO transaction_items (transaction_id, item_id, item_name, quantity, rate, amount) VALUES (?, ?, ?, ?, ?, ?)'
    );

    let txnsAdded = 0;
    const allItems = db.prepare('SELECT * FROM items').all();

    for (const dt of demoTxns) {
      const date = new Date(Date.now() - dt.hoursAgo * 3600 * 1000).toISOString();
      let total = 0;
      const lineItems = [];

      for (const li of dt.lineItems) {
        const item = allItems.find(i => i.name === li.name);
        if (!item) continue;
        const amount = li.qty * item.sale_price;
        total += amount;
        lineItems.push({
          itemId: item.id,
          itemName: item.name,
          quantity: li.qty,
          rate: item.sale_price,
          amount
        });
      }

      if (lineItems.length === 0) continue;

      const result = insertTxn.run('SALE', date, total, 0);
      const txnId = result.lastInsertRowid;

      for (const li of lineItems) {
        insertLineItem.run(txnId, li.itemId, li.itemName, li.quantity, li.rate, li.amount);
      }
      txnsAdded++;
    }

    res.json({
      message: `Demo data loaded! ${itemsAdded} items, ${txnsAdded} transactions added`,
      itemsAdded,
      transactionsAdded: txnsAdded
    });
  } catch (err) {
    console.error('POST /api/seed error:', err.message);
    res.status(500).json({ error: 'Failed to load demo data' });
  }
});

// POST /api/reset — Reset all data
app.post('/api/reset', (req, res) => {
  try {
    const db = require('./database');
    db.exec('DELETE FROM transaction_items');
    db.exec('DELETE FROM transactions');
    db.exec('DELETE FROM items');
    db.exec("UPDATE business SET name = 'My Business', phone = '', address = '' WHERE id = 1");
    res.json({ message: 'All data has been reset' });
  } catch (err) {
    console.error('POST /api/reset error:', err.message);
    res.status(500).json({ error: 'Failed to reset data' });
  }
});

// GET /api/export — Export all data as JSON
app.get('/api/export', (req, res) => {
  try {
    const db = require('./database');
    const items = db.prepare('SELECT * FROM items').all();
    const transactions = db.prepare('SELECT * FROM transactions ORDER BY id').all();
    const txnItems = db.prepare('SELECT * FROM transaction_items ORDER BY transaction_id').all();
    const business = db.prepare('SELECT * FROM business WHERE id = 1').get();

    res.json({
      items,
      transactions,
      transactionItems: txnItems,
      business,
      exportDate: new Date().toISOString()
    });
  } catch (err) {
    console.error('GET /api/export error:', err.message);
    res.status(500).json({ error: 'Failed to export data' });
  }
});

// POST /api/import — Import all data from JSON backup
app.post('/api/import', (req, res) => {
  try {
    const db = require('./database');
    const { items, transactions, transactionItems, business } = req.body;

    db.transaction(() => {
      // 1. Clear existing data
      db.exec('DELETE FROM transaction_items');
      db.exec('DELETE FROM transactions');
      db.exec('DELETE FROM items');

      // 2. Import business profile
      if (business) {
        db.prepare('UPDATE business SET name = ?, phone = ?, address = ? WHERE id = 1')
          .run(business.name || 'My Business', business.phone || '', business.address || '');
      }

      // 3. Import items
      if (items && Array.isArray(items)) {
        const insertItem = db.prepare(
          'INSERT OR REPLACE INTO items (id, name, category, sale_price, purchase_price, stock) VALUES (?, ?, ?, ?, ?, ?)'
        );
        for (const item of items) {
          insertItem.run(item.id, item.name, item.category, item.sale_price, item.purchase_price, item.stock);
        }
      }

      // 4. Import transactions
      if (transactions && Array.isArray(transactions)) {
        const insertTxn = db.prepare(
          'INSERT OR REPLACE INTO transactions (id, type, date, total, balance) VALUES (?, ?, ?, ?, ?)'
        );
        for (const txn of transactions) {
          insertTxn.run(txn.id, txn.type || 'SALE', txn.date, txn.total, txn.balance || 0);
        }
      }

      // 5. Import transaction items
      if (transactionItems && Array.isArray(transactionItems)) {
        const insertTxnItem = db.prepare(
          'INSERT OR REPLACE INTO transaction_items (id, transaction_id, item_id, item_name, quantity, rate, amount) VALUES (?, ?, ?, ?, ?, ?, ?)'
        );
        for (const ti of transactionItems) {
          insertTxnItem.run(ti.id, ti.transaction_id, ti.item_id, ti.item_name, ti.quantity, ti.rate, ti.amount);
        }
      }
    })();

    res.json({ message: 'Data imported successfully!' });
  } catch (err) {
    console.error('POST /api/import error:', err.message);
    res.status(500).json({ error: 'Failed to import data: ' + err.message });
  }
});

// =============================================================================
//  SERVE REACT BUILD (Production)
// =============================================================================

const clientBuildPath = path.join(__dirname, '..', 'client', 'dist');
app.use(express.static(clientBuildPath));

// Catch-all: serve React app for any non-API routes
app.get('*', (req, res) => {
  const indexPath = path.join(clientBuildPath, 'index.html');
  if (require('fs').existsSync(indexPath)) {
    res.sendFile(indexPath);
  } else {
    res.status(200).json({
      message: 'Anjanikkal API is running!',
      docs: {
        items: 'GET/POST /api/items',
        transactions: 'GET/POST /api/transactions',
        business: 'GET/PUT /api/business',
        stats: 'GET /api/transactions/stats/summary',
        seed: 'POST /api/seed',
        reset: 'POST /api/reset',
        export: 'GET /api/export'
      }
    });
  }
});

// =============================================================================
//  START SERVER
// =============================================================================

app.listen(PORT, () => {
  console.log('');
  console.log('  ╔══════════════════════════════════════╗');
  console.log('  ║      Anjanikkal Server v1.0          ║');
  console.log(`  ║  Running on http://localhost:${PORT}    ║`);
  console.log('  ║  Database: SQLite (data/anjanikkal.db)║');
  console.log('  ╚══════════════════════════════════════╝');
  console.log('');
});
