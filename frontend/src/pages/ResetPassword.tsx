import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { apiPost } from '../api';
import PasswordField from '../components/PasswordField';
import { passwordConfirmError } from '../password';

export default function ResetPassword() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (password.length < 8) {
      setError('A senha deve ter ao menos 8 caracteres.');
      return;
    }
    const mismatch = passwordConfirmError(password, confirm);
    if (mismatch) {
      setError(mismatch);
      return;
    }
    setLoading(true);
    try {
      await apiPost('/auth/password/reset', { token, newPassword: password });
      setOk(true);
      setTimeout(() => navigate('/login'), 2500);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível redefinir a senha.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="brand">
          <div className="brand-mark">TM</div>
          <h1>Redefinir senha</h1>
        </div>
        {!token ? (
          <div className="alert-error">Link inválido ou incompleto.</div>
        ) : ok ? (
          <div className="alert-ok">Senha redefinida! Redirecionando para o login...</div>
        ) : (
          <form onSubmit={onSubmit}>
            <label>
              Nova senha
              <PasswordField
                value={password}
                onChange={setPassword}
                autoComplete="new-password"
                placeholder="mín. 8, letras e números"
                required
              />
            </label>
            <label>
              Confirmar senha
              <PasswordField value={confirm} onChange={setConfirm} autoComplete="new-password" required />
            </label>
            {error && <div className="alert-error">{error}</div>}
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Salvando...' : 'Redefinir senha'}
            </button>
          </form>
        )}
        <p className="muted small">
          <a href="/login">Voltar ao login</a>
        </p>
      </div>
    </div>
  );
}
