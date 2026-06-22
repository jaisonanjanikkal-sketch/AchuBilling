import React, { useState, useEffect } from 'react';
import { bluetoothPrinter } from '../utils/bluetoothPrinter';
import { useToast } from '../components/Toast';

export default function PrinterListModal({ isOpen, onClose, onConnected }) {
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(false);
  const showToast = useToast();

  useEffect(() => {
    if (isOpen) {
      loadDevices();
    }
  }, [isOpen]);

  async function loadDevices() {
    setLoading(true);
    const list = await bluetoothPrinter.listPairedDevices();
    setDevices(list);
    setLoading(false);
  }

  async function handleConnect(device) {
    setLoading(true);
    const result = await bluetoothPrinter.connect(device.address || device.id);
    setLoading(false);

    if (result.success) {
      showToast(`Connected to ${result.deviceName}`);
      if (onConnected) onConnected(result.deviceName);
      onClose();
    } else {
      showToast(result.error || 'Connection failed', 'error');
    }
  }

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" style={{ display: 'flex', zIndex: 2000 }} onClick={onClose}>
      <div className="modal-sheet" onClick={e => e.stopPropagation()} style={{ maxHeight: '70vh' }}>
        <div className="modal-handle"></div>
        <div className="modal-header">
          <span style={{ fontWeight: 700, fontSize: 16 }}>Select Paired Printer</span>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div style={{ padding: '10px 20px 20px' }}>
          <p style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 15 }}>
            Note: Only paired Bluetooth Classic devices are shown. Pair your printer in phone settings first.
          </p>

          {loading && <div style={{ textAlign: 'center', padding: 20 }}>Searching...</div>}

          {!loading && devices.length === 0 && (
            <div style={{ textAlign: 'center', padding: 30 }}>
              <div style={{ fontSize: 30, marginBottom: 10 }}>🔌</div>
              <div style={{ fontWeight: 600 }}>No Paired Devices Found</div>
              <button className="btn btn-outline btn-sm" style={{ marginTop: 10 }} onClick={loadDevices}>🔄 Refresh</button>
            </div>
          )}

          {!loading && devices.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {devices.map((d, i) => (
                <button
                  key={i}
                  className="btn btn-outline"
                  style={{ justifyContent: 'space-between', padding: '12px 16px', textAlign: 'left' }}
                  onClick={() => handleConnect(d)}
                >
                  <div>
                    <div style={{ fontWeight: 700 }}>{d.name || 'Unnamed Device'}</div>
                    <div style={{ fontSize: 10, color: 'var(--text-tertiary)' }}>{d.address || d.id}</div>
                  </div>
                  <span>🔌</span>
                </button>
              ))}
              <button className="btn btn-block btn-sm" style={{ marginTop: 10, border: 'none', color: 'var(--primary)' }} onClick={loadDevices}>
                🔄 Refresh Device List
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
