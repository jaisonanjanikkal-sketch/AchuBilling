import React, { useState, useEffect } from 'react';
import Header from './components/Header';
import BottomNav from './components/BottomNav';
import HomePage from './pages/HomePage';
import DashboardPage from './pages/DashboardPage';
import ItemsPage from './pages/ItemsPage';
import MenuPage from './pages/MenuPage';
import AddSalePage from './pages/AddSalePage';

import AddItemModal from './modals/AddItemModal';
import BusinessProfileModal from './modals/BusinessProfileModal';
import InvoiceModal from './modals/InvoiceModal';
import ConfirmDialog from './components/ConfirmDialog';
import InstallPrompt from './components/InstallPrompt';
import { useToast } from './components/Toast';
import { dataApi, businessApi, transactionsApi } from './api/api';

export default function App() {
  const showToast = useToast();
  const [currentTab, setCurrentTab] = useState('home');
  const [isAddSaleOpen, setIsAddSaleOpen] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [businessName, setBusinessName] = useState('');
  const [editingTxn, setEditingTxn] = useState(null);

  function handleEditInvoice(txn) {
    setEditingTxn(txn);
    setIsAddSaleOpen(true);
  }

  // Modal states
  const [addItemConfig, setAddItemConfig] = useState({ show: false, item: null });
  const [isBizProfileOpen, setIsBizProfileOpen] = useState(false);
  const [invoiceConfig, setInvoiceConfig] = useState({ show: false, txnId: null });

  // Custom Confirm Dialog state
  const [confirmConfig, setConfirmConfig] = useState({
    show: false,
    icon: '⚠️',
    title: 'Are you sure?',
    description: 'This action cannot be undone.',
    onConfirm: () => {},
    onCancel: () => {}
  });

  const triggerRefresh = () => setRefreshKey(prev => prev + 1);

  useEffect(() => {
    async function loadBusiness() {
      try {
        const data = await businessApi.get();
        setBusinessName(data.name || 'My Business');
      } catch (err) {
        console.error('Failed to load business details:', err);
      }
    }
    loadBusiness();
  }, [refreshKey]);

  function handleTabChange(tab) {
    setCurrentTab(tab);
    setIsAddSaleOpen(false);
  }

  function handleAddSale() {
    setIsAddSaleOpen(true);
  }

  function handleViewInvoice(id) {
    setInvoiceConfig({ show: true, txnId: id });
  }

  function handleEditItem(item) {
    setAddItemConfig({ show: true, item });
  }

  function triggerConfirm(title, description, icon, onConfirm) {
    setConfirmConfig({
      show: true,
      icon: icon || '⚠️',
      title: title || 'Are you sure?',
      description: description || 'This action cannot be undone.',
      onConfirm: () => {
        onConfirm();
        closeConfirm();
      },
      onCancel: closeConfirm
    });
  }

  function closeConfirm() {
    setConfirmConfig(prev => ({ ...prev, show: false }));
  }

  // Data Actions
  async function handleSeedData() {
    triggerConfirm(
      'Load Demo Data?',
      'This will add sample items and transactions to your app. Existing data will be preserved.',
      '📥',
      async () => {
        try {
          const res = await dataApi.seed();
          showToast(res.message || 'Demo data loaded!');
          triggerRefresh();
        } catch (err) {
          showToast(err.message || 'Failed to seed data', 'error');
        }
      }
    );
  }

  async function handleDeleteTransaction(id) {
    triggerConfirm(
      'Delete Transaction?',
      `Are you sure you want to delete invoice #${id}? This will also revert the item stocks.`,
      '🗑️',
      async () => {
        try {
          await transactionsApi.delete(id);
          showToast('Transaction deleted');
          triggerRefresh();
        } catch (err) {
          showToast('Failed to delete', 'error');
        }
      }
    );
  }

  async function handleResetData() {
    triggerConfirm(
      'Reset All Data?',
      'This will permanently delete all items, transactions, and settings. This cannot be undone.',
      '🗑️',
      async () => {
        try {
          const res = await dataApi.reset();
          showToast(res.message || 'All data has been reset');
          triggerRefresh();
        } catch (err) {
          showToast(err.message || 'Failed to reset data', 'error');
        }
      }
    );
  }

  async function handleExportData() {
    try {
      const data = await dataApi.export();
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `anjanikkal_backup_${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      showToast('Data exported successfully!');
    } catch (err) {
      showToast(err.message || 'Failed to export data', 'error');
    }
  }

  return (
    <div className="app-shell">
      <InstallPrompt />
      {isAddSaleOpen ? (
        <>
          <Header
            showBack={true}
            title={editingTxn ? `Edit Sale Invoice #${editingTxn.id}` : "New Sale Invoice"}
            onBack={() => {
              triggerConfirm(
                editingTxn ? 'Discard Changes?' : 'Discard Invoice?',
                editingTxn ? 'You have unsaved changes. Are you sure you want to discard them?' : 'You have unsaved changes. Are you sure you want to go back?',
                '⚠️',
                () => {
                  setEditingTxn(null);
                  setIsAddSaleOpen(false);
                }
              );
            }}
          />
          <AddSalePage
            editTxn={editingTxn}
            onClose={() => {
              setEditingTxn(null);
              setIsAddSaleOpen(false);
            }}
            onSaved={() => {
              setIsAddSaleOpen(false);
              setEditingTxn(null);
              setCurrentTab('home');
              triggerRefresh();
            }}
          />
        </>
      ) : (
        <>
          <Header />
          <main className="main-content">
             {currentTab === 'home' && (
              <HomePage
                refreshKey={refreshKey}
                onTabChange={handleTabChange}
                onAddSale={handleAddSale}
                onViewInvoice={handleViewInvoice}
                onEditInvoice={handleEditInvoice}
                onDeleteTransaction={handleDeleteTransaction}
              />
            )}
            {currentTab === 'dashboard' && (
               <DashboardPage
                refreshKey={refreshKey}
                onViewInvoice={handleViewInvoice}
                onEditInvoice={handleEditInvoice}
                onDeleteTransaction={handleDeleteTransaction}
              />
            )}
            {currentTab === 'items' && (
              <ItemsPage
                refreshKey={refreshKey}
                onEditItem={handleEditItem}
                onAddItem={() => setAddItemConfig({ show: true, item: null })}
              />
            )}
            {currentTab === 'menu' && (
              <MenuPage
                businessName={businessName}
                onEditProfile={() => setIsBizProfileOpen(true)}
                onSeedData={handleSeedData}
                onResetData={handleResetData}
                onExportData={handleExportData}
              />
            )}
          </main>
          <BottomNav currentTab={currentTab} onTabChange={handleTabChange} />
          <button className="fab" onClick={handleAddSale}>＋ Add New Sale</button>
        </>
      )}

      {/* Modals */}
      <AddItemModal
        show={addItemConfig.show}
        editItem={addItemConfig.item}
        onClose={() => setAddItemConfig({ show: false, item: null })}
        onSaved={() => {
          triggerRefresh();
          setAddItemConfig({ show: false, item: null });
        }}
      />

      <BusinessProfileModal
        show={isBizProfileOpen}
        onClose={() => setIsBizProfileOpen(false)}
        onSaved={() => {
          triggerRefresh();
          setIsBizProfileOpen(false);
        }}
      />

      <InvoiceModal
        show={invoiceConfig.show}
        transactionId={invoiceConfig.txnId}
        onClose={() => setInvoiceConfig({ show: false, txnId: null })}
      />

      <ConfirmDialog
        show={confirmConfig.show}
        icon={confirmConfig.icon}
        title={confirmConfig.title}
        description={confirmConfig.description}
        onConfirm={confirmConfig.onConfirm}
        onCancel={confirmConfig.onCancel}
      />
    </div>
  );
}
