import React from 'react';

export default function ConfirmDialog({ show, icon, title, description, onConfirm, onCancel }) {
  if (!show) return null;

  return (
    <div className="confirm-overlay" onClick={onCancel}>
      <div className="confirm-box" onClick={e => e.stopPropagation()}>
        <div className="cb-icon">{icon || '⚠️'}</div>
        <div className="cb-title">{title || 'Are you sure?'}</div>
        <div className="cb-desc">{description || 'This action cannot be undone.'}</div>
        <div className="cb-actions">
          <button className="btn btn-ghost" onClick={onCancel}>Cancel</button>
          <button className="btn btn-danger" onClick={onConfirm}>Confirm</button>
        </div>
      </div>
    </div>
  );
}
