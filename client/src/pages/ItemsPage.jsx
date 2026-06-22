import React, { useState, useEffect } from 'react';
import { itemsApi } from '../api/api';

function formatCurrency(n) {
  return '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}
function formatQty(q) {
  return Number(Number(q).toFixed(3)).toString();
}

export default function ItemsPage({ onEditItem, refreshKey }) {
  const [items, setItems] = useState([]);
  const [search, setSearch] = useState('');

  useEffect(() => {
    loadItems();
  }, [refreshKey]);

  useEffect(() => {
    const timer = setTimeout(() => loadItems(), 200);
    return () => clearTimeout(timer);
  }, [search]);

  async function loadItems() {
    try {
      const data = await itemsApi.getAll(search);
      setItems(data);
    } catch (err) {
      console.error('ItemsPage loadItems error:', err);
    }
  }

  return (
    <div className="tab-view">
      {/* Search Bar */}
      <div className="items-search-bar">
        <div className="search-input-wrap">
          <span className="search-icon">🔍</span>
          <input
            type="text"
            placeholder="Search for an item..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            autoComplete="off"
          />
        </div>
      </div>

      {/* Items List */}
      <div className="items-list">
        {items.length === 0 ? (
          <div className="empty-state">
            <div className="es-icon">📦</div>
            <div className="es-title">{search ? 'No items found' : 'No items yet'}</div>
            <div className="es-desc">
              {search ? 'Try a different search term' : 'Tap "Add New Item" to add your first product'}
            </div>
          </div>
        ) : (
          items.map(item => {
            const initials = item.name.substring(0, 2).toUpperCase();
            const stockClass = item.stock > 5 ? 'positive' : (item.stock > 0 ? 'zero' : 'negative');
            return (
              <div key={item.id} className="item-card" onClick={() => onEditItem(item)}>
                <div className="item-avatar">{initials}</div>
                <div className="item-details">
                  <div className="item-name">{item.name}</div>
                </div>
                <div className="item-price-stock">
                  <div className="item-sale-price">{formatCurrency(item.salePrice)}</div>
                  <div className={`item-stock ${stockClass}`}>{formatQty(item.stock)} in stock</div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Add Item FAB */}
      <button className="add-item-btn" onClick={() => onEditItem(null)}>
        ＋ Add New Item
      </button>
    </div>
  );
}
