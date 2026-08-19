import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { apiPost } from '../api';
import PasswordField from '../components/PasswordField';
import { passwordConfirmError } from '../password';
import { useI18n } from '../i18n';

export default function ResetPassword() {
  const navigate = useNavigate();
  const { t } = useI18n();
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
      setError(t('reset.minLen'));
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
      setError(err instanceof Error ? err.message : t('reset.err'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="brand">
          <div className="brand-mark">TM</div>
          <h1>{t('reset.title')}</h1>
        </div>
        {!token ? (
          <div className="alert-error">{t('reset.invalidLink')}</div>
        ) : ok ? (
          <div className="alert-ok">{t('reset.done')}</div>
        ) : (
          <form onSubmit={onSubmit}>
            <label>
              {t('account.pwd.new')}
              <PasswordField
                value={password}
                onChange={setPassword}
                autoComplete="new-password"
                placeholder={t('account.pwd.hint')}
                required
              />
            </label>
            <label>
              {t('reset.confirm')}
              <PasswordField value={confirm} onChange={setConfirm} autoComplete="new-password" required />
            </label>
            {error && <div className="alert-error">{error}</div>}
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? t('account.saving') : t('reset.submit')}
            </button>
          </form>
        )}
        <p className="muted small">
          <a href="/login">{t('reset.backToLogin')}</a>
        </p>
      </div>
    </div>
  );
}
