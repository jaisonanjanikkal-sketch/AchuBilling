import React, { useState, useEffect, useCallback, createContext, useContext } from 'react';

// Toast context for global access
const ToastContext = createContext(null);

export function useToast() {
  return useContext(ToastContext);
}

export function ToastProvider({ children }) {
  const [toast, setToast] = useState(null);

  const showToast = useCallback((message, type = 'success') => {
    setToast({ message, type, id: Date.now() });
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => {
      setToast(prev => prev ? { ...prev, exiting: true } : null);
      setTimeout(() => setToast(null), 300);
    }, 2500);
    return () => clearTimeout(timer);
  }, [toast?.id]);

  return (
    <ToastContext.Provider value={showToast}>
      {children}
      {toast && (
        <div className="toast-container">
          <div className={`toast visible ${toast.type}${toast.exiting ? ' exiting' : ''}`}>
            {toast.message}
          </div>
        </div>
      )}
    </ToastContext.Provider>
  );
}
