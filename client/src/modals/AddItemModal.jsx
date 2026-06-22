import React, { useState, useEffect } from 'react';
import Modal from '../components/Modal';
import { itemsApi } from '../api/api';
import { useToast } from '../components/Toast';

export default function AddItemModal({ show, editItem: item, onClose, onSaved: onSave }) {
  const showToast = useToast();
  const [name, setName] = useState('');
  const [salePrice, setSalePrice] = useState('');
  const [stock, setStock] = useState('');

  useEffect(() => {
    if (item) {
      setName(item.name || '');
      setSalePrice(item.salePrice || '');
      setStock(item.stock || '');
    } else {
      setName('');
      setSalePrice('');
      setStock('');
    }
  }, [item, show]);

  async function handleSave() {
    const trimmedName = name.trim();
    const numericSale = parseFloat(salePrice) || 0;
    const numericStock = parseFloat(stock) || 0;

    if (!trimmedName) {
      showToast('Item name is required', 'error');
      return;
    }
    if (numericSale <= 0) {
      showToast('Sale price must be greater than 0', 'error');
      return;
    }

    try {
      const payload = {
        name: trimmedName,
        salePrice: numericSale,
        stock: numericStock
      };

      if (item) {
        // Edit existing by ID
        await itemsApi.update(item.id, payload);
        showToast('Item updated successfully!');
      } else {
        // Add new
        await itemsApi.create(payload);
        showToast('Item added successfully!');
      }
      onSave();
    } catch (err) {
      showToast(err.message || 'Failed to save item', 'error');
    }
  }

  async function handleDelete() {
    if (!item) return;
    if (window.confirm(`Are you sure you want to delete "${item.name}"?`)) {
      try {
        await itemsApi.delete(item.id);
        showToast('Item deleted successfully!');
        onSave();
      } catch (err) {
        showToast(err.message || 'Failed to delete item', 'error');
      }
    }
  }

  return (
    <Modal
      show={show}
      title={item ? 'Edit Item' : 'Add New Item'}
      onClose={onClose}
    >
      <div className="form-group">
        <label className="form-label">Item Name *</label>
        <input
          type="text"
          className="form-input"
          placeholder="e.g. Chocolate Biscuits"
          value={name}
          onChange={e => setName(e.target.value)}
        />
      </div>
      <div className="form-group">
        <label className="form-label">Sale Price (₹) *</label>
        <input
          type="number"
          className="form-input"
          placeholder="0"
          min="0"
          step="0.01"
          value={salePrice}
          onChange={e => setSalePrice(e.target.value)}
        />
      </div>
      <div className="form-group">
        <label className="form-label">Stock</label>
        <input
          type="number"
          className="form-input"
          placeholder="0"
          step="any"
          value={stock}
          onChange={e => setStock(e.target.value)}
        />
      </div>
      <button className="btn btn-primary btn-block" onClick={handleSave}>
        💾 {item ? 'Update Item' : 'Save Item'}
      </button>
      {item && (
        <button
          className="btn btn-danger btn-block"
          style={{ marginTop: 8 }}
          onClick={handleDelete}
        >
          🗑️ Delete Item
        </button>
      )}
    </Modal>
  );
}
