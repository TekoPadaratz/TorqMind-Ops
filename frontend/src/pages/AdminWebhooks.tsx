import React, { useEffect, useState } from 'react';
import { apiDelete, apiGet, apiPost } from '../api';

type Company = { id: number; name: string };
type WebhookView = {
  id: number;
  url: string;
  events: string | null;
  active: boolean;
  companyId: number;
  createdAt: string;
  lastStatus: string | null;
  lastAttemptAt: string | null;
  failureCount: number;
};
type Props = {
  companies: Company[];
  onOk: (msg: string) => void;
  onError: (e: unknown) => void;
};

export default function AdminWebhooks({ companies, onOk, onError }: Props) {
  const [companyId, setCompanyId] = useState<number | ''>('');
  const [url, setUrl] = useState('');
  const [hooks, setHooks] = useState<WebhookView[]>([]);
  const [secret, setSecret] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function load(id: number) {
    apiGet(`/admin/webhooks?companyId=${id}`).then(setHooks).catch(onError);
  }

  useEffect(() => {
    if (typeof companyId === 'number') load(companyId);
    else setHooks([]);
    setSecret(null);
  }, [companyId]);

  async function create() {
    if (typeof companyId !== 'number') return;
    setBusy(true);
    setSecret(null);
    try {
      const res = await apiPost('/admin/webhooks', { companyId, url: url.trim() });
      setSecret(res.secret);
      setUrl('');
      load(companyId);
      onOk('Webhook criado. Guarde o segredo — ele valida a assinatura das entregas.');
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: number) {
    if (!window.confirm('Excluir este webhook? As entregas para esta URL param.')) return;
    try {
      await apiDelete(`/admin/webhooks/${id}`);
      if (typeof companyId === 'number') load(companyId);
      onOk('Webhook excluído.');
    } catch (e) {
      onError(e);
    }
  }

  async function test(id: number) {
    try {
      const res = await apiPost(`/admin/webhooks/${id}/test`, {});
      if (res.ok) onOk(`Teste enviado (HTTP ${res.status}).`);
      else onError(new Error(res.message || `Falhou (HTTP ${res.status ?? '-'})`));
      if (typeof companyId === 'number') load(companyId);
    } catch (e) {
      onError(e);
    }
  }

  return (
    <section className="card">
      <h2>Webhooks de saída</h2>
      <p className="muted small">
        Receba um POST assinado quando algo acontece (tarefa/ocorrência). Apenas <code>https</code> para endereços públicos
        (endereços internos são bloqueados). A assinatura vem no header <code>X-TorqMind-Signature: sha256=HMAC</code> — valide
        com o segredo.
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
          <label className="field-label">URL (https)
            <input
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://seu-sistema.com/webhooks/torqmind"
              autoCapitalize="none"
              autoComplete="off"
            />
          </label>
          <button type="button" className="btn-primary" disabled={busy || !url.trim()} onClick={create}>
            {busy ? 'Criando…' : 'Criar webhook'}
          </button>
          {secret && (
            <div className="alert-ok" style={{ marginTop: 10 }}>
              <div className="muted small">Segredo (guarde agora, não será exibido de novo):</div>
              <code className="secret-code">{secret}</code>
            </div>
          )}
          {hooks.length === 0 ? (
            <p className="muted small">Nenhum webhook para esta empresa.</p>
          ) : (
            <ul className="list" style={{ marginTop: 12 }}>
              {hooks.map((h) => (
                <li key={h.id}>
                  <div className="item-main">
                    <strong style={{ wordBreak: 'break-all' }}>{h.url}</strong>
                    <div className="muted small">
                      {h.lastStatus ? `último: ${h.lastStatus}` : 'sem envios'}
                      {h.failureCount > 0 ? ` · ${h.failureCount} falha(s)` : ''}
                    </div>
                  </div>
                  <span className="actions">
                    <button type="button" className="btn-ghost" onClick={() => test(h.id)}>Testar</button>
                    <button type="button" className="btn-ghost danger" onClick={() => remove(h.id)}>Excluir</button>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
