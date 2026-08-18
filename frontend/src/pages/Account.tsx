import React, { useState, useEffect } from 'react';
import { apiGet, apiPost } from '../api';
import { useAuth } from '../auth';
import PasswordField from '../components/PasswordField';
import { passwordConfirmError } from '../password';

function TwoFactorCard() {
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
      setError(e instanceof Error ? e.message : 'Falha ao iniciar.');
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
      setOk('Verificação em duas etapas ativada.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Código inválido.');
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
      setOk('Verificação em duas etapas desativada.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Código inválido.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <h2>Verificação em duas etapas</h2>
      <p className="muted small">
        Proteja o acesso com um código do seu app de autenticação (Google Authenticator, Authy, etc.).
      </p>
      {enabled === null && <p className="muted">Carregando...</p>}

      {enabled === true && (
        <form className="stack" onSubmit={disable}>
          <div className="alert-ok">Ativa nesta conta.</div>
          <label className="field-label">Para desativar, informe um código atual
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
            {busy ? 'Processando...' : 'Desativar'}
          </button>
        </form>
      )}

      {enabled === false && !setup && (
        <>
          {ok && <div className="alert-ok">{ok}</div>}
          {error && <div className="alert-error">{error}</div>}
          <button className="btn-primary" type="button" disabled={busy} onClick={startSetup}>
            {busy ? 'Gerando...' : 'Ativar'}
          </button>
        </>
      )}

      {enabled === false && setup && (
        <form className="stack" onSubmit={confirmEnable}>
          <p className="muted small">1. Adicione esta chave no seu app de autenticação:</p>
          <code className="secret-code">
            {setup.secret}
          </code>
          <p className="muted small" style={{ wordBreak: 'break-all' }}>Ou use o link: {setup.otpauthUri}</p>
          <label className="field-label">2. Digite o código gerado
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
            {busy ? 'Confirmando...' : 'Confirmar e ativar'}
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
        setError('Permissão de notificações não concedida.');
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
      setOk('Notificações ativadas neste dispositivo.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao ativar notificações.');
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
      setOk('Notificações desativadas neste dispositivo.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao desativar.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <h2>Notificações no dispositivo</h2>
      <p className="muted small">
        Receba avisos de tarefas atrasadas e reatribuições mesmo com o app fechado. No iPhone, primeiro adicione o app à tela de início.
      </p>
      {!supported ? (
        <p className="muted small">Este dispositivo/navegador não suporta notificações push.</p>
      ) : (
        <>
          {error && <div className="alert-error">{error}</div>}
          {ok && <div className="alert-ok">{ok}</div>}
          {enabled ? (
            <button className="btn-ghost" type="button" disabled={busy} onClick={disable}>
              {busy ? 'Processando…' : 'Desativar notificações'}
            </button>
          ) : (
            <button className="btn-primary" type="button" disabled={busy} onClick={enable}>
              {busy ? 'Ativando…' : 'Ativar notificações'}
            </button>
          )}
        </>
      )}
    </section>
  );
}

export default function Account() {
  const { session, replaceSession } = useAuth();
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
      setError('A nova senha deve ser diferente da atual.');
      return;
    }
    setLoading(true);
    try {
      const next = await apiPost('/auth/password', { currentPassword, newPassword });
      replaceSession(next);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setOk('Senha atualizada. Sua sessão continua ativa.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível trocar a senha.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page">
      <section className="card">
        <h2>Minha senha</h2>
        <p className="muted small">
          {session?.fullName} · @{session?.username}
        </p>
        <form className="stack" onSubmit={onSubmit}>
          <label className="field-label">Senha atual
            <PasswordField
              value={currentPassword}
              onChange={setCurrentPassword}
              autoComplete="current-password"
              required
            />
          </label>
          <label className="field-label">Nova senha
            <PasswordField
              value={newPassword}
              onChange={setNewPassword}
              autoComplete="new-password"
              placeholder="mín. 8, letras e números"
              required
            />
          </label>
          <label className="field-label">Confirmar nova senha
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
            {loading ? 'Salvando...' : 'Trocar senha'}
          </button>
        </form>
      </section>
      <TwoFactorCard />
      <PushCard />
    </div>
  );
}
