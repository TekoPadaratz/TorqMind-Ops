import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(username, password);
      navigate('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha no login');
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
            <div className="password-field">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                placeholder="sua senha"
                required
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                aria-pressed={showPassword}
                title={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
              >
                {showPassword ? (
                  <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                    <path
                      fill="currentColor"
                      d="M12 6c-5 0-9.3 3.1-11 7.5C2.7 17.9 7 21 12 21s9.3-3.1 11-7.5C21.3 9.1 17 6 12 6zm0 12.5c-3.6 0-6.8-2.2-8.3-5.5C5.2 9.7 8.4 7.5 12 7.5s6.8 2.2 8.3 5.5c-1.5 3.3-4.7 5.5-8.3 5.5zM12 9a4 4 0 1 0 .001 8.001A4 4 0 0 0 12 9zm0 6.5a2.5 2.5 0 1 1 0-5 2.5 2.5 0 0 1 0 5z"
                    />
                    <path
                      fill="currentColor"
                      d="M3.3 4.7 4.7 3.3l16 16-1.4 1.4z"
                    />
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                    <path
                      fill="currentColor"
                      d="M12 6c-5 0-9.3 3.1-11 7.5C2.7 17.9 7 21 12 21s9.3-3.1 11-7.5C21.3 9.1 17 6 12 6zm0 12.5c-3.6 0-6.8-2.2-8.3-5.5C5.2 9.7 8.4 7.5 12 7.5s6.8 2.2 8.3 5.5c-1.5 3.3-4.7 5.5-8.3 5.5zM12 9a4 4 0 1 0 .001 8.001A4 4 0 0 0 12 9zm0 6.5a2.5 2.5 0 1 1 0-5 2.5 2.5 0 0 1 0 5z"
                    />
                  </svg>
                )}
              </button>
            </div>
          </label>
          {error && <div className="alert-error">{error}</div>}
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? 'Entrando...' : 'Entrar'}
          </button>
        </form>
        <p className="muted small build-tag">versão {__BUILD_ID__}</p>
      </div>
    </div>
  );
}
