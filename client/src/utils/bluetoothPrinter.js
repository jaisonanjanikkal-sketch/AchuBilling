import { Capacitor } from '@capacitor/core';

const LINE_WIDTH = 32;

/**
 * Standard ESC/POS Commands for F2C CX588
 */
const ESC = 0x1b;
const GS = 0x1d;
const COMMANDS = {
  INIT:         [ESC, 0x40],
  ALIGN_LEFT:   [ESC, 0x61, 0x00],
  ALIGN_CENTER: [ESC, 0x61, 0x01],
  ALIGN_RIGHT:  [ESC, 0x61, 0x02],
  BOLD_ON:      [ESC, 0x45, 0x01],
  BOLD_OFF:     [ESC, 0x45, 0x00],
  FONT_LARGE:   [GS, 0x21, 0x11],
  FONT_NORMAL:  [GS, 0x21, 0x00],
  LINE_FEED:    [0x0a],
  FEED_3:       [ESC, 0x64, 0x03],
};

class BluetoothPrinter {
  constructor() {
    this._connected = false;
    this._deviceName = '';
    this._pairedDevices = [];
    this._isPrinting = false;
    this._webDevice = null;
    this._webCharacteristic = null;
  }

  isSupported() {
    return Capacitor.isNativePlatform() || (typeof navigator !== 'undefined' && !!navigator.bluetooth);
  }

  isConnected() {
    return this._connected;
  }

  getDeviceName() {
    return this._deviceName || '';
  }

  async listPairedDevices() {
    if (!Capacitor.isNativePlatform()) {
      if (typeof navigator !== 'undefined' && navigator.bluetooth) {
        return [{ name: '🔍 Scan for BLE Printer...', id: 'web-bluetooth-scan' }];
      }
      return [];
    }
    const bt = window.bluetoothSerial;
    if (!bt) return [];

    return new Promise((resolve) => {
      bt.list((devices) => {
        this._pairedDevices = devices;
        resolve(devices);
      }, (err) => {
        console.error('List paired devices error:', err);
        resolve([]);
      });
    });
  }

  async connectWebBluetooth() {
    if (typeof navigator === 'undefined' || !navigator.bluetooth) {
      return { success: false, error: 'Web Bluetooth not supported in this browser.' };
    }

    try {
      const device = await navigator.bluetooth.requestDevice({
        filters: [
          { services: ['0000ffe0-0000-1000-8000-00805f9b34fb'] },
          { namePrefix: 'F2C' },
          { namePrefix: 'CX588' },
          { namePrefix: 'Printer' },
          { namePrefix: 'MPT' },
          { namePrefix: '58' }
        ],
        optionalServices: [
          '0000ffe0-0000-1000-8000-00805f9b34fb', 
          '000018f0-0000-1000-8000-00805f9b34fb', 
          'e7810a71-73ae-499d-8c15-faa9aef0c3f2'
        ]
      });

      this._webDevice = device;
      this._deviceName = device.name || 'BLE Printer';

      const server = await device.gatt.connect();

      let service;
      const services = [
        '0000ffe0-0000-1000-8000-00805f9b34fb',
        '000018f0-0000-1000-8000-00805f9b34fb',
        'e7810a71-73ae-499d-8c15-faa9aef0c3f2'
      ];
      
      for (const s of services) {
        try {
          service = await server.getPrimaryService(s);
          if (service) break;
        } catch (e) {
          // ignore
        }
      }

      if (!service) {
        throw new Error('No supported print service found on this BLE device.');
      }

      const characteristics = await service.getCharacteristics();
      this._webCharacteristic = characteristics.find(c => c.properties.write || c.properties.writeWithoutResponse);

      if (!this._webCharacteristic) {
        throw new Error('No write characteristic found.');
      }

      this._connected = true;
      
      device.addEventListener('gattserverdisconnected', () => {
        this._connected = false;
        this._deviceName = '';
        this._webDevice = null;
        this._webCharacteristic = null;
      });

      return { success: true, deviceName: this._deviceName };
    } catch (err) {
      console.error('[WebBluetooth] Connection failed:', err);
      this._connected = false;
      this._deviceName = '';
      this._webDevice = null;
      this._webCharacteristic = null;
      return { success: false, error: err.message || 'Connection failed.' };
    }
  }

  async connect(addressOrId) {
    if (!Capacitor.isNativePlatform()) {
      if (addressOrId === 'web-bluetooth-scan' || !this._webDevice) {
        return this.connectWebBluetooth();
      }
      return { success: false, error: 'Web Bluetooth scanner failed.' };
    }

    const bt = window.bluetoothSerial;
    if (!bt) return { success: false, error: 'Bluetooth plugin missing.' };

    try {
      // 1. Ensure BT is enabled
      await new Promise((res, rej) => bt.enable(res, rej));

      // 2. If no address, auto-detect F2C models (CX588, SRT, MPT etc.)
      let targetId = addressOrId;
      if (!targetId) {
        const devices = await this.listPairedDevices();
        const printer = devices.find(d => {
          const n = (d.name || '').toLowerCase();
          return n.includes('f2c') ||
                 n.includes('cx588') ||
                 n.includes('58') ||
                 n.includes('printer') ||
                 n.includes('mpt') ||
                 n.includes('innerprinter');
        });
        if (printer) targetId = printer.address || printer.id;
      }

      if (!targetId) {
        return { success: false, error: 'Printer not found. Please pair "CX588" or "F2C" in phone settings first.' };
      }

      // 3. Force disconnect existing
      await new Promise(res => bt.disconnect(() => res(), () => res()));

      // 4. Connect via SPP (Standard Bluetooth)
      await new Promise((res, rej) => bt.connect(targetId, res, rej));

      this._connected = true;
      const device = this._pairedDevices.find(d => (d.address || d.id) === targetId);
      this._deviceName = device?.name || 'F2C CX588 Printer';

      return { success: true, deviceName: this._deviceName };
    } catch (err) {
      console.error('[BluetoothPrinter] Connect failed:', err);
      this._connected = false;
      return { success: false, error: 'Connect failed. Is the printer ON and Paired?' };
    }
  }

  async disconnect() {
    if (!Capacitor.isNativePlatform()) {
      if (this._webDevice && this._webDevice.gatt.connected) {
        this._webDevice.gatt.disconnect();
      }
      this._connected = false;
      this._deviceName = '';
      this._webDevice = null;
      this._webCharacteristic = null;
      return;
    }
    const bt = window.bluetoothSerial;
    if (bt) {
      try {
        await new Promise(res => bt.disconnect(() => res(), () => res()));
      } catch(e){}
    }
    this._connected = false;
    this._deviceName = '';
  }

  /**
   * Safe transmission for F2C CX588.
   * CX588 has a 384-dot line buffer.
   */
  async _sendBytes(data) {
    const bytes = data instanceof Uint8Array ? data : new Uint8Array(data);

    if (!this._connected) return;

    if (!Capacitor.isNativePlatform()) {
      if (!this._webCharacteristic) return;
      if (this._isPrinting) return;
      this._isPrinting = true;
      try {
        const CHUNK_SIZE = 20;
        for (let i = 0; i < bytes.length; i += CHUNK_SIZE) {
          const chunk = bytes.slice(i, i + CHUNK_SIZE);
          if (this._webCharacteristic.properties.writeWithoutResponse) {
            await this._webCharacteristic.writeValueWithoutResponse(chunk);
          } else {
            await this._webCharacteristic.writeValue(chunk);
          }
          await new Promise(resolve => setTimeout(resolve, 30));
        }
      } catch (err) {
        console.error('[WebBluetooth] Write failed:', err);
      } finally {
        this._isPrinting = false;
      }
      return;
    }

    const bt = window.bluetoothSerial;
    if (!bt) return;
    if (this._isPrinting) return;

    this._isPrinting = true;
    try {
      const CHUNK_SIZE = 16;
      for (let i = 0; i < bytes.length; i += CHUNK_SIZE) {
        const chunk = bytes.slice(i, i + CHUNK_SIZE);
        await new Promise((res, rej) => bt.write(chunk, res, rej));
        await new Promise(resolve => setTimeout(resolve, 60));
      }
    } catch (err) {
      console.error('Byte transmission failed:', err);
    } finally {
      this._isPrinting = false;
    }
  }

  _textToBytes(text) {
    const encoder = new TextEncoder();
    return encoder.encode(text);
  }

  _buildBuffer(...parts) {
    const arrays = parts.map(p => {
      if (typeof p === 'string') return this._textToBytes(p);
      if (Array.isArray(p)) return new Uint8Array(p);
      return p;
    });

    const totalLength = arrays.reduce((sum, a) => sum + a.length, 0);
    const result = new Uint8Array(totalLength);
    let offset = 0;
    for (const arr of arrays) {
      result.set(arr, offset);
      offset += arr.length;
    }
    return result;
  }

  _padRight(str, len) {
    const s = String(str || '').substring(0, len);
    return s + ' '.repeat(Math.max(0, len - s.length));
  }
  _padLeft(str, len) {
    const s = String(str || '').substring(0, len);
    return ' '.repeat(Math.max(0, len - s.length)) + s;
  }
  _dashedLine() { return '-'.repeat(LINE_WIDTH); }
  _twoColumn(left, right) {
    const l = String(left || '');
    const r = String(right || '');
    const maxLeft = LINE_WIDTH - r.length - 1;
    const trimmedLeft = l.substring(0, maxLeft);
    const spaces = LINE_WIDTH - trimmedLeft.length - r.length;
    return trimmedLeft + ' '.repeat(Math.max(spaces, 1)) + r;
  }

  async printInvoice(txn, biz) {
    if (!this._connected) return;
    if (!txn) return;

    try {
      const lines = [];
      // Start with INIT to clear buffer
      lines.push(COMMANDS.INIT);
      lines.push(COMMANDS.LINE_FEED);

      // Header
      lines.push(COMMANDS.ALIGN_CENTER);
      lines.push(COMMANDS.BOLD_ON);
      lines.push((biz?.name || 'BILLING APP').toUpperCase() + '\n');
      lines.push(COMMANDS.BOLD_OFF);

      if (biz?.phone) lines.push('Mobile: ' + biz.phone + '\n');
      lines.push(COMMANDS.LINE_FEED);

      // Invoice Details
      lines.push(COMMANDS.ALIGN_LEFT);
      lines.push(this._dashedLine() + '\n');
      lines.push(this._twoColumn('Invoice No:', '#' + (txn.id || '000')) + '\n');
      const d = txn.date ? new Date(txn.date) : new Date();
      lines.push(this._twoColumn('Date:', d.toLocaleDateString('en-IN')) + '\n');
      lines.push(this._dashedLine() + '\n');

      // Table Header: ITEM(16) QTY(6) AMT(10)
      lines.push(this._padRight('ITEM', 16) + this._padLeft('QTY', 6) + this._padLeft('AMT', 10) + '\n');
      lines.push(this._dashedLine() + '\n');

      // Table Items
      if (txn.items && Array.isArray(txn.items)) {
        for (const item of txn.items) {
          const name = String(item.itemName || 'Item').substring(0, 16);
          const qty = String(item.quantity || 0);
          const amt = String(item.amount || 0);
          lines.push(this._padRight(name, 16) + this._padLeft(qty, 6) + this._padLeft(amt, 10) + '\n');
        }
      }

      // Total
      lines.push(this._dashedLine() + '\n');
      lines.push(COMMANDS.BOLD_ON);
      lines.push(this._twoColumn('GRAND TOTAL', 'Rs.' + (txn.total || 0)) + '\n');
      lines.push(COMMANDS.BOLD_OFF);
      lines.push(this._dashedLine() + '\n');

      // Footer
      lines.push(COMMANDS.ALIGN_CENTER);
      lines.push('\nTHANK YOU FOR SHOPPING!\n');

      // Feed paper so it can be torn easily
      lines.push(COMMANDS.FEED_3);
      lines.push('\n\n\n');

      const buffer = this._buildBuffer(...lines);
      await this._sendBytes(buffer);
    } catch (err) {
      console.error('Invoice print failed:', err);
    }
  }

  async printTestPage() {
    if (!this._connected) throw new Error('Printer not connected');
    const lines = [
      COMMANDS.INIT,
      COMMANDS.ALIGN_CENTER,
      COMMANDS.BOLD_ON,
      'F2C CX588 READY\n',
      COMMANDS.BOLD_OFF,
      this._dashedLine() + '\n',
      'Mode: Standard SPP\n',
      'Buffer: High Stability\n',
      this._dashedLine() + '\n',
      COMMANDS.FEED_3
    ];
    await this._sendBytes(this._buildBuffer(...lines));
  }
}

export const bluetoothPrinter = new BluetoothPrinter();
