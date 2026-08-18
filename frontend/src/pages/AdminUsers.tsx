import React, { useEffect, useState } from 'react';
import { apiGet, apiPost, apiPut } from '../api';
import PasswordField from '../components/PasswordField';
import { passwordConfirmError } from '../password';
import { ROLE_OPTIONS_ADMIN, roleLabel } from '../roles';

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
          onOk('Usuário criado.');
        }}
        onError={onError}
      />
      <section className="card">
        <h2>Usuários</h2>
        {users.length === 0 ? (
          <p className="muted">Nenhum usuário cadastrado.</p>
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

  return (
    <section className="card">
      <h2>Cadastrar usuário</h2>
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
        <input value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Nome completo" required />
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="Usuário (a-z, 0-9, . _ -)"
          autoCapitalize="none"
          autoComplete="off"
          required
        />
        <input
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          type="email"
          placeholder="E-mail (avisos e recuperação de senha)"
          autoCapitalize="none"
          autoComplete="off"
        />
        <label className="field-label">Função</label>
        <select value={role} onChange={(e) => setRole(e.target.value)}>
          {ROLE_OPTIONS_ADMIN.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </select>
        <label className="field-label">Senha inicial
          <PasswordField
            value={password}
            onChange={setPassword}
            autoComplete="new-password"
            placeholder="mín. 8, letras e números"
            required
          />
        </label>
        <label className="field-label">Confirmar senha
          <PasswordField value={confirm} onChange={setConfirm} autoComplete="new-password" required />
        </label>
        {needsCompany && (
          <>
            <label className="field-label">Empresa</label>
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
            <label className="field-label">Filial</label>
            <select
              value={branchId}
              onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
              required
            >
              <option value="">Selecione a filial</option>
              {branches.map((b) => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
          </>
        )}
        <label className="field-label">Setor (opcional)</label>
        <select value={sectorId} onChange={(e) => setSectorId(e.target.value === '' ? '' : Number(e.target.value))}>
          <option value="">(Sem setor)</option>
          {sectors.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
        <button className="btn-primary" type="submit">Criar usuário</button>
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
          {!user.active && <span className="chip">Inativo</span>}
          {user.locked && <span className="chip">Bloqueado</span>}
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
                onOk('Cadastro atualizado.');
              } catch (err) {
                onError(err);
              }
            }}
          >
            <label className="field-label">Login (não altera)
              <input value={user.username} readOnly />
            </label>
            <label className="field-label">E-mail
              <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="e-mail para avisos/recuperação" autoCapitalize="none" />
            </label>
            <label className="field-label">Nome completo
              <input value={fullName} onChange={(e) => setFullName(e.target.value)} required />
            </label>
            <label className="field-label">Função
              <select value={role} onChange={(e) => setRole(e.target.value)}>
                {ROLE_OPTIONS_ADMIN.map((r) => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </label>
            {needsCompany && (
              <label className="field-label">Empresa
                <select
                  value={companyId}
                  onChange={(e) => {
                    setCompanyId(Number(e.target.value));
                    setBranchId('');
                    setSectorId('');
                  }}
                  required
                >
                  <option value="">Selecione</option>
                  {companies.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </label>
            )}
            {needsBranch && (
              <label className="field-label">Filial
                <select
                  value={branchId}
                  onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
                  required
                >
                  <option value="">Selecione a filial</option>
                  {scopedBranches.map((b) => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
              </label>
            )}
            {role === 'OWNER' && (
              <label className="field-label">Filial (opcional)
                <select
                  value={branchId}
                  onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
                >
                  <option value="">(Toda a empresa)</option>
                  {scopedBranches.map((b) => (
                    <option key={b.id} value={b.id}>{b.name}</option>
                  ))}
                </select>
              </label>
            )}
            {needsCompany && (
              <label className="field-label">Setor (opcional)
                <select value={sectorId} onChange={(e) => setSectorId(e.target.value === '' ? '' : Number(e.target.value))}>
                  <option value="">(Sem setor)</option>
                  {scopedSectors.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </label>
            )}
            <label className="check">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Conta ativa
            </label>
            <p className="muted small">Última troca de senha: {formatWhen(user.passwordChangedAt)}</p>
            <button className="btn-primary" type="submit">Salvar cadastro</button>
          </form>

          {user.locked && (
            <button
              type="button"
              className="btn-ghost"
              onClick={async () => {
                try {
                  await apiPost(`/admin/users/${user.id}/unlock`, {});
                  await onReload();
                  onOk('Conta desbloqueada.');
                } catch (err) {
                  onError(err);
                }
              }}
            >
              Desbloquear login
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
                onOk('Senha redefinida. As sessões anteriores deste usuário são encerradas.');
              } catch (err) {
                onError(err);
              }
            }}
          >
            <h3 className="section-subtitle">Redefinir senha</h3>
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
              <PasswordField value={confirm} onChange={setConfirm} autoComplete="new-password" required />
            </label>
            <button className="btn-ghost" type="submit">Aplicar nova senha</button>
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
              {events ? 'Atualizar histórico' : 'Ver histórico de senhas'}
            </button>
            {events && (
              events.length === 0 ? (
                <p className="muted small">Nenhuma troca registrada.</p>
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
