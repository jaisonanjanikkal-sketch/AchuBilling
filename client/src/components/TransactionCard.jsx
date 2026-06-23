import React, { useState } from 'react';
import { bluetoothPrinter } from '../utils/bluetoothPrinter';
import { useToast } from './Toast';
import { businessApi } from '../api/api';
import { shareInvoiceAsImage } from '../utils/shareInvoice';

function formatCurrency(n) {
  return '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}
function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}
function formatTime(iso) {
  return new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
}

export default function TransactionCard({ txn, onView, onEdit, onDelete }) {
  const [printing, setPrinting] = useState(false);
  const showToast = useToast();

  async function handlePrint(e) {
    e.stopPropagation();
    if (!bluetoothPrinter.isConnected()) {
      showToast('Printer not connected. Go to Menu to link printer.', 'error');
      return;
    }

    setPrinting(true);
    try {
      const biz = await businessApi.get();
      await bluetoothPrinter.printInvoice(txn, biz);
      showToast('Printing...');
    } catch (err) {
      showToast('Print failed', 'error');
    } finally {
      setPrinting(false);
    }
  }

  async function handleShare(e) {
    e.stopPropagation();
    try {
      const biz = await businessApi.get();
      await shareInvoiceAsImage(txn, biz, showToast);
    } catch (err) {
      showToast('Failed to share invoice', 'error');
    }
  }

  const isPaid = txn.balance === 0 || txn.balance === null || txn.balance === undefined;

  return (
    <div className="txn-card-wrapper" style={{ marginBottom: 12 }}>
      <div className="txn-card" onClick={() => onView(txn.id)}>
        <div className="txn-icon sale">💵</div>
        <div className="txn-info">
          <div className="txn-party">Cash Sale</div>
          <div className="txn-meta">
            #{txn.id} · {formatDate(txn.date)} {formatTime(txn.date)}
          </div>
        </div>
        <div className="txn-amount">
          <div className="txn-total">{formatCurrency(txn.total)}</div>
          <div className="txn-balance">
            <span className={`txn-badge ${isPaid ? 'paid' : 'unpaid'}`}>
              {isPaid ? 'Paid' : 'Unpaid'}
            </span>
          </div>
        </div>
      </div>

      <div className="txn-actions" style={{
        display: 'flex',
        gap: 8,
        padding: '8px 12px',
        background: 'var(--card-light)',
        border: '1px solid var(--border)',
        borderTop: 'none',
        borderRadius: '0 0 var(--radius-md) var(--radius-md)',
        marginTop: -4
      }} onClick={(e) => e.stopPropagation()}>
        <button className="btn btn-outline btn-sm" style={{ flex: 1 }} onClick={handlePrint} disabled={printing}>
          {printing ? '⏳...' : '🖨️ Print'}
        </button>
        <button className="btn btn-outline btn-sm" style={{ flex: 1 }} onClick={handleShare}>
          📤 Share
        </button>
        <button className="btn btn-outline btn-sm" style={{ flex: 1 }} onClick={(e) => { e.stopPropagation(); if (onEdit) onEdit(txn); }}>
          ✏️ Edit
        </button>
        <button className="btn btn-outline btn-sm" style={{ flex: 1, color: '#dc2626' }} onClick={(e) => { e.stopPropagation(); if (onDelete) onDelete(txn.id); }}>
          🗑️ Del
        </button>
      </div>
    </div>
  );
}
