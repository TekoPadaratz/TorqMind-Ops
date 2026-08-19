import React, { useEffect, useState } from 'react';
import { apiDelete, apiGet, apiPost } from '../api';
import { useI18n } from '../i18n';

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
  const { t } = useI18n();
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
      const res = await apiPost('/admin/api-keys', { companyId, name: name.trim() || t('akeys.defaultName') });
      setCreated(res.key);
      setName('');
      load(companyId);
      onOk(t('akeys.created'));
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  async function revoke(id: number) {
    if (!window.confirm(t('akeys.confirmRevoke'))) return;
    try {
      await apiDelete(`/admin/api-keys/${id}`);
      if (typeof companyId === 'number') load(companyId);
      onOk(t('akeys.revoked'));
    } catch (e) {
      onError(e);
    }
  }

  return (
    <section className="card">
      <h2>{t('akeys.title')}</h2>
      <p className="muted small">
        {t('akeys.descA')} <code>X-API-Key</code> {t('akeys.descB')}{' '}
        <code>/api/public/v1/…</code>{t('akeys.descC')}
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
          <label className="field-label">{t('akeys.keyName')}
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t('akeys.keyNamePlaceholder')} />
          </label>
          <button type="button" className="btn-primary" disabled={busy} onClick={create}>
            {busy ? t('akeys.generating') : t('akeys.generate')}
          </button>
          {created && (
            <div className="alert-ok" style={{ marginTop: 10 }}>
              <div className="muted small">{t('akeys.copyNow')}</div>
              <code className="secret-code">{created}</code>
            </div>
          )}
          {keys.length === 0 ? (
            <p className="muted small">{t('akeys.none')}</p>
          ) : (
            <ul className="list" style={{ marginTop: 12 }}>
              {keys.map((k) => (
                <li key={k.id}>
                  <div className="item-main">
                    <strong>{k.name}</strong>
                    <div className="muted small">
                      {k.maskedKey}
                      {k.active ? '' : ` · ${t('akeys.revokedTag')}`}
                      {k.lastUsedAt ? ` · ${t('akeys.usedPrefix')} ${new Date(k.lastUsedAt).toLocaleDateString()}` : ` · ${t('akeys.neverUsed')}`}
                    </div>
                  </div>
                  {k.active && (
                    <button type="button" className="btn-ghost danger" onClick={() => revoke(k.id)}>{t('akeys.revoke')}</button>
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
