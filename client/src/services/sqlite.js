import { Capacitor } from '@capacitor/core';
import { CapacitorSQLite, SQLiteConnection } from '@capacitor-community/sqlite';

const isNative = Capacitor.isNativePlatform();
let sqlite = null;
let db = null;

const DB_NAME = 'anjanikkal_db';

export async function initDB() {
  if (!isNative) return;

  try {
    sqlite = new SQLiteConnection(CapacitorSQLite);
    const ret = await sqlite.checkConnectionsConsistency();
    const isConn = (await sqlite.isConnection(DB_NAME, false)).result;

    if (ret.result && isConn) {
      db = await sqlite.retrieveConnection(DB_NAME, false);
    } else {
      db = await sqlite.createConnection(DB_NAME, false, 'no-encryption', 1, false);
    }

    await db.open();

    const queries = [
      `CREATE TABLE IF NOT EXISTS items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE,
        category TEXT,
        sale_price REAL NOT NULL DEFAULT 0,
        purchase_price REAL DEFAULT 0,
        stock REAL DEFAULT 0,
        created_at TEXT DEFAULT (datetime('now')),
        updated_at TEXT DEFAULT (datetime('now'))
      );`,
      `CREATE TABLE IF NOT EXISTS transactions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        type TEXT NOT NULL DEFAULT 'SALE',
        date TEXT NOT NULL,
        total REAL NOT NULL DEFAULT 0,
        balance REAL DEFAULT 0,
        created_at TEXT DEFAULT (datetime('now'))
      );`,
      `CREATE TABLE IF NOT EXISTS transaction_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        transaction_id INTEGER NOT NULL,
        item_id INTEGER NOT NULL,
        item_name TEXT NOT NULL,
        quantity REAL NOT NULL DEFAULT 1,
        rate REAL NOT NULL DEFAULT 0,
        amount REAL NOT NULL DEFAULT 0,
        FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
      );`,
      `CREATE TABLE IF NOT EXISTS business (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        name TEXT DEFAULT 'My Business',
        phone TEXT DEFAULT '',
        address TEXT DEFAULT ''
      );`,
      `INSERT OR IGNORE INTO business (id, name, phone, address) VALUES (1, 'My Business', '', '');`
    ];

    for (const q of queries) {
      await db.execute(q);
    }
  } catch (err) {
    console.error('SQLite Init Error:', err);
    throw err;
  }
}

export const sqlItemsApi = {
  getAll: async (search = '') => {
    if (!db) return [];
    let query = 'SELECT * FROM items';
    let values = [];
    if (search) {
      query += ' WHERE name LIKE ?';
      values = [`%${search}%`];
    }
    const res = await db.query(query, values);
    return (res.values || []).map(i => ({
      ...i,
      salePrice: i.sale_price,
      purchasePrice: i.purchase_price
    }));
  },
  getOne: async (id) => {
    if (!db) return null;
    const res = await db.query('SELECT * FROM items WHERE id = ?', [id]);
    if (res.values?.[0]) {
      const i = res.values[0];
      return { ...i, salePrice: i.sale_price, purchasePrice: i.purchase_price };
    }
    return null;
  },
  create: async (item) => {
    if (!db) return null;
    const { name, category, salePrice, purchasePrice, stock } = item;
    const query = 'INSERT INTO items (name, category, sale_price, purchase_price, stock) VALUES (?, ?, ?, ?, ?)';
    const values = [name, category || 'General', salePrice || 0, purchasePrice || 0, stock || 0];
    const res = await db.run(query, values);
    return { id: res.changes.lastId, ...item };
  },
  update: async (id, item) => {
    if (!db) return null;
    const { name, category, salePrice, purchasePrice, stock } = item;
    const query = 'UPDATE items SET name=?, category=?, sale_price=?, purchase_price=?, stock=?, updated_at=datetime("now") WHERE id=?';
    const values = [name, category, salePrice, purchasePrice, stock, id];
    await db.run(query, values);
    return { id, ...item };
  },
  delete: async (id) => {
    if (!db) return false;
    await db.run('DELETE FROM items WHERE id=?', [id]);
    return true;
  }
};

export const sqlTransactionsApi = {
  getAll: async (limit) => {
    if (!db) return [];
    let query = 'SELECT * FROM transactions ORDER BY id DESC';
    if (limit) query += ` LIMIT ${limit}`;
    const res = await db.query(query);
    const txns = res.values || [];
    for (let t of txns) {
      const itemsRes = await db.query('SELECT * FROM transaction_items WHERE transaction_id = ?', [t.id]);
      t.items = (itemsRes.values || []).map(li => ({
        itemId: li.item_id,
        itemName: li.item_name,
        quantity: li.quantity,
        rate: li.rate,
        amount: li.amount
      }));
    }
    return txns;
  },
  getOne: async (id) => {
    if (!db) return null;
    const txRes = await db.query('SELECT * FROM transactions WHERE id = ?', [id]);
    if (!txRes.values?.[0]) return null;
    const txn = txRes.values[0];
    const itemsRes = await db.query('SELECT * FROM transaction_items WHERE transaction_id = ?', [id]);
    txn.items = (itemsRes.values || []).map(li => ({
      itemId: li.item_id,
      itemName: li.item_name,
      quantity: li.quantity,
      rate: li.rate,
      amount: li.amount
    }));
    return txn;
  },
  create: async (items, balance = 0) => {
    if (!db) throw new Error('Database not connected');
    const total = items.reduce((sum, item) => sum + (item.amount || 0), 0);
    const date = new Date().toISOString();
    const txRes = await db.run('INSERT INTO transactions (type, date, total, balance) VALUES (?, ?, ?, ?)', ['SALE', date, total, balance]);
    const txId = txRes.changes.lastId;
    for (const item of items) {
      await db.run('INSERT INTO transaction_items (transaction_id, item_id, item_name, quantity, rate, amount) VALUES (?, ?, ?, ?, ?, ?)',
        [txId, item.itemId || 0, item.itemName, item.quantity, item.rate, item.amount]);
      if (item.itemId) {
        await db.run('UPDATE items SET stock = stock - ? WHERE id = ?', [item.quantity, item.itemId]);
      }
    }
    return { id: txId, type: 'SALE', date, total, balance, items };
  },
  update: async (id, items, balance = 0) => {
    if (!db) throw new Error('Database not connected');

    // 1. Revert stock of existing transaction items
    const oldItemsRes = await db.query('SELECT * FROM transaction_items WHERE transaction_id = ?', [id]);
    const oldItems = oldItemsRes.values || [];
    for (const oldItem of oldItems) {
      if (oldItem.item_id) {
        await db.run('UPDATE items SET stock = stock + ? WHERE id = ?', [oldItem.quantity, oldItem.item_id]);
      }
    }

    // 2. Delete old transaction items
    await db.run('DELETE FROM transaction_items WHERE transaction_id = ?', [id]);

    // 3. Calculate new total
    const total = items.reduce((sum, item) => sum + (item.amount || 0), 0);

    // 4. Update transactions record (total and balance)
    await db.run('UPDATE transactions SET total = ?, balance = ? WHERE id = ?', [total, balance, id]);

    // 5. Insert new line items and deduct stock
    for (const item of items) {
      await db.run('INSERT INTO transaction_items (transaction_id, item_id, item_name, quantity, rate, amount) VALUES (?, ?, ?, ?, ?, ?)',
        [id, item.itemId || 0, item.itemName, item.quantity, item.rate, item.amount]);
      if (item.itemId) {
        await db.run('UPDATE items SET stock = stock - ? WHERE id = ?', [item.quantity, item.itemId]);
      }
    }

    return { id, type: 'SALE', total, balance, items };
  },
  getStats: async () => {
    if (!db) return { todaySales: 0, totalRevenue: 0, totalItemsSold: 0, totalTransactions: 0, lowStockCount: 0 };
    const today = new Date().toISOString().split('T')[0];
    const tS = await db.query('SELECT SUM(total) as val FROM transactions WHERE date LIKE ?', [`${today}%`]);
    const tR = await db.query('SELECT SUM(total) as val FROM transactions');
    const tT = await db.query('SELECT COUNT(*) as val FROM transactions');
    const iS = await db.query('SELECT SUM(quantity) as val FROM transaction_items');
    const lS = await db.query('SELECT COUNT(*) as val FROM items WHERE stock <= 5');
    const iV = await db.query('SELECT SUM(sale_price * stock) as val FROM items');

    const totalRevenue = tR.values?.[0]?.val || 0;
    const totalTransactions = tT.values?.[0]?.val || 0;

    return {
      todaySales: tS.values?.[0]?.val || 0,
      totalRevenue,
      totalTransactions,
      totalItemsSold: iS.values?.[0]?.val || 0,
      lowStockCount: lS.values?.[0]?.val || 0,
      inventoryValue: iV.values?.[0]?.val || 0,
      avgInvoice: totalTransactions ? (totalRevenue / totalTransactions) : 0
    };
  },
  delete: async (id) => {
    if (!db) throw new Error('Database not connected');

    // 1. Revert stock of existing transaction items
    const itemsRes = await db.query('SELECT * FROM transaction_items WHERE transaction_id = ?', [id]);
    const items = itemsRes.values || [];
    for (const item of items) {
      if (item.item_id) {
        await db.run('UPDATE items SET stock = stock + ? WHERE id = ?', [item.quantity, item.item_id]);
      }
    }

    // 2. Delete transaction (cascades to transaction_items if foreign key is set up with ON DELETE CASCADE)
    // Just in case cascade is not working or not trusted:
    await db.run('DELETE FROM transaction_items WHERE transaction_id = ?', [id]);
    await db.run('DELETE FROM transactions WHERE id = ?', [id]);

    return true;
  }
};

export const sqlBusinessApi = {
  get: async () => {
    if (!db) return { name: 'My Business', phone: '', address: '' };
    const res = await db.query('SELECT * FROM business WHERE id = 1');
    return res.values?.[0] || { name: 'My Business', phone: '', address: '' };
  },
  update: async (data) => {
    if (!db) return data;
    await db.run('UPDATE business SET name=?, phone=?, address=? WHERE id=1', [data.name, data.phone, data.address]);
    return data;
  }
};
