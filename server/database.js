// =============================================================================
//  DATABASE MODULE — SQLite setup, table creation, and helper functions
// =============================================================================

const { DatabaseSync } = require('node:sqlite');
const path = require('path');
const fs = require('fs');

// Ensure data directory exists
const dataDir = path.join(__dirname, 'data');
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}

const dbPath = process.env.DB_PATH || path.join(dataDir, 'anjanikkal.db');
const db = new DatabaseSync(dbPath);

// Enable WAL mode for better performance
db.exec('PRAGMA journal_mode = WAL');
db.exec('PRAGMA foreign_keys = ON');

// Add compatibility helper for better-sqlite3 transactions
db.transaction = (fn) => {
  return (...args) => {
    let inTransaction = false;
    try {
      db.exec('BEGIN TRANSACTION');
      inTransaction = true;
      const result = fn(...args);
      db.exec('COMMIT');
      return result;
    } catch (err) {
      if (inTransaction) {
        db.exec('ROLLBACK');
      }
      throw err;
    }
  };
};

// =============================================================================
//  SCHEMA MIGRATION DETECTOR
// =============================================================================
try {
  const tableInfo = db.prepare("PRAGMA table_info(items)").all();
  const hasCodeColumn = tableInfo.some(col => col.name === 'code');
  if (hasCodeColumn) {
    console.log("Old schema detected (using item codes). Dropping tables to recreate...");
    db.exec(`
      DROP TABLE IF EXISTS transaction_items;
      DROP TABLE IF EXISTS transactions;
      DROP TABLE IF EXISTS items;
    `);
  }
} catch (e) {
  // Tables do not exist yet
}

// =============================================================================
//  TABLE CREATION
// =============================================================================

db.exec(`
  CREATE TABLE IF NOT EXISTS items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    category TEXT,
    sale_price REAL NOT NULL DEFAULT 0,
    purchase_price REAL DEFAULT 0,
    stock REAL DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL DEFAULT 'SALE',
    date TEXT NOT NULL,
    total REAL NOT NULL DEFAULT 0,
    balance REAL DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS transaction_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    item_name TEXT NOT NULL,
    quantity REAL NOT NULL DEFAULT 1,
    rate REAL NOT NULL DEFAULT 0,
    amount REAL NOT NULL DEFAULT 0,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE RESTRICT
  );

  CREATE TABLE IF NOT EXISTS business (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    name TEXT DEFAULT 'My Business',
    phone TEXT DEFAULT '',
    address TEXT DEFAULT ''
  );
`);

// Seed default business row if it doesn't exist
const bizRow = db.prepare('SELECT id FROM business WHERE id = 1').get();
if (!bizRow) {
  db.prepare('INSERT INTO business (id, name, phone, address) VALUES (1, ?, ?, ?)')
    .run('My Business', '', '');
}

// =============================================================================
//  EXPORT DATABASE INSTANCE
// =============================================================================

module.exports = db;
