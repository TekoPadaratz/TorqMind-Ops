// Service worker de limpeza: remove qualquer SW/cache antigo preso em aparelhos
// (especialmente PWA instalado no iOS) e recarrega para a versão fresca via rede.
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      try {
        const keys = await caches.keys();
        await Promise.all(keys.map((k) => caches.delete(k)));
      } catch (e) {
        // ignora falha de limpeza de cache
      }
      try {
        await self.registration.unregister();
      } catch (e) {
        // ignora falha de unregister
      }
      const clients = await self.clients.matchAll({ type: 'window' });
      clients.forEach((client) => {
        if ('navigate' in client) {
          client.navigate(client.url);
        }
      });
    })()
  );
});
