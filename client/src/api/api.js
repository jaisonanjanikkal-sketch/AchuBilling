import { Capacitor } from '@capacitor/core';
import { sqlItemsApi, sqlTransactionsApi, sqlBusinessApi, sqlDataApi } from '../services/sqlite';

// =============================================================================
//  API MODULE — Centralized fetch wrapper for all backend endpoints
//  OR Capacitor SQLite implementation
// =============================================================================

const isNative = Capacitor.isNativePlatform();
const API_BASE = '/api';

async function request(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint}`;
  const config = {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  };

  if (config.body && typeof config.body === 'object') {
    config.body = JSON.stringify(config.body);
  }

  const response = await fetch(url, config);
  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.error || `API error: ${response.status}`);
  }

  return data;
}

// ---------------------------------------------------------------------------
//  ITEMS
// ---------------------------------------------------------------------------
export const itemsApi = {
  getAll: (search = '') => isNative ? sqlItemsApi.getAll(search) : request(`/items${search ? `?search=${encodeURIComponent(search)}` : ''}`),
  getOne: (id) => isNative ? sqlItemsApi.getOne(id) : request(`/items/${encodeURIComponent(id)}`),
  create: (item) => isNative ? sqlItemsApi.create(item) : request('/items', { method: 'POST', body: item }),
  update: (id, item) => isNative ? sqlItemsApi.update(id, item) : request(`/items/${encodeURIComponent(id)}`, { method: 'PUT', body: item }),
  delete: (id) => isNative ? sqlItemsApi.delete(id) : request(`/items/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};

// ---------------------------------------------------------------------------
//  TRANSACTIONS
// ---------------------------------------------------------------------------
export const transactionsApi = {
  getAll: (limit) => isNative ? sqlTransactionsApi.getAll(limit) : request(`/transactions${limit ? `?limit=${limit}` : ''}`),
  getOne: (id) => isNative ? sqlTransactionsApi.getOne(id) : request(`/transactions/${id}`),
  create: (items, balance) => isNative ? sqlTransactionsApi.create(items, balance) : request('/transactions', { method: 'POST', body: { items, balance } }),
  update: (id, items, balance) => isNative ? sqlTransactionsApi.update(id, items, balance) : request(`/transactions/${id}`, { method: 'PUT', body: { items, balance } }),
  delete: (id) => isNative ? sqlTransactionsApi.delete(id) : request(`/transactions/${id}`, { method: 'DELETE' }),
  getStats: () => isNative ? sqlTransactionsApi.getStats() : request('/transactions/stats/summary'),
};

// ---------------------------------------------------------------------------
//  BUSINESS
// ---------------------------------------------------------------------------
export const businessApi = {
  get: () => isNative ? sqlBusinessApi.get() : request('/business'),
  update: (data) => isNative ? sqlBusinessApi.update(data) : request('/business', { method: 'PUT', body: data }),
};

// ---------------------------------------------------------------------------
//  DATA MANAGEMENT
// ---------------------------------------------------------------------------
export const dataApi = {
  seed: async () => {
    if (isNative) {
      await sqlItemsApi.create({ name: 'Demo Item 1', salePrice: 100, category: 'General' });
      return { message: 'Demo data loaded in SQLite!' };
    }
    return request('/seed', { method: 'POST' });
  },
  reset: async () => {
    if (isNative) {
      throw new Error('Reset is not fully implemented in SQLite mode yet');
    }
    return request('/reset', { method: 'POST' });
  },
  export: async () => {
    if (isNative) {
      return sqlDataApi.export();
    }
    return request('/export');
  },
  import: async (data) => {
    if (isNative) {
      return sqlDataApi.import(data);
    }
    return request('/import', { method: 'POST', body: data });
  },
};
