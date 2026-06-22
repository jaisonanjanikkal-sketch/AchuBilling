import React, { useState, useEffect } from 'react';

/**
 * InstallPrompt — Shows a smart banner prompting the user to install the PWA.
 * Captures the beforeinstallprompt event and provides a native-feeling install experience.
 * Only visible on mobile browsers when the app is not yet installed.
 */
export default function InstallPrompt() {
  const [deferredPrompt, setDeferredPrompt] = useState(null);
  const [showBanner, setShowBanner] = useState(false);
  const [isInstalled, setIsInstalled] = useState(false);

  useEffect(() => {
    // Check if app is already installed (standalone mode)
    if (window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone) {
      setIsInstalled(true);
      return;
    }

    // Check if user previously dismissed the banner
    const dismissed = localStorage.getItem('pwa-install-dismissed');
    if (dismissed) {
      const dismissedAt = Number(dismissed);
      // Show again after 7 days
      if (Date.now() - dismissedAt < 7 * 24 * 60 * 60 * 1000) {
        return;
      }
    }

    function handleBeforeInstall(e) {
      e.preventDefault();
      setDeferredPrompt(e);
      setShowBanner(true);
    }

    function handleAppInstalled() {
      setIsInstalled(true);
      setShowBanner(false);
      setDeferredPrompt(null);
    }

    window.addEventListener('beforeinstallprompt', handleBeforeInstall);
    window.addEventListener('appinstalled', handleAppInstalled);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstall);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  async function handleInstall() {
    if (!deferredPrompt) return;

    deferredPrompt.prompt();
    const result = await deferredPrompt.userChoice;

    if (result.outcome === 'accepted') {
      setShowBanner(false);
    }
    setDeferredPrompt(null);
  }

  function handleDismiss() {
    setShowBanner(false);
    localStorage.setItem('pwa-install-dismissed', String(Date.now()));
  }

  if (!showBanner || isInstalled) return null;

  return (
    <div className="install-prompt-banner" id="install-prompt">
      <div className="ipb-content">
        <div className="ipb-icon">📲</div>
        <div className="ipb-text">
          <div className="ipb-title">Install Anjanikkal</div>
          <div className="ipb-desc">Add to home screen for the best experience</div>
        </div>
      </div>
      <div className="ipb-actions">
        <button className="ipb-dismiss" onClick={handleDismiss}>Later</button>
        <button className="ipb-install" onClick={handleInstall}>Install</button>
      </div>
    </div>
  );
}
