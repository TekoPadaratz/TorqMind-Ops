import React, { useEffect, useState } from 'react';
import { pendingCount, flushUploads } from '../offline';

export function OfflineBadge() {
  const [count, setCount] = useState(0);
  const [online, setOnline] = useState(typeof navigator === 'undefined' ? true : navigator.onLine);

  useEffect(() => {
    let active = true;
    const refresh = () => pendingCount().then((n) => active && setCount(n));

    refresh();
    if (navigator.onLine) {
      flushUploads();
    }

    const onChange = () => refresh();
    const onOnline = () => {
      setOnline(true);
      flushUploads();
    };
    const onOffline = () => setOnline(false);

    window.addEventListener('torqmind:offline-changed', onChange);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      active = false;
      window.removeEventListener('torqmind:offline-changed', onChange);
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  if (online && count === 0) return null;

  const label = !online
    ? count > 0
      ? `Offline · ${count} pendente${count > 1 ? 's' : ''}`
      : 'Offline'
    : `Sincronizando ${count} pendente${count > 1 ? 's' : ''}`;

  return (
    <span className="chip offline-chip" title="Fotos aguardando envio serão sincronizadas automaticamente">
      {label}
    </span>
  );
}
