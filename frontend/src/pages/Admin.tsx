import React, { useEffect, useState } from 'react';
import { apiGet, apiPost } from '../api';
import { ROLE_OPTIONS_ADMIN, roleLabel } from '../roles';

type Option = { id: number; name: string };
type UserRow = {
  id: string;
  username: string;
  fullName: string;
  role: string;
  roleLabel?: string;
  companyId?: number | null;
  branchId?: number | null;
  active: boolean;
};

export default function Admin() {
  const [companies, setCompanies] = useState<Option[]>([]);
  const [branches, setBranches] = useState<Option[]>([]);
  const [sectors, setSectors] = useState<Option[]>([]);
  const [users, setUsers] = useState<UserRow[]>([]);
  const [companyId, setCompanyId] = useState<number | ''>('');
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);

  async function loadCompanies() {
    const list = await apiGet('/catalog/companies');
    setCompanies(list);
    if (list.length && companyId === '') setCompanyId(list[0].id);
  }
  async function loadScoped(cid: number) {
    const [b, s] = await Promise.all([
      apiGet(`/catalog/branches?companyId=${cid}`),
      apiGet(`/catalog/sectors?companyId=${cid}`)
    ]);
    setBranches(b);
    setSectors(s);
  }
  async function loadUsers() {
    setUsers(await apiGet('/admin/users'));
  }

  useEffect(() => {
    loadCompanies().catch((e) => setError(e.message));
    loadUsers().catch((e) => setError(e.message));
  }, []);
  useEffect(() => {
    if (typeof companyId === 'number') loadScoped(companyId).catch((e) => setError(e.message));
  }, [companyId]);

  function flash(msg: string) {
    setOk(msg);
    setError(null);
    setTimeout(() => setOk(null), 2500);
  }
  function fail(e: unknown) {
    setError(e instanceof Error ? e.message : 'Erro');
  }

  return (
    <div className="page">
      {error && <div className="alert-error">{error}</div>}
      {ok && <div className="alert-ok">{ok}</div>}

      <CompanyForm
        onCreated={async () => {
          await loadCompanies();
          flash('Empresa criada.');
        }}
        onError={fail}
      />

      <BranchForm
        companies={companies}
        onCreated={async (cid) => {
          if (cid === companyId) await loadScoped(cid);
          flash('Filial criada.');
        }}
        onError={fail}
      />

      <SectorForm
        companies={companies}
        branches={branches}
        selectedCompany={companyId}
        onCompanyChange={setCompanyId}
        onCreated={async () => {
          if (typeof companyId === 'number') await loadScoped(companyId);
          flash('Setor criado.');
        }}
        onError={fail}
      />

      <UserForm
        companies={companies}
        branches={branches}
        sectors={sectors}
        selectedCompany={companyId}
        onCompanyChange={setCompanyId}
        onCreated={async () => {
          await loadUsers();
          flash('Usuário criado.');
        }}
        onError={fail}
      />

      <section className="card">
        <h2>Usuários</h2>
        <ul className="list">
          {users.map((u) => (
            <li key={u.id}>
              <div>
                <strong>{u.fullName}</strong>
                <div className="muted small">@{u.username}</div>
              </div>
              <span className="chip">{u.roleLabel || roleLabel(u.role)}</span>
            </li>
          ))}
        </ul>
      </section>

      <section className="card">
        <h2>Setores</h2>
        {sectors.length === 0 ? (
          <p className="muted">Nenhum setor nesta empresa.</p>
        ) : (
          <ul className="list">
            {sectors.map((s) => (
              <li key={s.id}><span>{s.name}</span></li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function CompanyForm({ onCreated, onError }: { onCreated: () => void; onError: (e: unknown) => void }) {
  const [name, setName] = useState('');
  return (
    <section className="card">
      <h2>Cadastrar empresa</h2>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault();
          try {
            await apiPost('/admin/companies', { name });
            setName('');
            onCreated();
          } catch (err) {
            onError(err);
          }
        }}
      >
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Nome da empresa" required />
        <button className="btn-primary" type="submit">Criar empresa</button>
      </form>
    </section>
  );
}

function BranchForm({
  companies,
  onCreated,
  onError
}: {
  companies: Option[];
  onCreated: (companyId: number) => void;
  onError: (e: unknown) => void;
}) {
  const [companyId, setCompanyId] = useState<number | ''>('');
  const [name, setName] = useState('');
  useEffect(() => {
    if (companies.length && companyId === '') setCompanyId(companies[0].id);
  }, [companies]);
  return (
    <section className="card">
      <h2>Cadastrar filial</h2>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault();
          if (typeof companyId !== 'number') return;
          try {
            await apiPost('/admin/branches', { companyId, name });
            setName('');
            onCreated(companyId);
          } catch (err) {
            onError(err);
          }
        }}
      >
        <label className="field-label">Empresa</label>
        <select value={companyId} onChange={(e) => setCompanyId(Number(e.target.value))}>
          {companies.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Nome da filial" required />
        <button className="btn-primary" type="submit">Criar filial</button>
      </form>
    </section>
  );
}

function SectorForm({
  companies,
  branches,
  selectedCompany,
  onCompanyChange,
  onCreated,
  onError
}: {
  companies: Option[];
  branches: Option[];
  selectedCompany: number | '';
  onCompanyChange: (id: number) => void;
  onCreated: () => void;
  onError: (e: unknown) => void;
}) {
  const [name, setName] = useState('');
  const [branchId, setBranchId] = useState<number | ''>('');
  return (
    <section className="card">
      <h2>Cadastrar setor</h2>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault();
          if (typeof selectedCompany !== 'number') return;
          try {
            await apiPost('/admin/sectors', {
              companyId: selectedCompany,
              branchId: branchId === '' ? null : branchId,
              name
            });
            setName('');
            onCreated();
          } catch (err) {
            onError(err);
          }
        }}
      >
        <label className="field-label">Empresa</label>
        <select value={selectedCompany} onChange={(e) => onCompanyChange(Number(e.target.value))}>
          {companies.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
        <label className="field-label">Filial</label>
        <select value={branchId} onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}>
          <option value="">(Sem filial específica)</option>
          {branches.map((b) => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Nome do setor" required />
        <button className="btn-primary" type="submit">Criar setor</button>
      </form>
    </section>
  );
}

function UserForm({
  companies,
  branches,
  sectors,
  selectedCompany,
  onCompanyChange,
  onCreated,
  onError
}: {
  companies: Option[];
  branches: Option[];
  sectors: Option[];
  selectedCompany: number | '';
  onCompanyChange: (id: number) => void;
  onCreated: () => void;
  onError: (e: unknown) => void;
}) {
  const [username, setUsername] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState('MANAGER');
  const [password, setPassword] = useState('');
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
          try {
            await apiPost('/admin/users', {
              username,
              fullName,
              role,
              password,
              companyId: needsCompany && typeof selectedCompany === 'number' ? selectedCompany : null,
              branchId: needsBranch && branchId !== '' ? branchId : null,
              sectorId: sectorId === '' ? null : sectorId
            });
            setUsername('');
            setFullName('');
            setPassword('');
            onCreated();
          } catch (err) {
            onError(err);
          }
        }}
      >
        <input value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Nome completo" required />
        <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Usuário (a-z, 0-9, . _ -)" autoCapitalize="none" required />
        <label className="field-label">Função</label>
        <select value={role} onChange={(e) => setRole(e.target.value)}>
          {ROLE_OPTIONS_ADMIN.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </select>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Senha (mín. 8, letras e números)" required />
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
