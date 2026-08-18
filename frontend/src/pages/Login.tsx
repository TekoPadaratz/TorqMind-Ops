import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import PasswordField from '../components/PasswordField';

export default function Login() {
  const { login, loginTotp } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [challenge, setChallenge] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const step = await login(username, password);
      if (step.totpRequired) {
        setChallenge(step.challenge);
      } else {
        navigate('/');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha no login');
    } finally {
      setLoading(false);
    }
  }

  async function onSubmitCode(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await loginTotp(challenge as string, code.trim());
      navigate('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Código inválido');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="brand">
          <div className="brand-mark">TM</div>
          <h1>TorqMind Ops</h1>
          <p>Sua operação sob controle.</p>
        </div>
        {challenge ? (
          <form onSubmit={onSubmitCode}>
            <p className="muted small">Digite o código do seu app de autenticação.</p>
            <label>
              Código de verificação
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
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Verificando...' : 'Confirmar'}
            </button>
            <button
              type="button"
              className="btn-ghost"
              disabled={loading}
              onClick={() => { setChallenge(null); setCode(''); setError(null); }}
            >
              Voltar
            </button>
          </form>
        ) : (
          <form onSubmit={onSubmit}>
            <label>
              Usuário
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoCapitalize="none"
                autoComplete="username"
                placeholder="seu usuário"
                required
              />
            </label>
            <label>
              Senha
              <PasswordField
                value={password}
                onChange={setPassword}
                autoComplete="current-password"
                placeholder="sua senha"
                required
              />
            </label>
            {error && <div className="alert-error">{error}</div>}
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Entrando...' : 'Entrar'}
            </button>
          </form>
        )}
        <p className="muted small build-tag">versão {__BUILD_ID__}</p>
      </div>
    </div>
  );
}
