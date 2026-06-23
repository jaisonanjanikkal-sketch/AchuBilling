import React, { useState, useEffect } from 'react';
import Modal from '../components/Modal';
import { transactionsApi, businessApi } from '../api/api';
import { bluetoothPrinter } from '../utils/bluetoothPrinter';
import { useToast } from '../components/Toast';
import { shareInvoiceAsImage } from '../utils/shareInvoice';

function formatCurrency(n) {
  return '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}
function formatQty(q) {
  return Number(Number(q).toFixed(3)).toString();
}
function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}
function formatTime(iso) {
  return new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
}

export default function InvoiceModal({ show, transactionId, onClose }) {
  const showToast = useToast();
  const [txn, setTxn] = useState(null);
  const [biz, setBiz] = useState(null);
  const [loading, setLoading] = useState(false);
  const [printing, setPrinting] = useState(false);

  useEffect(() => {
    if (show && transactionId) {
      loadData();
    } else {
      setTxn(null);
    }
  }, [show, transactionId]);

  async function loadData() {
    setLoading(true);
    try {
      const [txnData, bizData] = await Promise.all([
        transactionsApi.getOne(transactionId),
        businessApi.get()
      ]);
      setTxn(txnData);
      setBiz(bizData);
    } catch (err) {
      console.error('InvoiceModal loadData error:', err);
    } finally {
      setLoading(false);
    }
  }

  async function handlePrint() {
    // If Bluetooth printer is connected, print to thermal printer
    if (bluetoothPrinter.isConnected() && txn && biz) {
      setPrinting(true);
      try {
        await bluetoothPrinter.printInvoice(txn, biz);
      } catch (err) {
        console.error('Bluetooth print error:', err);
        // Fallback to browser print
        window.print();
      } finally {
        setPrinting(false);
      }
    } else {
      // Fallback to browser print
      window.print();
    }
  }

  async function handleShare() {
    if (!txn || !biz) return;
    await shareInvoiceAsImage(txn, biz, showToast);
  }

  function buildShareText(txn, biz) {
    let text = '';
    text += `${biz.name || 'My Business'}\n`;
    if (biz.phone || biz.address) {
      text += [biz.phone, biz.address].filter(Boolean).join(' · ') + '\n';
    }
    text += '─'.repeat(28) + '\n';
    text += `Invoice #${txn.id}\n`;
    text += `Date: ${formatDate(txn.date)} ${formatTime(txn.date)}\n`;
    text += `Payment: Cash\n`;
    text += '─'.repeat(28) + '\n';

    for (const li of txn.items) {
      text += `${li.itemName}\n`;
      text += `  ${formatQty(li.quantity)} × ${formatCurrency(li.rate)} = ${formatCurrency(li.amount)}\n`;
    }

    text += '─'.repeat(28) + '\n';
    text += `TOTAL: ${formatCurrency(txn.total)}\n`;
    if (txn.balance === 0) {
      text += `Balance: ${formatCurrency(txn.balance)} (Paid in Full)\n`;
    } else {
      text += `Balance: ${formatCurrency(txn.balance)} (Pending Payment)\n`;
    }
    text += '─'.repeat(28) + '\n';
    text += 'Thank you for your purchase!';
    return text;
  }

  const printerConnected = bluetoothPrinter.isSupported() && bluetoothPrinter.isConnected();

  const footer = (
    <div className="invoice-actions">
      <button
        className="btn btn-outline"
        style={{ flex: 1 }}
        onClick={handleShare}
        title="Share invoice via WhatsApp, SMS, etc."
      >
        📤 Share
      </button>
      <button
        className={`btn ${printerConnected ? 'btn-success' : 'btn-outline'}`}
        style={{ flex: 1 }}
        onClick={handlePrint}
        disabled={printing}
      >
        {printing ? '⏳ Printing...' : printerConnected ? '🖨️ Print (BT)' : '🖨️ Print'}
      </button>
      <button className="btn btn-primary" style={{ flex: 1 }} onClick={onClose}>
        Close
      </button>
    </div>
  );

  return (
    <Modal
      show={show}
      title="Invoice Details"
      onClose={onClose}
      footer={footer}
    >
      {loading && <div style={{ textAlign: 'center', padding: 20 }}>Loading invoice details...</div>}
      {!loading && txn && biz && (
        <div className="invoice-paper" id="invoicePaper">
          <div className="invoice-biz-name">{biz.name || 'My Business'}</div>
          {(biz.phone || biz.address) && (
            <div className="invoice-biz-detail">
              {[biz.phone, biz.address].filter(Boolean).join(' · ')}
            </div>
          )}
          <hr className="invoice-divider" />
          <div className="invoice-row">
            <span className="label">Invoice #</span>
            <span className="value">{txn.id}</span>
          </div>
          <div className="invoice-row">
            <span className="label">Date</span>
            <span className="value">
              {formatDate(txn.date)} {formatTime(txn.date)}
            </span>
          </div>
          <div className="invoice-row">
            <span className="label">Payment</span>
            <span className="value">Cash</span>
          </div>
          <hr className="invoice-divider" />
          <table className="invoice-items-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Item</th>
                <th>Qty</th>
                <th>Rate</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {txn.items.map((li, idx) => (
                <tr key={idx}>
                  <td>{idx + 1}</td>
                  <td>{li.itemName}</td>
                  <td style={{ textAlign: 'right' }}>{formatQty(li.quantity)}</td>
                  <td style={{ textAlign: 'right' }}>{formatCurrency(li.rate)}</td>
                  <td style={{ textAlign: 'right' }}>{formatCurrency(li.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="invoice-grand-total">
            <span>Total</span>
            <span>{formatCurrency(txn.total)}</span>
          </div>
          <div className="invoice-footer">
            Thank you for your purchase!<br />
            <strong>Balance: {formatCurrency(txn.balance)}</strong> {txn.balance === 0 ? '(Paid in Full)' : '(Pending Payment)'}
          </div>
        </div>
      )}
    </Modal>
  );
}
