import React, { useEffect, useState } from 'react';
import { apiDelete, apiGet, apiPost } from '../api';

type Company = { id: number; name: string };
type ApiKeyView = {
  id: number;
  name: string;
  maskedKey: string;
  active: boolean;
  companyId: number;
  createdAt: string;
  lastUsedAt: string | null;
};
type Props = {
  companies: Company[];
  onOk: (msg: string) => void;
  onError: (e: unknown) => void;
};

export default function AdminApiKeys({ companies, onOk, onError }: Props) {
  const [companyId, setCompanyId] = useState<number | ''>('');
  const [name, setName] = useState('');
  const [keys, setKeys] = useState<ApiKeyView[]>([]);
  const [created, setCreated] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function load(id: number) {
    apiGet(`/admin/api-keys?companyId=${id}`).then(setKeys).catch(onError);
  }

  useEffect(() => {
    if (typeof companyId === 'number') load(companyId);
    else setKeys([]);
    setCreated(null);
  }, [companyId]);

  async function create() {
    if (typeof companyId !== 'number') return;
    setBusy(true);
    setCreated(null);
    try {
      const res = await apiPost('/admin/api-keys', { companyId, name: name.trim() || 'Chave' });
      setCreated(res.key);
      setName('');
      load(companyId);
      onOk('Chave criada. Copie agora — ela não será exibida novamente.');
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  async function revoke(id: number) {
    if (!window.confirm('Revogar esta chave? Integrações que a usam vão parar de funcionar.')) return;
    try {
      await apiDelete(`/admin/api-keys/${id}`);
      if (typeof companyId === 'number') load(companyId);
      onOk('Chave revogada.');
    } catch (e) {
      onError(e);
    }
  }

  return (
    <section className="card">
      <h2>API pública (somente leitura)</h2>
      <p className="muted small">
        Gere uma chave para integrar os dados da empresa (BI/ERP). Envie no header <code>X-API-Key</code> para{' '}
        <code>/api/public/v1/…</code>. A chave aparece uma única vez e é guardada apenas como hash.
      </p>
      <label className="field-label">Empresa
        <select value={companyId} onChange={(e) => setCompanyId(e.target.value === '' ? '' : Number(e.target.value))}>
          <option value="">Selecione a empresa</option>
          {companies.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
      </label>
      {typeof companyId === 'number' && (
        <>
          <label className="field-label">Nome da chave
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Ex: Integração BI" />
          </label>
          <button type="button" className="btn-primary" disabled={busy} onClick={create}>
            {busy ? 'Gerando…' : 'Gerar chave'}
          </button>
          {created && (
            <div className="alert-ok" style={{ marginTop: 10 }}>
              <div className="muted small">Copie agora (não será exibida de novo):</div>
              <code className="secret-code">{created}</code>
            </div>
          )}
          {keys.length === 0 ? (
            <p className="muted small">Nenhuma chave para esta empresa.</p>
          ) : (
            <ul className="list" style={{ marginTop: 12 }}>
              {keys.map((k) => (
                <li key={k.id}>
                  <div className="item-main">
                    <strong>{k.name}</strong>
                    <div className="muted small">
                      {k.maskedKey}
                      {k.active ? '' : ' · revogada'}
                      {k.lastUsedAt ? ` · uso ${new Date(k.lastUsedAt).toLocaleDateString()}` : ' · sem uso'}
                    </div>
                  </div>
                  {k.active && (
                    <button type="button" className="btn-ghost danger" onClick={() => revoke(k.id)}>Revogar</button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
