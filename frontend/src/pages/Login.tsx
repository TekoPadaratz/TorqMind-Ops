import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiPost } from '../api';
import { useAuth } from '../auth';
import { useI18n } from '../i18n';
import PasswordField from '../components/PasswordField';

export default function Login() {
  const { login, loginTotp } = useAuth();
  const navigate = useNavigate();
  const { t } = useI18n();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [challenge, setChallenge] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [showForgot, setShowForgot] = useState(false);
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotDone, setForgotDone] = useState(false);

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
      setError(err instanceof Error ? err.message : t('login.failed'));
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
      setError(err instanceof Error ? err.message : t('login.invalidCode'));
    } finally {
      setLoading(false);
    }
  }

  async function onForgot(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await apiPost('/auth/password/forgot', { email: forgotEmail.trim() });
    } catch {
      /* nao revela existencia da conta */
    } finally {
      setForgotDone(true);
      setLoading(false);
    }
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="brand">
          <div className="brand-mark">TM</div>
          <h1>TorqMind Ops</h1>
          <p>{t('brand.subtitle')}</p>
        </div>
        {challenge ? (
          <form onSubmit={onSubmitCode}>
            <p className="muted small">{t('login.code.hint')}</p>
            <label>
              {t('login.code.label')}
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
              {loading ? t('login.verifying') : t('login.confirm')}
            </button>
            <button
              type="button"
              className="btn-ghost"
              disabled={loading}
              onClick={() => { setChallenge(null); setCode(''); setError(null); }}
            >
              {t('login.back')}
            </button>
          </form>
        ) : showForgot ? (
          <form onSubmit={onForgot}>
            <p className="muted small">{t('login.forgot.hint')}</p>
            <label>
              {t('login.email')}
              <input
                value={forgotEmail}
                onChange={(e) => setForgotEmail(e.target.value)}
                type="email"
                autoComplete="email"
                placeholder={t('login.emailPlaceholder')}
                required
              />
            </label>
            {forgotDone && <div className="alert-ok">{t('login.forgot.sent')}</div>}
            {error && <div className="alert-error">{error}</div>}
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? t('login.sending') : t('login.send')}
            </button>
            <button
              type="button"
              className="btn-ghost"
              disabled={loading}
              onClick={() => { setShowForgot(false); setError(null); setForgotDone(false); }}
            >
              {t('login.back')}
            </button>
          </form>
        ) : (
          <form onSubmit={onSubmit}>
            <label>
              {t('login.user')}
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoCapitalize="none"
                autoComplete="username"
                placeholder={t('login.user.placeholder')}
                required
              />
            </label>
            <label>
              {t('login.password')}
              <PasswordField
                value={password}
                onChange={setPassword}
                autoComplete="current-password"
                placeholder={t('login.password.placeholder')}
                required
              />
            </label>
            {error && <div className="alert-error">{error}</div>}
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? t('login.entering') : t('login.enter')}
            </button>
            <button
              type="button"
              className="btn-ghost"
              disabled={loading}
              onClick={() => { setShowForgot(true); setError(null); }}
            >
              {t('login.forgot')}
            </button>
          </form>
        )}
        <p className="muted small build-tag">{t('login.version')} {__BUILD_ID__}</p>
      </div>
    </div>
  );
}
