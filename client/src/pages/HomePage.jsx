import React, { useState, useEffect } from 'react';
import { transactionsApi } from '../api/api';
import TransactionCard from '../components/TransactionCard';

// Format helpers
function formatCurrency(n) {
  return '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}
function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export default function HomePage({ onTabChange, onAddSale, onViewInvoice, onEditInvoice, onDeleteTransaction, refreshKey }) {
  const [stats, setStats] = useState(null);
  const [recentTxns, setRecentTxns] = useState([]);

  useEffect(() => {
    loadData();
  }, [refreshKey]);

  async function loadData() {
    try {
      const [statsData, txnsData] = await Promise.all([
        transactionsApi.getStats(),
        transactionsApi.getAll(10),
      ]);
      setStats(statsData);
      setRecentTxns(txnsData);
    } catch (err) {
      console.error('HomePage loadData error:', err);
    }
  }

  return (
    <div className="tab-view">
      {/* Stats Grid */}
      <div className="home-stats">
        <div className="stat-card blue">
          <div className="stat-icon">💰</div>
          <div className="stat-value">{formatCurrency(stats?.todaySales || 0)}</div>
          <div className="stat-label">Today's Sales</div>
        </div>
        <div className="stat-card green">
          <div className="stat-icon">📦</div>
          <div className="stat-value">{stats?.totalItemsSold || 0}</div>
          <div className="stat-label">Items Sold</div>
        </div>
        <div className="stat-card amber">
          <div className="stat-icon">📋</div>
          <div className="stat-value">{stats?.totalTransactions || 0}</div>
          <div className="stat-label">Total Invoices</div>
        </div>
        <div className="stat-card red">
          <div className="stat-icon">⚠️</div>
          <div className="stat-value">{stats?.lowStockCount || 0}</div>
          <div className="stat-label">Low Stock Alerts</div>
        </div>
      </div>

      {/* Quick Links */}
      <div className="quick-links">
        <div className="quick-links-grid">
          <div className="quick-link" onClick={() => onTabChange('items')}>
            <div className="ql-icon" style={{background:'#dbeafe',color:'#2563eb'}}>📦</div>
            <span className="ql-label">Stock Summary</span>
          </div>
          <div className="quick-link" onClick={() => onTabChange('dashboard')}>
            <div className="ql-icon" style={{background:'#dcfce7',color:'#16a34a'}}>📊</div>
            <span className="ql-label">Analytics</span>
          </div>
          <div className="quick-link" onClick={onAddSale}>
            <div className="ql-icon" style={{background:'#fee2e2',color:'#dc2626'}}>🧾</div>
            <span className="ql-label">New Sale</span>
          </div>
          <div className="quick-link" onClick={() => onTabChange('menu')}>
            <div className="ql-icon" style={{background:'#fef3c7',color:'#d97706'}}>⚙️</div>
            <span className="ql-label">Settings</span>
          </div>
        </div>
      </div>

      {/* Recent Transactions */}
      <div className="section-header">
        <h2 className="section-title">Recent Transactions</h2>
        <button className="section-link" onClick={() => onTabChange('dashboard')}>View All</button>
      </div>
      <div className="txn-list">
        {recentTxns.length === 0 ? (
          <div className="empty-state">
            <div className="es-icon">📋</div>
            <div className="es-title">No transactions yet</div>
            <div className="es-desc">Tap "Add New Sale" to create your first invoice</div>
          </div>
        ) : (
          recentTxns.map(t => (
            <TransactionCard
              key={t.id}
              txn={t}
              onView={onViewInvoice}
              onEdit={onEditInvoice}
              onDelete={onDeleteTransaction}
            />
          ))
        )}
      </div>
    </div>
  );
}
