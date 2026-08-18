// Fila offline de uploads (fotos/evidencias) em IndexedDB. Reenvia ao reconectar.
// NAO usa Service Worker de cache (evita regressao de cache preso no iOS).
const DB_NAME = 'torqmind-offline';
const DB_VERSION = 1;
const STORE = 'uploads';
const API_BASE = '/api';

type QueuedUpload = {
  id?: number;
  path: string;
  fileName: string;
  mime: string;
  blob: Blob;
  lat: number | null;
  lng: number | null;
  createdAt: number;
};

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: 'id', autoIncrement: true });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function run<T>(mode: IDBTransactionMode, fn: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return openDb().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const t = db.transaction(STORE, mode);
        const req = fn(t.objectStore(STORE));
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
        t.oncomplete = () => db.close();
      })
  );
}

function notifyChanged() {
  window.dispatchEvent(new Event('torqmind:offline-changed'));
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('torqmind.token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function enqueueUpload(
  path: string,
  file: File,
  geo: { lat: number; lng: number } | null
): Promise<void> {
  const record: QueuedUpload = {
    path,
    fileName: file.name || 'foto.jpg',
    mime: file.type || 'application/octet-stream',
    blob: file,
    lat: geo ? geo.lat : null,
    lng: geo ? geo.lng : null,
    createdAt: Date.now()
  };
  await run('readwrite', (store) => store.add(record));
  notifyChanged();
}

export async function pendingCount(): Promise<number> {
  try {
    return await run<number>('readonly', (store) => store.count());
  } catch {
    return 0;
  }
}

async function allQueued(): Promise<QueuedUpload[]> {
  const items = await run<QueuedUpload[]>('readonly', (store) => store.getAll() as IDBRequest<QueuedUpload[]>);
  return items.sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
}

let flushing = false;

/** Reenvia os itens pendentes em ordem. Idempotente no servidor (dedup por checksum). */
export async function flushUploads(): Promise<void> {
  if (flushing) return;
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return;
  if (!localStorage.getItem('torqmind.token')) return;
  flushing = true;
  try {
    const items = await allQueued();
    for (const item of items) {
      const form = new FormData();
      form.append('file', new File([item.blob], item.fileName, { type: item.mime }));
      if (item.lat != null && item.lng != null) {
        form.append('lat', String(item.lat));
        form.append('lng', String(item.lng));
      }
      let res: Response;
      try {
        res = await fetch(`${API_BASE}${item.path}`, { method: 'POST', headers: { ...authHeaders() }, body: form });
      } catch {
        break; // rede indisponivel de novo: para e tenta na proxima
      }
      if (res.ok) {
        if (item.id != null) await run('readwrite', (store) => store.delete(item.id as number));
      } else if (res.status === 401 || res.status === 408 || res.status === 429 || res.status >= 500) {
        break; // temporario/auth: mantem na fila
      } else if (item.id != null) {
        await run('readwrite', (store) => store.delete(item.id as number)); // 4xx definitivo: descarta p/ nao travar
      }
    }
  } finally {
    flushing = false;
    notifyChanged();
  }
}
