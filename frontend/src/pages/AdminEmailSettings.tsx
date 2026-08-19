import React, { useEffect, useState } from 'react';
import { apiGet, apiPost, apiPut } from '../api';
import { useI18n } from '../i18n';

type EmailSettings = {
  enabled: boolean;
  host: string | null;
  port: number;
  username: string | null;
  passwordSet: boolean;
  useTls: boolean;
  useSsl: boolean;
  fromEmail: string | null;
  fromName: string;
};

export default function AdminEmailSettings({
  onOk,
  onError
}: {
  onOk: (msg: string) => void;
  onError: (e: unknown) => void;
}) {
  const { t } = useI18n();
  const [s, setS] = useState<EmailSettings | null>(null);
  const [password, setPassword] = useState('');
  const [testTo, setTestTo] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    apiGet('/admin/email-settings').then(setS).catch(onError);
  }, []);

  async function save() {
    if (!s) return;
    setBusy(true);
    try {
      const next = await apiPut('/admin/email-settings', {
        enabled: s.enabled,
        host: s.host,
        port: s.port,
        username: s.username,
        password: password ? password : null,
        useTls: s.useTls,
        useSsl: s.useSsl,
        fromEmail: s.fromEmail,
        fromName: s.fromName
      });
      setS(next);
      setPassword('');
      onOk(t('aemail.saved'));
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  async function sendTest() {
    setBusy(true);
    try {
      await apiPost('/admin/email-settings/test', { to: testTo.trim() });
      onOk(t('aemail.testSent'));
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  if (!s) return null;

  return (
    <section className="card">
      <h2>{t('aemail.title')}</h2>
      <p className="muted small">
        {t('aemail.desc')}
      </p>
      <div className="stack">
        <label className="check">
          <input type="checkbox" checked={s.enabled} onChange={(e) => setS({ ...s, enabled: e.target.checked })} />
          {t('aemail.enable')}
        </label>
        <label className="field-label">{t('aemail.host')}
          <input
            value={s.host ?? ''}
            onChange={(e) => setS({ ...s, host: e.target.value })}
            placeholder={t('aemail.hostPlaceholder')}
            autoCapitalize="none"
            autoComplete="off"
          />
        </label>
        <div className="time-row">
          <div className="field-block">
            <label className="field-label">{t('aemail.port')}
              <input type="number" value={s.port} onChange={(e) => setS({ ...s, port: Number(e.target.value) })} />
            </label>
          </div>
          <div className="field-block">
            <label className="check">
              <input type="checkbox" checked={s.useTls} onChange={(e) => setS({ ...s, useTls: e.target.checked })} /> STARTTLS
            </label>
            <label className="check">
              <input type="checkbox" checked={s.useSsl} onChange={(e) => setS({ ...s, useSsl: e.target.checked })} /> SSL
            </label>
          </div>
        </div>
        <label className="field-label">{t('aemail.username')}
          <input
            value={s.username ?? ''}
            onChange={(e) => setS({ ...s, username: e.target.value })}
            autoCapitalize="none"
            autoComplete="off"
          />
        </label>
        <label className="field-label">
          {t('aemail.password')} {s.passwordSet && <span className="muted small">{t('aemail.passwordSet')}</span>}
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder={s.passwordSet ? t('aemail.passwordPlaceholder') : ''}
            autoComplete="new-password"
          />
        </label>
        <label className="field-label">{t('aemail.fromEmail')}
          <input
            type="email"
            value={s.fromEmail ?? ''}
            onChange={(e) => setS({ ...s, fromEmail: e.target.value })}
            placeholder={t('aemail.fromEmailPlaceholder')}
            autoCapitalize="none"
            autoComplete="off"
          />
        </label>
        <label className="field-label">{t('aemail.fromName')}
          <input value={s.fromName} onChange={(e) => setS({ ...s, fromName: e.target.value })} />
        </label>
        <button className="btn-primary" type="button" onClick={save} disabled={busy}>
          {busy ? t('account.saving') : t('aemail.save')}
        </button>
        <div className="row-between">
          <input
            value={testTo}
            onChange={(e) => setTestTo(e.target.value)}
            type="email"
            placeholder={t('aemail.testPlaceholder')}
            autoCapitalize="none"
            autoComplete="off"
          />
          <button className="btn-ghost" type="button" onClick={sendTest} disabled={busy || !testTo.trim()}>
            {t('aemail.sendTest')}
          </button>
        </div>
      </div>
    </section>
  );
}
