import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import './App.css';
import { ToastProvider } from './components/Toast.jsx';
import { initDB } from './services/sqlite.js';

async function bootstrap() {
  try {
    await initDB();
  } catch (err) {
    console.error('Failed to initialize database:', err);
  }

  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <ToastProvider>
        <App />
      </ToastProvider>
    </React.StrictMode>,
  );
}

bootstrap();
