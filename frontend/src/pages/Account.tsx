import React, { useState, useEffect } from 'react';
import { apiGet, apiPost } from '../api';
import { useAuth } from '../auth';
import PasswordField from '../components/PasswordField';
import { passwordConfirmError } from '../password';
import { useI18n } from '../i18n';

function LanguageCard() {
  const { lang, setLang, t } = useI18n();
  return (
    <section className="card">
      <h2>{t('account.language')}</h2>
      <p className="muted small">{t('account.language.hint')}</p>
      <select value={lang} onChange={(e) => setLang(e.target.value === 'en' ? 'en' : 'pt')}>
        <option value="pt">Português (Brasil)</option>
        <option value="en">English</option>
      </select>
    </section>
  );
}

function TwoFactorCard() {
  const { t } = useI18n();
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    apiGet('/auth/2fa').then((r) => setEnabled(!!r.enabled)).catch(() => setEnabled(false));
  }, []);

  async function startSetup() {
    setError(null);
    setOk(null);
    setBusy(true);
    try {
      setSetup(await apiPost('/auth/2fa/setup', {}));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('account.2fa.errStart'));
    } finally {
      setBusy(false);
    }
  }

  async function confirmEnable(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setOk(null);
    setBusy(true);
    try {
      await apiPost('/auth/2fa/enable', { code: code.trim() });
      setEnabled(true);
      setSetup(null);
      setCode('');
      setOk(t('account.2fa.enabled'));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('account.2fa.invalidCode'));
    } finally {
      setBusy(false);
    }
  }

  async function disable(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setOk(null);
    setBusy(true);
    try {
      await apiPost('/auth/2fa/disable', { code: code.trim() });
      setEnabled(false);
      setCode('');
      setOk(t('account.2fa.disabledMsg'));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('account.2fa.invalidCode'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <h2>{t('account.2fa.title')}</h2>
      <p className="muted small">
        {t('account.2fa.desc')}
      </p>
      {enabled === null && <p className="muted">{t('common.loading')}</p>}

      {enabled === true && (
        <form className="stack" onSubmit={disable}>
          <div className="alert-ok">{t('account.2fa.activeHere')}</div>
          <label className="field-label">{t('account.2fa.toDisable')}
            <input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="000000"
              maxLength={6}
              required
            />
          </label>
          {error && <div className="alert-error">{error}</div>}
          {ok && <div className="alert-ok">{ok}</div>}
          <button className="btn-ghost" type="submit" disabled={busy}>
            {busy ? t('account.processing') : t('account.2fa.disable')}
          </button>
        </form>
      )}

      {enabled === false && !setup && (
        <>
          {ok && <div className="alert-ok">{ok}</div>}
          {error && <div className="alert-error">{error}</div>}
          <button className="btn-primary" type="button" disabled={busy} onClick={startSetup}>
            {busy ? t('account.2fa.generating') : t('account.2fa.enable')}
          </button>
        </>
      )}

      {enabled === false && setup && (
        <form className="stack" onSubmit={confirmEnable}>
          <p className="muted small">{t('account.2fa.step1')}</p>
          <code className="secret-code">
            {setup.secret}
          </code>
          <p className="muted small" style={{ wordBreak: 'break-all' }}>{t('account.2fa.orLink')} {setup.otpauthUri}</p>
          <label className="field-label">{t('account.2fa.step2')}
            <input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="000000"
              maxLength={6}
              autoFocus
              required
            />
          </label>
          {error && <div className="alert-error">{error}</div>}
          <button className="btn-primary" type="submit" disabled={busy}>
            {busy ? t('account.confirming') : t('account.2fa.confirmEnable')}
          </button>
        </form>
      )}
    </section>
  );
}

function urlBase64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const buffer = new ArrayBuffer(raw.length);
  const out = new Uint8Array(buffer);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

function PushCard() {
  const { t } = useI18n();
  const supported =
    typeof navigator !== 'undefined' &&
    'serviceWorker' in navigator &&
    typeof window !== 'undefined' &&
    'PushManager' in window &&
    'Notification' in window;
  const [enabled, setEnabled] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);

  useEffect(() => {
    if (!supported) return;
    navigator.serviceWorker
      .getRegistration()
      .then((reg) => (reg ? reg.pushManager.getSubscription() : null))
      .then((sub) => setEnabled(!!sub))
      .catch(() => undefined);
  }, [supported]);

  async function enable() {
    setError(null);
    setOk(null);
    setBusy(true);
    try {
      const reg = await navigator.serviceWorker.register('/sw-push.js');
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        setError(t('account.push.denied'));
        return;
      }
      const { publicKey } = await apiGet('/push/public-key');
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey)
      });
      const json: any = sub.toJSON();
      await apiPost('/push/subscribe', {
        endpoint: sub.endpoint,
        keys: { p256dh: json.keys?.p256dh, auth: json.keys?.auth }
      });
      setEnabled(true);
      setOk(t('account.push.enabledMsg'));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('account.push.errEnable'));
    } finally {
      setBusy(false);
    }
  }

  async function disable() {
    setError(null);
    setOk(null);
    setBusy(true);
    try {
      const reg = await navigator.serviceWorker.getRegistration();
      const sub = reg ? await reg.pushManager.getSubscription() : null;
      if (sub) {
        await apiPost('/push/unsubscribe', { endpoint: sub.endpoint });
        await sub.unsubscribe();
      }
      setEnabled(false);
      setOk(t('account.push.disabledMsg'));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('account.push.errDisable'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <h2>{t('account.push.title')}</h2>
      <p className="muted small">
        {t('account.push.desc')}
      </p>
      {!supported ? (
        <p className="muted small">{t('account.push.unsupported')}</p>
      ) : (
        <>
          {error && <div className="alert-error">{error}</div>}
          {ok && <div className="alert-ok">{ok}</div>}
          {enabled ? (
            <button className="btn-ghost" type="button" disabled={busy} onClick={disable}>
              {busy ? t('account.processing') : t('account.push.disableBtn')}
            </button>
          ) : (
            <button className="btn-primary" type="button" disabled={busy} onClick={enable}>
              {busy ? t('account.push.enabling') : t('account.push.enableBtn')}
            </button>
          )}
        </>
      )}
    </section>
  );
}

export default function Account() {
  const { session, replaceSession } = useAuth();
  const { t } = useI18n();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setOk(null);
    const mismatch = passwordConfirmError(newPassword, confirmPassword);
    if (mismatch) {
      setError(mismatch);
      return;
    }
    if (currentPassword === newPassword) {
      setError(t('account.pwd.mustDiffer'));
      return;
    }
    setLoading(true);
    try {
      const next = await apiPost('/auth/password', { currentPassword, newPassword });
      replaceSession(next);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setOk(t('account.pwd.updated'));
    } catch (err) {
      setError(err instanceof Error ? err.message : t('account.pwd.errChange'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page">
      <section className="card">
        <h2>{t('account.pwd.title')}</h2>
        <p className="muted small">
          {session?.fullName} · @{session?.username}
        </p>
        <form className="stack" onSubmit={onSubmit}>
          <label className="field-label">{t('account.pwd.current')}
            <PasswordField
              value={currentPassword}
              onChange={setCurrentPassword}
              autoComplete="current-password"
              required
            />
          </label>
          <label className="field-label">{t('account.pwd.new')}
            <PasswordField
              value={newPassword}
              onChange={setNewPassword}
              autoComplete="new-password"
              placeholder={t('account.pwd.hint')}
              required
            />
          </label>
          <label className="field-label">{t('account.pwd.confirm')}
            <PasswordField
              value={confirmPassword}
              onChange={setConfirmPassword}
              autoComplete="new-password"
              required
            />
          </label>
          {error && <div className="alert-error">{error}</div>}
          {ok && <div className="alert-ok">{ok}</div>}
          <button className="btn-primary" type="submit" disabled={loading}>
            {loading ? t('account.saving') : t('account.pwd.change')}
          </button>
        </form>
      </section>
      <LanguageCard />
      <TwoFactorCard />
      <PushCard />
    </div>
  );
}
