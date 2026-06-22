import React, { useState, useCallback } from 'react';
import { useToast } from '../components/Toast';
import { bluetoothPrinter } from '../utils/bluetoothPrinter';
import PrinterListModal from '../modals/PrinterListModal';

export default function MenuPage({ businessName, onEditProfile, onSeedData, onResetData, onExportData }) {
  const showToast = useToast();
  const [printerName, setPrinterName] = useState(bluetoothPrinter.getDeviceName());
  const [isListOpen, setIsListOpen] = useState(false);
  const [testPrinting, setTestPrinting] = useState(false);
  const [connecting, setConnecting] = useState(false);

  const isConnected = bluetoothPrinter.isConnected();
  const isSupported = bluetoothPrinter.isSupported();

  const handleConnectPrinter = useCallback(() => {
    setIsListOpen(true);
  }, []);

  const handleDisconnectPrinter = useCallback(() => {
    bluetoothPrinter.disconnect();
    setPrinterName('');
    showToast('Printer disconnected');
  }, [showToast]);

  const handleTestPrint = useCallback(async () => {
    setTestPrinting(true);
    try {
      await bluetoothPrinter.printTestPage();
      showToast('Test page sent to printer!');
    } catch (err) {
      showToast(err.message || 'Test print failed', 'error');
    } finally {
      setTestPrinting(false);
    }
  }, [showToast]);

  return (
    <div className="tab-view">
      <div className="menu-section">
        {/* Business Profile */}
        <div className="menu-group">
          <div className="menu-group-title">Business Profile</div>
          <div className="menu-card">
            <button className="menu-item" onClick={onEditProfile}>
              <div className="mi-icon" style={{background:'#dbeafe',color:'#2563eb'}}>🏪</div>
              <div className="mi-text">
                <div className="mi-title">{businessName || 'My Business'}</div>
                <div className="mi-desc">Edit business name, phone & address</div>
              </div>
              <span className="mi-arrow">›</span>
            </button>
          </div>
        </div>

        {/* Printer Settings */}
        <div className="menu-group">
          <div className="menu-group-title">Printer Settings</div>
          <div className="menu-card">
            {!isSupported ? (
              <div className="menu-item">
                <div className="mi-icon" style={{background:'#fee2e2',color:'#dc2626'}}>⚠️</div>
                <div className="mi-text">
                  <div className="mi-title">Bluetooth Not Supported</div>
                  <div className="mi-desc">Please use Google Chrome on Android to connect a printer</div>
                </div>
              </div>
            ) : isConnected ? (
              <>
                {/* Connected status */}
                <div className="menu-item">
                  <div className="mi-icon" style={{background:'#dcfce7',color:'#16a34a'}}>✅</div>
                  <div className="mi-text">
                    <div className="mi-title">{printerName || 'Printer Connected'}</div>
                    <div className="mi-desc" style={{color:'#16a34a'}}>Bluetooth connected and ready</div>
                  </div>
                </div>
                {/* Test Print */}
                <button className="menu-item" onClick={handleTestPrint} disabled={testPrinting}>
                  <div className="mi-icon" style={{background:'#e0e7ff',color:'#4f46e5'}}>🧪</div>
                  <div className="mi-text">
                    <div className="mi-title">{testPrinting ? 'Printing...' : 'Print Test Page'}</div>
                    <div className="mi-desc">Send a test receipt to verify the printer</div>
                  </div>
                  <span className="mi-arrow">›</span>
                </button>
                {/* Disconnect */}
                <button className="menu-item" onClick={handleDisconnectPrinter}>
                  <div className="mi-icon" style={{background:'#fee2e2',color:'#dc2626'}}>🔌</div>
                  <div className="mi-text">
                    <div className="mi-title">Disconnect Printer</div>
                    <div className="mi-desc">Disconnect from {printerName || 'current printer'}</div>
                  </div>
                  <span className="mi-arrow">›</span>
                </button>
              </>
            ) : (
              <>
                {/* Connect button */}
                <button className="menu-item" onClick={handleConnectPrinter} disabled={connecting}>
                  <div className="mi-icon" style={{background:'#dbeafe',color:'#2563eb'}}>🖨️</div>
                  <div className="mi-text">
                    <div className="mi-title">{connecting ? 'Connecting...' : 'Connect Bluetooth Printer'}</div>
                    <div className="mi-desc">Pair with a 58mm/80mm thermal printer</div>
                  </div>
                  <span className="mi-arrow">›</span>
                </button>
                {/* Help text */}
                <div className="menu-item">
                  <div className="mi-icon" style={{background:'#fef3c7',color:'#d97706'}}>💡</div>
                  <div className="mi-text">
                    <div className="mi-title">How to Connect</div>
                    <div className="mi-desc">Turn on your thermal printer → Tap "Connect" above → Select your printer from the list</div>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Data Tools */}
        <div className="menu-group">
          <div className="menu-group-title">Data Management</div>
          <div className="menu-card">
            <button className="menu-item" onClick={onSeedData}>
              <div className="mi-icon" style={{background:'#dcfce7',color:'#16a34a'}}>📥</div>
              <div className="mi-text">
                <div className="mi-title">Load Demo Data</div>
                <div className="mi-desc">Populate with sample items & transactions</div>
              </div>
              <span className="mi-arrow">›</span>
            </button>
            <button className="menu-item" onClick={onExportData}>
              <div className="mi-icon" style={{background:'#e0e7ff',color:'#4f46e5'}}>📤</div>
              <div className="mi-text">
                <div className="mi-title">Export Data</div>
                <div className="mi-desc">Download all data as JSON backup</div>
              </div>
              <span className="mi-arrow">›</span>
            </button>
            <button className="menu-item" onClick={onResetData}>
              <div className="mi-icon" style={{background:'#fee2e2',color:'#dc2626'}}>🗑️</div>
              <div className="mi-text">
                <div className="mi-title">Reset All Data</div>
                <div className="mi-desc">Clear all items, transactions & settings</div>
              </div>
              <span className="mi-arrow">›</span>
            </button>
          </div>
        </div>

        {/* About */}
        <div className="menu-group">
          <div className="menu-group-title">About</div>
          <div className="menu-card">
            <div className="menu-item">
              <div className="mi-icon" style={{background:'#fef3c7',color:'#d97706'}}>ℹ️</div>
              <div className="mi-text">
                <div className="mi-title">Anjanikkal v2.0</div>
                <div className="mi-desc">Full-stack billing & inventory app</div>
              </div>
            </div>
          </div>
        </div>

        <div className="menu-footer">
          Made with ❤️ — <strong>Anjanikkal</strong> &copy; 2026
        </div>
      </div>

      <PrinterListModal
        isOpen={isListOpen}
        onClose={() => setIsListOpen(false)}
        onConnected={(name) => setPrinterName(name)}
      />
    </div>
  );
}
