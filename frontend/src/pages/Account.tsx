import React, { useState } from 'react';
import { apiPost } from '../api';
import { useAuth } from '../auth';
import PasswordField from '../components/PasswordField';
import { passwordConfirmError } from '../password';

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
    </div>
  );
}
