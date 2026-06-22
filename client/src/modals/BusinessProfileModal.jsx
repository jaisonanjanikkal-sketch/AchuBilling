import React, { useState, useEffect } from 'react';
import Modal from '../components/Modal';
import { businessApi } from '../api/api';
import { useToast } from '../components/Toast';

export default function BusinessProfileModal({ show, onClose, onSaved: onSave }) {
  const showToast = useToast();
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');

  useEffect(() => {
    if (show) {
      loadProfile();
    }
  }, [show]);

  async function loadProfile() {
    try {
      const data = await businessApi.get();
      setName(data.name || '');
      setPhone(data.phone || '');
      setAddress(data.address || '');
    } catch (err) {
      console.error('Failed to load business profile:', err);
    }
  }

  async function handleSave() {
    try {
      const payload = {
        name: name.trim() || 'My Business',
        phone: phone.trim(),
        address: address.trim()
      };
      await businessApi.update(payload);
      showToast('Business profile saved!');
      onSave();
    } catch (err) {
      showToast(err.message || 'Failed to save profile', 'error');
    }
  }

  return (
    <Modal
      show={show}
      title="Business Profile"
      onClose={onClose}
    >
      <div className="form-group">
        <label className="form-label">Business Name</label>
        <input
          type="text"
          className="form-input"
          placeholder="My Shop"
          value={name}
          onChange={e => setName(e.target.value)}
        />
      </div>
      <div className="form-group">
        <label className="form-label">Phone</label>
        <input
          type="text"
          className="form-input"
          placeholder="+91 9876543210"
          value={phone}
          onChange={e => setPhone(e.target.value)}
        />
      </div>
      <div className="form-group">
        <label className="form-label">Address</label>
        <textarea
          className="form-input"
          rows="3"
          placeholder="Shop address..."
          value={address}
          onChange={e => setAddress(e.target.value)}
        />
      </div>
      <button className="btn btn-primary btn-block" onClick={handleSave}>
        💾 Save Profile
      </button>
    </Modal>
  );
}
