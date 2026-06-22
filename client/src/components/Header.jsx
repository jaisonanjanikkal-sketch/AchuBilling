import React from 'react';

export default function Header({ showBack, title, onBack }) {
  if (showBack) {
    return (
      <header className="app-header">
        <button className="header-back-btn" onClick={onBack} aria-label="Go back">←</button>
        <span className="header-title">{title || 'New Sale Invoice'}</span>
      </header>
    );
  }

  return (
    <header className="app-header">
      <div className="logo">Anjani<span>kkal</span></div>
      <span className="header-subtitle">Smart Billing</span>
    </header>
  );
}
