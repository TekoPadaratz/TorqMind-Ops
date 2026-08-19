import React, { useEffect, useState } from 'react';
import { apiGet, apiPost, apiPut } from '../api';
import { formatCep, formatCnpj, formatUf } from '../masks';
import { useI18n } from '../i18n';
import UsersAdmin, { AdminUser } from './AdminUsers';
import AdminSettings from './AdminSettings';
import AdminEmailSettings from './AdminEmailSettings';
import AdminApiKeys from './AdminApiKeys';
import AdminWebhooks from './AdminWebhooks';

type Option = {
  id: number;
  name: string;
  legalName?: string | null;
  cnpj?: string | null;
  address?: {
    street?: string | null;
    number?: string | null;
    complement?: string | null;
    neighborhood?: string | null;
    city?: string | null;
    state?: string | null;
    postalCode?: string | null;
  };
};
type UserRow = AdminUser;

export default function Admin() {
  const { t } = useI18n();
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
    setError(e instanceof Error ? e.message : t('admin.error'));
  }

  return (
    <div className="page">
      {error && <div className="alert-error">{error}</div>}
      {ok && <div className="alert-ok">{ok}</div>}

      <CompanyForm
        companies={companies}
        onCreated={async () => {
          await loadCompanies();
          flash(t('admin.companyCreated'));
        }}
        onUpdated={async () => {
          await loadCompanies();
          flash(t('admin.companyUpdated'));
        }}
        onError={fail}
      />

      <BranchForm
        companies={companies}
        branches={branches}
        onCreated={async (cid) => {
          if (cid === companyId) await loadScoped(cid);
          flash(t('admin.branchCreated'));
        }}
        onUpdated={async (cid) => {
          if (cid === companyId) await loadScoped(cid);
          flash(t('admin.branchUpdated'));
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
          flash(t('admin.sectorCreated'));
        }}
        onError={fail}
      />

      <UsersAdmin
        companies={companies}
        branches={branches}
        sectors={sectors}
        selectedCompany={companyId}
        onCompanyChange={setCompanyId}
        users={users}
        onReload={loadUsers}
        onOk={flash}
        onError={fail}
      />

      <AdminSettings
        companies={companies}
        selectedCompany={companyId}
        onCompanyChange={setCompanyId}
        onOk={flash}
        onError={fail}
      />

      <AdminEmailSettings onOk={flash} onError={fail} />

      <AdminApiKeys companies={companies} onOk={flash} onError={fail} />

      <AdminWebhooks companies={companies} onOk={flash} onError={fail} />

      <section className="card">
        <h2>{t('admin.sectors')}</h2>
        {sectors.length === 0 ? (
          <p className="muted">{t('admin.noSectors')}</p>
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

function emptyLegal() {
  return {
    name: '',
    legalName: '',
    cnpj: '',
    addressStreet: '',
    addressNumber: '',
    addressComplement: '',
    addressNeighborhood: '',
    addressCity: '',
    addressState: '',
    addressPostalCode: ''
  };
}

function fromOption(item?: Option | null) {
  const legal = emptyLegal();
  if (!item) return legal;
  legal.name = item.name ?? '';
  legal.legalName = item.legalName ?? '';
  legal.cnpj = item.cnpj ?? '';
  legal.addressStreet = item.address?.street ?? '';
  legal.addressNumber = item.address?.number ?? '';
  legal.addressComplement = item.address?.complement ?? '';
  legal.addressNeighborhood = item.address?.neighborhood ?? '';
  legal.addressCity = item.address?.city ?? '';
  legal.addressState = item.address?.state ?? '';
  legal.addressPostalCode = item.address?.postalCode ?? '';
  return legal;
}

function LegalFields({
  value,
  onChange
}: {
  value: ReturnType<typeof emptyLegal>;
  onChange: (next: ReturnType<typeof emptyLegal>) => void;
}) {
  function set<K extends keyof ReturnType<typeof emptyLegal>>(key: K, val: string) {
    onChange({ ...value, [key]: val });
  }
  const { t } = useI18n();
  return (
    <>
      <input value={value.name} onChange={(e) => set('name', e.target.value)} placeholder={t('admin.legal.name')} required />
      <input value={value.legalName} onChange={(e) => set('legalName', e.target.value)} placeholder={t('admin.legal.legalName')} />
      <input value={value.cnpj} onChange={(e) => set('cnpj', formatCnpj(e.target.value))} placeholder={t('admin.legal.cnpj')} inputMode="numeric" />
      <div className="form-grid two">
        <input value={value.addressStreet} onChange={(e) => set('addressStreet', e.target.value)} placeholder={t('admin.legal.street')} />
        <input value={value.addressNumber} onChange={(e) => set('addressNumber', e.target.value)} placeholder={t('admin.legal.number')} />
        <input value={value.addressComplement} onChange={(e) => set('addressComplement', e.target.value)} placeholder={t('admin.legal.complement')} />
        <input value={value.addressNeighborhood} onChange={(e) => set('addressNeighborhood', e.target.value)} placeholder={t('admin.legal.neighborhood')} />
        <input value={value.addressCity} onChange={(e) => set('addressCity', e.target.value)} placeholder={t('admin.legal.city')} />
        <input value={value.addressState} onChange={(e) => set('addressState', formatUf(e.target.value))} placeholder={t('admin.legal.state')} />
        <input value={value.addressPostalCode} onChange={(e) => set('addressPostalCode', formatCep(e.target.value))} placeholder={t('admin.legal.postalCode')} inputMode="numeric" />
      </div>
    </>
  );
}

function CompanyForm({
  companies,
  onCreated,
  onUpdated,
  onError
}: {
  companies: Option[];
  onCreated: () => void;
  onUpdated: () => void;
  onError: (e: unknown) => void;
}) {
  const [create, setCreate] = useState(emptyLegal());
  const [editId, setEditId] = useState<number | ''>('');
  const [edit, setEdit] = useState(emptyLegal());
  const { t } = useI18n();
  useEffect(() => {
    const selected = companies.find((c) => c.id === editId);
    setEdit(fromOption(selected));
  }, [editId, companies]);
  return (
    <section className="card">
      <h2>{t('admin.company')}</h2>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault();
          try {
            await apiPost('/admin/companies', create);
            setCreate(emptyLegal());
            onCreated();
          } catch (err) {
            onError(err);
          }
        }}
      >
        <LegalFields value={create} onChange={setCreate} />
        <button className="btn-primary" type="submit">{t('admin.createCompany')}</button>
      </form>
      {companies.length > 0 && (
        <form
          className="stack"
          style={{ marginTop: 16 }}
          onSubmit={async (e) => {
            e.preventDefault();
            if (typeof editId !== 'number') return;
            try {
              await apiPut(`/admin/companies/${editId}`, edit);
              onUpdated();
            } catch (err) {
              onError(err);
            }
          }}
        >
          <label className="field-label">{t('admin.updateCompany')}
            <select value={editId} onChange={(e) => setEditId(e.target.value ? Number(e.target.value) : '')}>
              <option value="">{t('admin.select')}</option>
              {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </label>
          {typeof editId === 'number' && <LegalFields value={edit} onChange={setEdit} />}
          {typeof editId === 'number' && <button className="btn-primary" type="submit">{t('admin.saveCompany')}</button>}
        </form>
      )}
    </section>
  );
}

function BranchForm({
  companies,
  branches,
  onCreated,
  onUpdated,
  onError
}: {
  companies: Option[];
  branches: Option[];
  onCreated: (companyId: number) => void;
  onUpdated: (companyId: number) => void;
  onError: (e: unknown) => void;
}) {
  const [companyId, setCompanyId] = useState<number | ''>('');
  const [create, setCreate] = useState(emptyLegal());
  const [editId, setEditId] = useState<number | ''>('');
  const [edit, setEdit] = useState(emptyLegal());
  const { t } = useI18n();
  useEffect(() => {
    if (companies.length && companyId === '') setCompanyId(companies[0].id);
  }, [companies]);
  useEffect(() => {
    setEdit(fromOption(branches.find((b) => b.id === editId)));
  }, [editId, branches]);
  return (
    <section className="card">
      <h2>{t('admin.branch')}</h2>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault();
          if (typeof companyId !== 'number') return;
          try {
            await apiPost('/admin/branches', { companyId, ...create });
            setCreate(emptyLegal());
            onCreated(companyId);
          } catch (err) {
            onError(err);
          }
        }}
      >
        <label className="field-label">{t('admin.company')}
          <select value={companyId} onChange={(e) => setCompanyId(Number(e.target.value))}>
            {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <LegalFields value={create} onChange={setCreate} />
        <button className="btn-primary" type="submit">{t('admin.createBranch')}</button>
      </form>
      {branches.length > 0 && typeof companyId === 'number' && (
        <form
          className="stack"
          style={{ marginTop: 16 }}
          onSubmit={async (e) => {
            e.preventDefault();
            if (typeof editId !== 'number') return;
            try {
              await apiPut(`/admin/branches/${editId}`, { companyId, ...edit });
              onUpdated(companyId);
            } catch (err) {
              onError(err);
            }
          }}
        >
          <label className="field-label">{t('admin.updateBranch')}
            <select value={editId} onChange={(e) => setEditId(e.target.value ? Number(e.target.value) : '')}>
              <option value="">{t('admin.select')}</option>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </label>
          {typeof editId === 'number' && <LegalFields value={edit} onChange={setEdit} />}
          {typeof editId === 'number' && <button className="btn-primary" type="submit">{t('admin.saveBranch')}</button>}
        </form>
      )}
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
  const { t } = useI18n();
  return (
    <section className="card">
      <h2>{t('admin.registerSector')}</h2>
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
        <label className="field-label">{t('admin.company')}</label>
        <select value={selectedCompany} onChange={(e) => onCompanyChange(Number(e.target.value))}>
          {companies.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
        <label className="field-label">{t('admin.branchLabel')}</label>
        <select value={branchId} onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}>
          <option value="">{t('admin.noBranchSpecific')}</option>
          {branches.map((b) => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t('admin.sectorName')} required />
        <button className="btn-primary" type="submit">{t('admin.createSector')}</button>
      </form>
    </section>
  );
}
