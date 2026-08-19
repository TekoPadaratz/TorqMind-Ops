import React, { useEffect, useState } from 'react';
import { apiDelete, apiGet, apiPost } from '../api';
import { useI18n } from '../i18n';

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
  const { t } = useI18n();
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
      onOk(t('whook.created'));
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: number) {
    if (!window.confirm(t('whook.confirmDelete'))) return;
    try {
      await apiDelete(`/admin/webhooks/${id}`);
      if (typeof companyId === 'number') load(companyId);
      onOk(t('whook.deleted'));
    } catch (e) {
      onError(e);
    }
  }

  async function test(id: number) {
    try {
      const res = await apiPost(`/admin/webhooks/${id}/test`, {});
      if (res.ok) onOk(t('whook.testSent', { status: res.status }));
      else onError(new Error(res.message || t('whook.testFailed', { status: res.status ?? '-' })));
      if (typeof companyId === 'number') load(companyId);
    } catch (e) {
      onError(e);
    }
  }

  return (
    <section className="card">
      <h2>{t('whook.title')}</h2>
      <p className="muted small">
        {t('whook.descA')} <code>https</code> {t('whook.descB')} <code>X-TorqMind-Signature: sha256=HMAC</code> {t('whook.descC')}
      </p>
      <label className="field-label">{t('admin.company')}
        <select value={companyId} onChange={(e) => setCompanyId(e.target.value === '' ? '' : Number(e.target.value))}>
          <option value="">{t('akeys.selectCompany')}</option>
          {companies.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
      </label>
      {typeof companyId === 'number' && (
        <>
          <label className="field-label">{t('whook.urlLabel')}
            <input
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder={t('whook.urlPlaceholder')}
              autoCapitalize="none"
              autoComplete="off"
            />
          </label>
          <button type="button" className="btn-primary" disabled={busy || !url.trim()} onClick={create}>
            {busy ? t('whook.creating') : t('whook.create')}
          </button>
          {secret && (
            <div className="alert-ok" style={{ marginTop: 10 }}>
              <div className="muted small">{t('whook.secretLabel')}</div>
              <code className="secret-code">{secret}</code>
            </div>
          )}
          {hooks.length === 0 ? (
            <p className="muted small">{t('whook.none')}</p>
          ) : (
            <ul className="list" style={{ marginTop: 12 }}>
              {hooks.map((h) => (
                <li key={h.id}>
                  <div className="item-main">
                    <strong style={{ wordBreak: 'break-all' }}>{h.url}</strong>
                    <div className="muted small">
                      {h.lastStatus ? `${t('whook.lastPrefix')} ${h.lastStatus}` : t('whook.noSends')}
                      {h.failureCount > 0 ? ` · ${h.failureCount} ${t('whook.failures')}` : ''}
                    </div>
                  </div>
                  <span className="actions">
                    <button type="button" className="btn-ghost" onClick={() => test(h.id)}>{t('whook.test')}</button>
                    <button type="button" className="btn-ghost danger" onClick={() => remove(h.id)}>{t('whook.delete')}</button>
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
