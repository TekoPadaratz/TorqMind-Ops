import React, { useEffect, useState } from 'react';
import { apiGet, apiPost, apiPut } from '../api';
import PasswordField from '../components/PasswordField';
import { passwordConfirmError } from '../password';
import { ROLE_OPTIONS_ADMIN, roleLabel } from '../roles';
import { useI18n } from '../i18n';

export type CatalogOption = { id: number; name: string };

export type AdminUser = {
  id: string;
  username: string;
  fullName: string;
  email?: string | null;
  role: string;
  roleLabel?: string;
  companyId?: number | null;
  branchId?: number | null;
  sectorId?: number | null;
  active: boolean;
  locked?: boolean;
  lockedUntil?: string | null;
  passwordChangedAt?: string | null;
};

type PasswordEvent = {
  id: number;
  action: string;
  actionLabel: string;
  actorName?: string | null;
  actorUsername?: string | null;
  createdAt: string;
};

function formatWhen(iso?: string | null) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' });
}

export default function UsersAdmin({
  companies,
  branches,
  sectors,
  selectedCompany,
  onCompanyChange,
  users,
  onReload,
  onOk,
  onError
}: {
  companies: CatalogOption[];
  branches: CatalogOption[];
  sectors: CatalogOption[];
  selectedCompany: number | '';
  onCompanyChange: (id: number) => void;
  users: AdminUser[];
  onReload: () => Promise<void>;
  onOk: (msg: string) => void;
  onError: (e: unknown) => void;
}) {
  const { t } = useI18n();
  return (
    <>
      <CreateUserForm
        companies={companies}
        branches={branches}
        sectors={sectors}
        selectedCompany={selectedCompany}
        onCompanyChange={onCompanyChange}
        onCreated={async () => {
          await onReload();
          onOk(t('ausers.created'));
        }}
        onError={onError}
      />
      <section className="card">
        <h2>{t('ausers.title')}</h2>
        {users.length === 0 ? (
          <p className="muted">{t('ausers.none')}</p>
        ) : (
          <ul className="list">
            {users.map((user) => (
              <UserRow
                key={user.id}
                user={user}
                companies={companies}
                onReload={onReload}
                onOk={onOk}
                onError={onError}
              />
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

function CreateUserForm({
  companies,
  branches,
  sectors,
  selectedCompany,
  onCompanyChange,
  onCreated,
  onError
}: {
  companies: CatalogOption[];
  branches: CatalogOption[];
  sectors: CatalogOption[];
  selectedCompany: number | '';
  onCompanyChange: (id: number) => void;
  onCreated: () => void;
  onError: (e: unknown) => void;
}) {
  const [username, setUsername] = useState('');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('MANAGER');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [branchId, setBranchId] = useState<number | ''>('');
  const [sectorId, setSectorId] = useState<number | ''>('');

  const needsCompany = role !== 'MASTER';
  const needsBranch = role === 'MANAGER' || role === 'OPERATOR';
  const { t } = useI18n();

  return (
    <section className="card">
      <h2>{t('ausers.register')}</h2>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault();
          const confirmError = passwordConfirmError(password, confirm);
          if (confirmError) {
            onError(new Error(confirmError));
            return;
          }
          try {
            await apiPost('/admin/users', {
              username,
              fullName,
              email: email.trim() || null,
              role,
              password,
              companyId: needsCompany && typeof selectedCompany === 'number' ? selectedCompany : null,
              branchId: needsBranch && branchId !== '' ? branchId : null,
              sectorId: sectorId === '' ? null : sectorId
            });
            setUsername('');
            setFullName('');
            setEmail('');
            setPassword('');
            setConfirm('');
            onCreated();
          } catch (err) {
            onError(err);
          }
        }}
      >
        <input value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder={t('ausers.fullName')} required />
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder={t('ausers.username')}
          autoCapitalize="none"
          autoComplete="off"
          required
        />
        <input
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          type="email"
          placeholder={t('ausers.email')}
          autoCapitalize="none"
          autoComplete="off"
        />
        <label className="field-label">{t('ausers.role')}</label>
        <select value={role} onChange={(e) => setRole(e.target.value)}>
          {ROLE_OPTIONS_ADMIN.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </select>
        <label className="field-label">{t('ausers.initialPassword')}
          <PasswordField
            value={password}
            onChange={setPassword}
            autoComplete="new-password"
            placeholder={t('account.pwd.hint')}
            required
          />
        </label>
        <label className="field-label">{t('ausers.confirmPassword')}
          <PasswordField value={confirm} onChange={setConfirm} autoComplete="new-password" required />
        </label>
        {needsCompany && (
          <>
            <label className="field-label">{t('admin.company')}</label>
            <select
              value={selectedCompany}
              onChange={(e) => onCompanyChange(Number(e.target.value))}
              required
            >
              {companies.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </>
        )}
        {needsBranch && (
          <>
            <label className="field-label">{t('admin.branchLabel')}</label>
            <select
              value={branchId}
              onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
              required
            >
              <option value="">{t('ausers.selectBranch')}</option>
              {branches.map((b) => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
          </>
        )}
        <label className="field-label">{t('ausers.sectorOptional')}</label>
        <select value={sectorId} onChange={(e) => setSectorId(e.target.value === '' ? '' : Number(e.target.value))}>
          <option value="">{t('ausers.noSector')}</option>
          {sectors.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
        <button className="btn-primary" type="submit">{t('ausers.createUser')}</button>
      </form>
    </section>
  );
}

function UserRow({
  user,
  companies,
  onReload,
  onOk,
  onError
}: {
  user: AdminUser;
  companies: CatalogOption[];
  onReload: () => Promise<void>;
  onOk: (msg: string) => void;
  onError: (e: unknown) => void;
}) {
  const [open, setOpen] = useState(false);
  const [fullName, setFullName] = useState(user.fullName);
  const [email, setEmail] = useState(user.email ?? '');
  const [role, setRole] = useState(user.role);
  const [companyId, setCompanyId] = useState<number | ''>(user.companyId ?? '');
  const [branchId, setBranchId] = useState<number | ''>(user.branchId ?? '');
  const [sectorId, setSectorId] = useState<number | ''>(user.sectorId ?? '');
  const [active, setActive] = useState(user.active);
  const [scopedBranches, setScopedBranches] = useState<CatalogOption[]>([]);
  const [scopedSectors, setScopedSectors] = useState<CatalogOption[]>([]);
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [events, setEvents] = useState<PasswordEvent[] | null>(null);

  const needsCompany = role !== 'MASTER';
  const needsBranch = role === 'MANAGER' || role === 'OPERATOR';
  const { t } = useI18n();

  useEffect(() => {
    setFullName(user.fullName);
    setEmail(user.email ?? '');
    setRole(user.role);
    setCompanyId(user.companyId ?? '');
    setBranchId(user.branchId ?? '');
    setSectorId(user.sectorId ?? '');
    setActive(user.active);
  }, [user]);

  useEffect(() => {
    if (!open || typeof companyId !== 'number') {
      if (role === 'MASTER') {
        setScopedBranches([]);
        setScopedSectors([]);
      }
      return;
    }
    let activeLoad = true;
    Promise.all([
      apiGet(`/catalog/branches?companyId=${companyId}`),
      apiGet(`/catalog/sectors?companyId=${companyId}`)
    ])
      .then(([b, s]) => {
        if (!activeLoad) return;
        setScopedBranches(b);
        setScopedSectors(s);
      })
      .catch(onError);
    return () => {
      activeLoad = false;
    };
    // onError muda a cada render do pai; recarregar só quando o recorte muda.
  }, [open, companyId, role]);

  async function loadEvents() {
    setEvents(await apiGet(`/admin/users/${user.id}/password-events`));
  }

  return (
    <li className="user-admin-item">
      <button type="button" className="user-admin-head" onClick={() => setOpen((v) => !v)}>
        <div>
          <strong>{user.fullName}</strong>
          <div className="muted small">@{user.username}</div>
        </div>
        <div className="chips">
          <span className="chip">{user.roleLabel || roleLabel(user.role)}</span>
          {!user.active && <span className="chip">{t('ausers.inactive')}</span>}
          {user.locked && <span className="chip">{t('ausers.locked')}</span>}
        </div>
      </button>
      {open && (
        <div className="user-admin-body stack">
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault();
              try {
                await apiPut(`/admin/users/${user.id}`, {
                  fullName,
                  email: email.trim(),
                  role,
                  companyId: needsCompany && typeof companyId === 'number' ? companyId : null,
                  branchId: (needsBranch || role === 'OWNER') && branchId !== '' ? branchId : null,
                  sectorId: sectorId === '' ? null : sectorId,
                  active
                });
                await onReload();
                onOk(t('ausers.updated'));
              } catch (err) {
                onError(err);
              }
            }}
          >
            <label className="field-label">{t('ausers.loginNoChange')}
              <input value={user.username} readOnly />
            </label>
            <label className="field-label">{t('ausers.emailLabel')}
              <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder={t('ausers.emailEditPlaceholder')} autoCapitalize="none" />
            </label>
            <label className="field-label">{t('ausers.fullName')}
              <input value={fullName} onChange={(e) => setFullName(e.target.value)} required />
            </label>
            <label className="field-label">{t('ausers.role')}
              <select value={role} onChange={(e) => setRole(e.target.value)}>
                {ROLE_OPTIONS_ADMIN.map((r) => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </label>
            {needsCompany && (
              <label className="field-label">{t('admin.company')}
                <select
                  value={companyId}
                  onChange={(e) => {
                    setCompanyId(Number(e.target.value));
                    setBranchId('');
                    setSectorId('');
                  }}
                  required
                >
                  <option value="">{t('admin.select')}</option>
                  {companies.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </label>
            )}
            {needsBranch && (
              <label className="field-label">{t('admin.branchLabel')}
                <select
                  value={branchId}
                  onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
                  required
                >
                  <option value="">{t('ausers.selectBranch')}</option>
                  {scopedBranches.map((b) => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
              </label>
            )}
            {role === 'OWNER' && (
              <label className="field-label">{t('ausers.branchOptional')}
                <select
                  value={branchId}
                  onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
                >
                  <option value="">{t('ausers.wholeCompany')}</option>
                  {scopedBranches.map((b) => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
              </label>
            )}
            {needsCompany && (
              <label className="field-label">{t('ausers.sectorOptional')}
                <select value={sectorId} onChange={(e) => setSectorId(e.target.value === '' ? '' : Number(e.target.value))}>
                  <option value="">{t('ausers.noSector')}</option>
                  {scopedSectors.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </label>
            )}
            <label className="check">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              {t('ausers.accountActive')}
            </label>
            <p className="muted small">{t('ausers.lastPwdChange')} {formatWhen(user.passwordChangedAt)}</p>
            <button className="btn-primary" type="submit">{t('ausers.saveUser')}</button>
          </form>

          {user.locked && (
            <button
              type="button"
              className="btn-ghost"
              onClick={async () => {
                try {
                  await apiPost(`/admin/users/${user.id}/unlock`, {});
                  await onReload();
                  onOk(t('ausers.unlocked'));
                } catch (err) {
                  onError(err);
                }
              }}
            >
              {t('ausers.unlock')}
            </button>
          )}

          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault();
              const confirmError = passwordConfirmError(newPassword, confirm);
              if (confirmError) {
                onError(new Error(confirmError));
                return;
              }
              try {
                await apiPut(`/admin/users/${user.id}/password`, { newPassword });
                setNewPassword('');
                setConfirm('');
                await onReload();
                if (events) await loadEvents();
                onOk(t('ausers.pwdReset'));
              } catch (err) {
                onError(err);
              }
            }}
          >
            <h3 className="section-subtitle">{t('ausers.resetPwd')}</h3>
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
              <PasswordField value={confirm} onChange={setConfirm} autoComplete="new-password" required />
            </label>
            <button className="btn-ghost" type="submit">{t('ausers.applyPwd')}</button>
          </form>

          <div>
            <button
              type="button"
              className="btn-ghost"
              onClick={async () => {
                try {
                  await loadEvents();
                } catch (err) {
                  onError(err);
                }
              }}
            >
              {events ? t('ausers.refreshHistory') : t('ausers.viewHistory')}
            </button>
            {events && (
              events.length === 0 ? (
                <p className="muted small">{t('ausers.noChanges')}</p>
              ) : (
                <ul className="password-history">
                  {events.map((event) => (
                    <li key={event.id}>
                      <strong>{event.actionLabel}</strong>
                      <span className="muted small">
                        {formatWhen(event.createdAt)}
                        {event.actorName ? ` · ${event.actorName}` : event.actorUsername ? ` · @${event.actorUsername}` : ''}
                      </span>
                    </li>
                  ))}
                </ul>
              )
            )}
          </div>
        </div>
      )}
    </li>
  );
}
