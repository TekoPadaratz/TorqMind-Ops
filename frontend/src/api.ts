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

export async function apiLogin(username: string, password: string): Promise<Session> {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
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

export async function apiDelete(path: string) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
    headers: { ...authHeaders() }
  });
  return handle(res);
}

export async function apiUpload(path: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { ...authHeaders() },
    body: form
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
