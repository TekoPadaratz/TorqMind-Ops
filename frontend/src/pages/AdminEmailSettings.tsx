import React, { useEffect, useState } from 'react';
import { apiGet, apiPost, apiPut } from '../api';

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
      onOk('Configuração de e-mail salva.');
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
      onOk('E-mail de teste enviado.');
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  if (!s) return null;

  return (
    <section className="card">
      <h2>E-mail (recuperação de senha e avisos)</h2>
      <p className="muted small">
        Configure o SMTP que enviará os e-mails. A senha é guardada cifrada e nunca é exibida. Só o administrador altera.
      </p>
      <div className="stack">
        <label className="check">
          <input type="checkbox" checked={s.enabled} onChange={(e) => setS({ ...s, enabled: e.target.checked })} />
          Habilitar envio de e-mails
        </label>
        <label className="field-label">Servidor SMTP (host)
          <input
            value={s.host ?? ''}
            onChange={(e) => setS({ ...s, host: e.target.value })}
            placeholder="ex: smtp.seuprovedor.com"
            autoCapitalize="none"
            autoComplete="off"
          />
        </label>
        <div className="time-row">
          <div className="field-block">
            <label className="field-label">Porta
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
        <label className="field-label">Usuário SMTP
          <input
            value={s.username ?? ''}
            onChange={(e) => setS({ ...s, username: e.target.value })}
            autoCapitalize="none"
            autoComplete="off"
          />
        </label>
        <label className="field-label">
          Senha SMTP {s.passwordSet && <span className="muted small">(já definida — deixe em branco para manter)</span>}
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder={s.passwordSet ? '•••••• (mantém a atual)' : ''}
            autoComplete="new-password"
          />
        </label>
        <label className="field-label">E-mail remetente (de)
          <input
            type="email"
            value={s.fromEmail ?? ''}
            onChange={(e) => setS({ ...s, fromEmail: e.target.value })}
            placeholder="nao-responder@seu-dominio.com"
            autoCapitalize="none"
            autoComplete="off"
          />
        </label>
        <label className="field-label">Nome do remetente
          <input value={s.fromName} onChange={(e) => setS({ ...s, fromName: e.target.value })} />
        </label>
        <button className="btn-primary" type="button" onClick={save} disabled={busy}>
          {busy ? 'Salvando...' : 'Salvar e-mail'}
        </button>
        <div className="row-between">
          <input
            value={testTo}
            onChange={(e) => setTestTo(e.target.value)}
            type="email"
            placeholder="enviar teste para..."
            autoCapitalize="none"
            autoComplete="off"
          />
          <button className="btn-ghost" type="button" onClick={sendTest} disabled={busy || !testTo.trim()}>
            Enviar teste
          </button>
        </div>
      </div>
    </section>
  );
}
