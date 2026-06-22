import React, { useState, useEffect } from 'react';
import { transactionsApi } from '../api/api';
import TransactionCard from '../components/TransactionCard';

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

export default function DashboardPage({ onViewInvoice, onEditInvoice, onDeleteTransaction, refreshKey }) {
  const [stats, setStats] = useState(null);
  const [allTxns, setAllTxns] = useState([]);

  useEffect(() => {
    loadData();
  }, [refreshKey]);

  async function loadData() {
    try {
      const [statsData, txnsData] = await Promise.all([
        transactionsApi.getStats(),
        transactionsApi.getAll(),
      ]);
      setStats(statsData);
      setAllTxns(txnsData);
    } catch (err) {
      console.error('DashboardPage loadData error:', err);
    }
  }

  const topItems = stats?.topItems || [];
  const maxQty = topItems.length ? topItems[0].totalQty : 1;
  const lowStockItems = stats?.lowStockItems || [];

  return (
    <div className="tab-view">
      <div className="dash-section">
        {/* Metrics Grid */}
        <div className="dash-metric-grid">
          <div className="dash-metric">
            <div className="dm-label">Total Revenue</div>
            <div className="dm-value" style={{color:'var(--success)'}}>
              {formatCurrency(stats?.totalRevenue || 0)}
            </div>
            <div className="dm-sub">All time sales</div>
          </div>
          <div className="dash-metric">
            <div className="dm-label">Avg Invoice</div>
            <div className="dm-value" style={{color:'var(--primary)'}}>
              {formatCurrency(stats?.avgInvoice || 0)}
            </div>
            <div className="dm-sub">Per transaction</div>
          </div>
          <div className="dash-metric">
            <div className="dm-label">Items Sold</div>
            <div className="dm-value" style={{color:'var(--warning)'}}>
              {formatQty(stats?.totalItemsSold || 0)}
            </div>
            <div className="dm-sub">Total quantity</div>
          </div>
          <div className="dash-metric">
            <div className="dm-label">Inventory Value</div>
            <div className="dm-value" style={{color:'var(--text)'}}>
              {formatCurrency(stats?.inventoryValue || 0)}
            </div>
            <div className="dm-sub">At sale price</div>
          </div>
        </div>

        {/* Top Selling Items */}
        <div className="section-header" style={{padding:0,marginBottom:10}}>
          <h2 className="section-title">Top Selling Items</h2>
        </div>
        <div className="alert-list" style={{marginBottom:20}}>
          {topItems.length === 0 ? (
            <div className="empty-state" style={{padding:20}}>
              <div className="es-desc">No sales data yet</div>
            </div>
          ) : (
            topItems.map(item => {
              const pct = Math.round((item.totalQty / maxQty) * 100);
              return (
                <div key={item.itemId} className="alert-card">
                  <div style={{flex:1}}>
                    <div className="alert-name">{item.itemName}</div>
                    <div className="alert-stock">{formatQty(item.totalQty)} units sold</div>
                    <div className="progress-bar-wrap">
                      <div className="progress-bar" style={{width:`${pct}%`,background:'linear-gradient(90deg,#2563eb,#60a5fa)'}}></div>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Low Stock Alerts */}
        <div className="section-header" style={{padding:0,marginBottom:10}}>
          <h2 className="section-title">Low Stock Alerts</h2>
        </div>
        <div className="alert-list" style={{marginBottom:20}}>
          {lowStockItems.length === 0 ? (
            <div className="empty-state" style={{padding:20}}>
              <div className="es-desc">All items have healthy stock levels 🎉</div>
            </div>
          ) : (
            lowStockItems.map(item => (
              <div key={item.id} className="alert-card">
                <div className={`alert-dot ${item.stock < 0 ? 'red' : 'amber'}`}></div>
                <div className="alert-info">
                  <div className="alert-name">{item.name}</div>
                  <div className="alert-stock">Category: {item.category || 'General'}</div>
                </div>
                <div style={{fontSize:14,fontWeight:700,color:item.stock < 0 ? 'var(--accent)' : 'var(--warning)'}}>
                  {formatQty(item.stock)} left
                </div>
              </div>
            ))
          )}
        </div>

        {/* All Transactions */}
        <div className="section-header" style={{padding:0,margin:'20px 0 10px'}}>
          <h2 className="section-title">All Transactions</h2>
        </div>
        <div>
          {allTxns.length === 0 ? (
            <div className="empty-state" style={{padding:20}}>
              <div className="es-desc">No transactions recorded</div>
            </div>
          ) : (
            allTxns.map(t => (
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
    </div>
  );
}
