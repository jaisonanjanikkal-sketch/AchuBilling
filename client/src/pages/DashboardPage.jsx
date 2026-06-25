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
  const [subView, setSubView] = useState(null); // null, 'topSelling', or 'allTransactions'
  const [txSearch, setTxSearch] = useState('');

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

  // Sort items in the order of low stock (lowest stock level first)
  const sortedLowStockItems = [...lowStockItems].sort((a, b) => a.stock - b.stock);

  const filteredTxns = allTxns.filter(t => {
    if (!txSearch) return true;
    const query = txSearch.toLowerCase();
    const partyName = t.partyName?.toLowerCase() || '';
    const idStr = t.id?.toString() || '';
    return partyName.includes(query) || idStr.includes(query);
  });

  // Top Selling Items Subview
  if (subView === 'topSelling') {
    return (
      <div className="tab-view">
        <div className="subview-header">
          <button className="subview-back-btn" onClick={() => setSubView(null)}>←</button>
          <span className="subview-title">Top Selling Items</span>
        </div>
        <div className="subview-content">
          <div className="alert-list">
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
        </div>
      </div>
    );
  }

  // All Transactions Subview
  if (subView === 'allTransactions') {
    return (
      <div className="tab-view">
        <div className="subview-header">
          <button className="subview-back-btn" onClick={() => setSubView(null)}>←</button>
          <span className="subview-title">All Transactions</span>
        </div>
        <div className="subview-content">
          <div className="subview-search-bar">
            <div className="search-input-wrap">
              <span className="search-icon">🔍</span>
              <input
                type="text"
                placeholder="Search transaction by name or invoice..."
                value={txSearch}
                onChange={e => setTxSearch(e.target.value)}
                autoComplete="off"
              />
            </div>
          </div>
          <div>
            {filteredTxns.length === 0 ? (
              <div className="empty-state" style={{padding:20}}>
                <div className="es-desc">{txSearch ? 'No matching transactions found' : 'No transactions recorded'}</div>
              </div>
            ) : (
              filteredTxns.map(t => (
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

  // Low Stock Alerts Subview
  if (subView === 'lowStock') {
    return (
      <div className="tab-view">
        <div className="subview-header">
          <button className="subview-back-btn" onClick={() => setSubView(null)}>←</button>
          <span className="subview-title">Low Stock Alerts</span>
        </div>
        <div className="subview-content">
          <div className="alert-list">
            {sortedLowStockItems.length === 0 ? (
              <div className="empty-state" style={{padding:20}}>
                <div className="es-desc">All items have healthy stock levels 🎉</div>
              </div>
            ) : (
              sortedLowStockItems.map(item => (
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
        </div>
      </div>
    );
  }

  // Main Dashboard View
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

        {/* Dashboard Navigation Cards */}
        <div className="dash-menu-list">
          <div className="dash-menu-card" onClick={() => setSubView('topSelling')}>
            <div className="dash-menu-icon" style={{background:'var(--primary-light)', color:'var(--primary)'}}>📈</div>
            <div className="dash-menu-info">
              <div className="dash-menu-title">Top Selling Items</div>
              <div className="dash-menu-subtitle">{topItems.length} products with sales</div>
            </div>
            <span className="dash-menu-arrow">→</span>
          </div>

          <div className="dash-menu-card" onClick={() => setSubView('allTransactions')}>
            <div className="dash-menu-icon" style={{background:'var(--success-light)', color:'var(--success)'}}>🧾</div>
            <div className="dash-menu-info">
              <div className="dash-menu-title">All Transactions</div>
              <div className="dash-menu-subtitle">{allTxns.length} invoices recorded</div>
            </div>
            <span className="dash-menu-arrow">→</span>
          </div>

          <div className="dash-menu-card" onClick={() => setSubView('lowStock')}>
            <div className="dash-menu-icon" style={{background:'var(--accent-light)', color:'var(--accent)'}}>⚠️</div>
            <div className="dash-menu-info">
              <div className="dash-menu-title">Low Stock Alerts</div>
              <div className="dash-menu-subtitle">
                {lowStockItems.length === 0
                  ? 'All items have healthy stock levels'
                  : `${lowStockItems.length} items needing attention`
                }
              </div>
            </div>
            <span className="dash-menu-arrow">→</span>
          </div>
        </div>
      </div>
    </div>
  );
}
