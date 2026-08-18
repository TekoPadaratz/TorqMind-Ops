import { enqueueUpload } from './offline';

const API_BASE = '/api';

export type Session = {
  token: string;
  userId: string;
  username: string;
  fullName: string;
  role: string;
  roleLabel?: string;
  companyId?: number | null;
  branchId?: number | null;
};

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('torqmind.token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function handle(res: Response) {
  if (res.status === 204) return null;
  const text = await res.text();
  let data: any = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (!res.ok) {
    if (res.status === 401) {
      localStorage.removeItem('torqmind.token');
      localStorage.removeItem('torqmind.session');
      window.dispatchEvent(new Event('torqmind:unauthorized'));
    }
    const message = (data && (data.message || data.error)) || `Erro ${res.status}`;
    throw new Error(message);
  }
  return data;
}

export type LoginResponse = Partial<Session> & { totpRequired?: boolean; challenge?: string | null };

export async function apiLogin(username: string, password: string): Promise<LoginResponse> {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  return handle(res);
}

export async function apiLoginTotp(challenge: string, code: string): Promise<Session> {
  const res = await fetch(`${API_BASE}/auth/login/2fa`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ challenge, code })
  });
  return handle(res);
}

export async function apiGet(path: string) {
  const res = await fetch(`${API_BASE}${path}`, { headers: { ...authHeaders() } });
  return handle(res);
}

export async function apiPost(path: string, body: unknown) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body ?? {})
  });
  return handle(res);
}

export async function apiPut(path: string, body: unknown) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body ?? {})
  });
  return handle(res);
}

export async function apiDelete(path: string) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
    headers: { ...authHeaders() }
  });
  return handle(res);
}

export async function apiUpload(path: string, file: File, geo?: { lat: number; lng: number } | null) {
  const form = new FormData();
  form.append('file', file);
  if (geo && Number.isFinite(geo.lat) && Number.isFinite(geo.lng)) {
    form.append('lat', String(geo.lat));
    form.append('lng', String(geo.lng));
  }
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { ...authHeaders() },
    body: form
  });
  return handle(res);
}

export async function uploadOrQueue(
  path: string,
  file: File,
  geo?: { lat: number; lng: number } | null
): Promise<{ queued: boolean; attachment?: any }> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    await enqueueUpload(path, file, geo ?? null);
    return { queued: true };
  }
  try {
    const attachment = await apiUpload(path, file, geo);
    return { queued: false, attachment };
  } catch (e) {
    if (e instanceof TypeError) {
      await enqueueUpload(path, file, geo ?? null);
      return { queued: true };
    }
    throw e;
  }
}

export function currentGeo(timeoutMs = 6000): Promise<{ lat: number; lng: number } | null> {
  return new Promise((resolve) => {
    if (typeof navigator === 'undefined' || !('geolocation' in navigator)) {
      resolve(null);
      return;
    }
    let done = false;
    const finish = (v: { lat: number; lng: number } | null) => {
      if (!done) {
        done = true;
        resolve(v);
      }
    };
    try {
      navigator.geolocation.getCurrentPosition(
        (pos) => finish({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        () => finish(null),
        { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 60000 }
      );
    } catch {
      finish(null);
    }
    setTimeout(() => finish(null), timeoutMs + 500);
  });
}

export async function apiPatch(path: string, body: unknown) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body ?? {})
  });
  return handle(res);
}

export async function apiUploadForm(path: string, form: FormData) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { ...authHeaders() },
    body: form
  });
  return handle(res);
}

export async function apiPostIdempotent(path: string, body: unknown, idempotencyKey: string) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
      ...authHeaders()
    },
    body: JSON.stringify(body ?? {})
  });
  return handle(res);
}

export async function apiBlob(path: string): Promise<Blob> {
  // A URL do anexo já vem completa do backend (/api/attachments/{id}); evita /api duplicado.
  const url = path.startsWith('/api') ? path : `${API_BASE}${path}`;
  const res = await fetch(url, { headers: { ...authHeaders() } });
  if (!res.ok) {
    if (res.status === 401) {
      localStorage.removeItem('torqmind.token');
      localStorage.removeItem('torqmind.session');
      window.dispatchEvent(new Event('torqmind:unauthorized'));
    }
    throw new Error(`Erro ${res.status}`);
  }
  return res.blob();
}
