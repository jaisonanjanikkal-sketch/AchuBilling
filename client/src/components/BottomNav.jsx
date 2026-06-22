import React from 'react';

const tabs = [
  { key: 'home', icon: '🏠', label: 'HOME' },
  { key: 'dashboard', icon: '📊', label: 'DASHBOARD' },
  { key: 'items', icon: '📦', label: 'ITEMS' },
  { key: 'menu', icon: '☰', label: 'MENU' },
];

export default function BottomNav({ currentTab, onTabChange }) {
  return (
    <nav className="bottom-nav">
      {tabs.map(tab => (
        <button
          key={tab.key}
          className={`nav-item${currentTab === tab.key ? ' active' : ''}`}
          onClick={() => onTabChange(tab.key)}
        >
          <span className="nav-icon">{tab.icon}</span>
          {tab.label}
        </button>
      ))}
    </nav>
  );
}
