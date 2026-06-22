import React, { useState, useEffect, useRef } from 'react';
import { itemsApi, transactionsApi, businessApi } from '../api/api';
import { useToast } from '../components/Toast';

function formatCurrency(n) {
  return '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}
function formatQty(q) {
  return Number(Number(q).toFixed(3)).toString();
}

export default function AddSalePage({ onClose, onSaved, editTxn }) {
  const showToast = useToast();

  // Item input fields
  const [itemName, setItemName] = useState('');
  const [rate, setRate] = useState('');
  const [qty, setQty] = useState('1');
  const [selectedItemId, setSelectedItemId] = useState(null);

  // Bill items list
  const [billItems, setBillItems] = useState(() => {
    if (editTxn && editTxn.items) {
      return editTxn.items.map(item => ({
        itemId: item.itemId,
        itemName: item.itemName,
        quantity: item.quantity,
        rate: item.rate,
        amount: item.amount
      }));
    }
    return [];
  });

  // Autocomplete states
  const [searchResults, setSearchResults] = useState([]);
  const [showResults, setShowResults] = useState(false);
  const [acActiveIndex, setAcActiveIndex] = useState(-1);

  // Save states
  const [saving, setSaving] = useState(false);

  const searchRef = useRef(null);

  // Search items as user types the item name
  useEffect(() => {
    if (!itemName.trim()) {
      setSearchResults([]);
      setShowResults(false);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const items = await itemsApi.getAll(itemName);
        setSearchResults(items.slice(0, 6));
        setShowResults(true);
      } catch (err) {
        console.error('Autocomplete error:', err);
      }
    }, 150);
    return () => clearTimeout(timer);
  }, [itemName]);

  // Close autocomplete on clicking outside
  useEffect(() => {
    function handleClick(e) {
      if (searchRef.current && !searchRef.current.contains(e.target)) {
        setShowResults(false);
      }
    }
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, []);

  // Keyboard navigation for autocomplete list
  function handleKeyDown(e) {
    if (!showResults || searchResults.length === 0) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setAcActiveIndex(prev => Math.min(prev + 1, searchResults.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setAcActiveIndex(prev => Math.max(prev - 1, 0));
    } else if (e.key === 'Enter') {
      if (acActiveIndex >= 0 && acActiveIndex < searchResults.length) {
        e.preventDefault();
        pickItem(searchResults[acActiveIndex]);
      }
    }
  }

  function pickItem(item) {
    setItemName(item.name);
    setRate(item.salePrice.toString());
    setQty('1');
    setSelectedItemId(item.id);
    setShowResults(false);
    setAcActiveIndex(-1);
    // Focus quantity field
    const qtyInput = document.getElementById('saleQtyInput');
    if (qtyInput) {
      setTimeout(() => qtyInput.select(), 50);
    }
  }

  function handleItemNameChange(value) {
    setItemName(value);
    setSelectedItemId(null);
    setAcActiveIndex(-1);
  }

  const lineTotal = (parseFloat(rate) || 0) * (parseFloat(qty) || 0);
  const grandTotal = billItems.reduce((s, b) => s + b.amount, 0);

  function addItemToBill() {
    const trimmedName = itemName.trim();
    const finalRate = parseFloat(rate) || 0;
    const finalQty = parseFloat(qty) || 0;

    if (!trimmedName) {
      showToast('Enter item name', 'error');
      return;
    }
    if (finalRate <= 0) {
      showToast('Enter sale price', 'error');
      return;
    }
    if (finalQty <= 0) {
      showToast('Enter valid quantity', 'error');
      return;
    }

    setBillItems(prev => {
      const existingIdx = prev.findIndex(b => b.itemName.toLowerCase() === trimmedName.toLowerCase());
      if (existingIdx !== -1) {
        return prev.map((b, i) =>
          i === existingIdx
            ? { ...b, quantity: b.quantity + finalQty, rate: finalRate, amount: (b.quantity + finalQty) * finalRate }
            : b
        );
      }
      return [...prev, {
        itemId: selectedItemId,
        itemName: trimmedName,
        quantity: finalQty,
        rate: finalRate,
        amount: finalQty * finalRate
      }];
    });

    // Reset inputs
    setItemName('');
    setRate('');
    setQty('1');
    setSelectedItemId(null);
    setShowResults(false);
    setAcActiveIndex(-1);

    showToast('Item added to bill');

    const nameInput = document.getElementById('saleItemNameInput');
    if (nameInput) {
      setTimeout(() => nameInput.focus(), 50);
    }
  }

  function removeBillItem(idx) {
    setBillItems(prev => prev.filter((_, i) => i !== idx));
  }

  async function handleFinalSave(isPaid = true) {
    if (billItems.length === 0) {
      showToast('Add at least one item', 'error');
      return;
    }

    setSaving(true);
    try {
      const finalBillItems = [...billItems];
      const grandTotal = finalBillItems.reduce((s, b) => s + b.amount, 0);

      // Auto-create ad-hoc items in database if needed
      for (let i = 0; i < finalBillItems.length; i++) {
        const item = finalBillItems[i];
        if (!item.itemId) {
          const results = await itemsApi.getAll(item.itemName);
          const exactMatch = results.find(dbItem => dbItem.name.toLowerCase() === item.itemName.toLowerCase());

          if (exactMatch) {
            item.itemId = exactMatch.id;
          } else {
            const created = await itemsApi.create({
              name: item.itemName,
              category: 'General',
              salePrice: item.rate,
              purchasePrice: 0,
              stock: 0
            });
            item.itemId = created.id;
          }
        }
      }

      const balance = isPaid ? 0 : grandTotal;

      if (editTxn) {
        await transactionsApi.update(editTxn.id, finalBillItems, balance);
        showToast(`Invoice updated successfully!`, 'success');
      } else {
        await transactionsApi.create(finalBillItems, balance);
        showToast(`Invoice saved as ${isPaid ? 'Paid' : 'Unpaid'}!`, 'success');
      }
      if (onSaved) onSaved();

    } catch (err) {
      console.error('Save error:', err);
      showToast(err.message || 'Failed to save transaction', 'error');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="sale-view">
      <div className="main-content" style={{ paddingBottom: 150 }}>
        {/* Direct Item Entry Form */}
        <div className="sale-entry-section">
          <div className="sale-entry-title">
            <span className="entry-icon">📝</span> Enter Item Details
          </div>

          <div className="sale-item-name-wrap" ref={searchRef}>
            <input
              type="text"
              className="form-input"
              id="saleItemNameInput"
              placeholder="Item name (e.g. Tata Salt 1kg)"
              value={itemName}
              onChange={e => handleItemNameChange(e.target.value)}
              onKeyDown={handleKeyDown}
              autoComplete="off"
            />
            {showResults && searchResults.length > 0 && (
              <div className="autocomplete-list" style={{ position: 'absolute', top: '100%', left: 0, right: 0, background: 'var(--card)', border: '1px solid var(--border)', borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-lg)', maxHeight: 180, overflowY: 'auto', zIndex: 100 }}>
                {searchResults.map((item, idx) => (
                  <button
                    key={item.id}
                    className={`ac-item ${idx === acActiveIndex ? 'ac-active' : ''}`}
                    onClick={() => pickItem(item)}
                    style={{ background: idx === acActiveIndex ? 'var(--primary-light)' : 'none', padding: '10px 14px', width: '100%', border: 'none', textAlign: 'left', display: 'flex', justifyContent: 'space-between', cursor: 'pointer' }}
                  >
                    <div>
                      <div className="ac-item-name" style={{ fontWeight: 600, fontSize: 13 }}>{item.name}</div>
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <div className="ac-item-price" style={{ color: 'var(--primary)', fontWeight: 700, fontSize: 13 }}>{formatCurrency(item.salePrice)}</div>
                      <div className="ac-item-stock" style={{ color: 'var(--text-tertiary)', fontSize: 10 }}>{formatQty(item.stock)} in stock</div>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="sale-entry-row" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 10 }}>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Sale Price (₹)</label>
              <input
                type="number"
                className="form-input"
                id="saleRateInput"
                placeholder="0.00"
                value={rate}
                onChange={e => setRate(e.target.value)}
                min="0"
                step="0.01"
              />
            </div>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Quantity</label>
              <input
                type="number"
                className="form-input"
                id="saleQtyInput"
                placeholder="1"
                value={qty}
                onChange={e => setQty(e.target.value)}
                min="0.001"
                step="any"
              />
            </div>
          </div>

          <div className="sale-line-total">
            <span className="slt-label">Line Total</span>
            <span className="slt-value">{formatCurrency(lineTotal)}</span>
          </div>

          <button className="btn btn-primary btn-block" onClick={addItemToBill}>
            ＋ Add to Bill
          </button>
        </div>

        <div className="bill-table-wrap">
          <table className="bill-table">
            <thead>
              <tr>
                <th style={{ width: 30 }}>#</th>
                <th>Item</th>
                <th>Qty</th>
                <th>Amount</th>
                <th style={{ width: 30 }}></th>
              </tr>
            </thead>
            <tbody>
              {billItems.map((b, i) => (
                <tr key={i}>
                  <td>{i + 1}</td>
                  <td>
                    <div style={{ fontWeight: 600, fontSize: 12 }}>{b.itemName}</div>
                    <div style={{ fontSize: 10, color: 'var(--text-tertiary)' }}>{formatCurrency(b.rate)}/unit</div>
                  </td>
                  <td>{formatQty(b.quantity)}</td>
                  <td>{formatCurrency(b.amount)}</td>
                  <td>
                    <button className="remove-line" onClick={() => removeBillItem(i)} title="Remove">×</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {billItems.length === 0 && (
            <div className="empty-state">
              <div className="es-icon">🧾</div>
              <div className="es-title">No items added yet</div>
              <div className="es-desc">Enter item details above to start your invoice</div>
            </div>
          )}
        </div>
      </div>

      {billItems.length > 0 && (
        <div className="bill-summary" style={{ position: 'fixed', bottom: 0, left: 0, right: 0, background: 'var(--card)', zIndex: 10, borderTop: '2px solid var(--border)', padding: '16px' }}>
          <div className="bill-total-row" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
            <span className="bill-total-label" style={{ fontSize: 16, fontWeight: 700 }}>Grand Total</span>
            <span className="bill-total-value" style={{ fontSize: 24, fontWeight: 800, color: 'var(--primary)' }}>{formatCurrency(grandTotal)}</span>
          </div>
          <div className="bill-actions" style={{ display: 'flex', gap: 10 }}>
            <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setBillItems([])}>🗑️ Clear</button>
            <div style={{ flex: 3, display: 'flex', gap: 10 }}>
              <button className="btn btn-accent" style={{ flex: 1 }} onClick={() => handleFinalSave(false)} disabled={saving}>
                {saving ? '...' : '⚠️ Unpaid'}
              </button>
              <button className="btn btn-primary" style={{ flex: 2 }} onClick={() => handleFinalSave(true)} disabled={saving}>
                {saving ? 'Saving...' : '💾 Save Paid'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
