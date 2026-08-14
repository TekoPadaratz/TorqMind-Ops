import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiDelete, apiGet, apiPost } from '../api';
import { useAuth } from '../auth';

type Template = {
  id: number;
  title: string;
  description: string | null;
  recurrenceRule: string;
  targetType: string;
  targetSectorId: number | null;
  targetUserId: string | null;
  startTime: string | null;
  dueTime: string | null;
  weekday: number | null;
  dayOfMonth: number | null;
  customDays: string | null;
  businessDaysOnly: boolean;
  startDate: string | null;
  reminderBeforeMinutes: number | null;
  requiresPhoto: boolean;
  requiresComment: boolean;
};

type Run = {
  id: number;
  templateId: number;
  status: string;
  scheduledFor: string;
  dueAt: string | null;
};

type Option = { id: number; name: string };
type UserOpt = { id: string; username: string; fullName: string; role: string };

const STATUS_LABEL: Record<string, string> = {
  PENDENTE: 'Pendente',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  ATRASADA: 'Atrasada',
  REJEITADA: 'Rejeitada'
};

const RECURRENCE_LABEL: Record<string, string> = {
  ONCE: 'Uma vez',
  DAILY: 'Diária',
  WEEKLY: 'Semanal',
  MONTHLY: 'Mensal',
  CUSTOM: 'Personalizado'
};

const TARGET_LABEL: Record<string, string> = {
  MANAGERS: 'Todos os gerentes',
  ALL: 'Todos',
  SECTOR: 'Setor',
  USER: 'Usuário'
};

const RUN_FILTERS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todas' },
  { value: 'PENDENTE', label: 'Pendentes' },
  { value: 'EM_ANDAMENTO', label: 'Em andamento' },
  { value: 'CONCLUIDA', label: 'Concluídas' },
  { value: 'ATRASADA', label: 'Atrasadas' },
  { value: 'REJEITADA', label: 'Rejeitadas' }
];

export default function Routines() {
  const navigate = useNavigate();
  const { session } = useAuth();
  const canDelete = session?.role === 'MASTER' || session?.role === 'OWNER';
  const isManager = session?.role === 'MANAGER' || session?.role === 'OPERATOR';
  const canPickBranch = session?.role === 'MASTER' || session?.role === 'OWNER';

  const [templates, setTemplates] = useState<Template[]>([]);
  const [runs, setRuns] = useState<Run[]>([]);
  const [sectors, setSectors] = useState<Option[]>([]);
  const [branches, setBranches] = useState<Option[]>([]);
  const [users, setUsers] = useState<UserOpt[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [recurrence, setRecurrence] = useState('DAILY');
  const [targetType, setTargetType] = useState('MANAGERS');
  const [sectorId, setSectorId] = useState<number | ''>('');
  const [userId, setUserId] = useState('');
  const [branchId, setBranchId] = useState<number | ''>(session?.branchId ?? '');
  const [startTime, setStartTime] = useState('10:00');
  const [dueTime, setDueTime] = useState('12:00');
  const [weekday, setWeekday] = useState(1);
  const [dayOfMonth, setDayOfMonth] = useState(1);
  const [customDays, setCustomDays] = useState<number[]>([]);
  const [businessDaysOnly, setBusinessDaysOnly] = useState(false);
  const [startDate, setStartDate] = useState('');
  const [reminderMinutes, setReminderMinutes] = useState(30);
  const [requiresPhoto, setRequiresPhoto] = useState(false);
  const [requiresComment, setRequiresComment] = useState(false);
  const [runStatus, setRunStatus] = useState('');

  function toggleCustomDay(day: number) {
    setCustomDays((prev) => (prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day].sort((a, b) => a - b)));
  }

  async function loadRuns(status: string) {
    const q = status ? `?status=${status}` : '';
    setRuns(await apiGet(`/routines/runs${q}`));
  }

  async function reload() {
    try {
      const [t, u, b, s] = await Promise.all([
        apiGet('/routines/templates'),
        apiGet('/catalog/users'),
        apiGet('/catalog/branches'),
        apiGet('/catalog/sectors')
      ]);
      setTemplates(t);
      setUsers(u);
      setBranches(b);
      setSectors(s);
      if (canPickBranch && branchId === '' && b.length) {
        setBranchId(b[0].id);
      }
      if (isManager && session?.branchId) {
        setBranchId(session.branchId);
      }
      await loadRuns(runStatus);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar');
    }
  }

  useEffect(() => {
    reload();
  }, []);

  useEffect(() => {
    loadRuns(runStatus).catch((e) => setError(e instanceof Error ? e.message : 'Erro ao carregar'));
  }, [runStatus]);

  async function createTask(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (recurrence === 'CUSTOM' && customDays.length === 0) {
      setError('Selecione ao menos um dia no calendário.');
      return;
    }
    try {
      await apiPost('/routines/tasks', {
        companyId: session?.companyId ?? undefined,
        branchId: branchId === '' ? null : branchId,
        title,
        description,
        recurrence,
        targetType,
        targetSectorId: targetType === 'SECTOR' ? (sectorId === '' ? null : sectorId) : null,
        targetUserId: targetType === 'USER' ? (userId || null) : null,
        startTime,
        dueTime,
        weekday: recurrence === 'WEEKLY' ? weekday : null,
        dayOfMonth: recurrence === 'MONTHLY' ? dayOfMonth : null,
        customDays: recurrence === 'CUSTOM' ? customDays : null,
        businessDaysOnly: (recurrence === 'MONTHLY' || recurrence === 'CUSTOM') ? businessDaysOnly : false,
        startDate: recurrence === 'ONCE' ? (startDate || null) : null,
        reminderBeforeMinutes: reminderMinutes,
        requiresPhoto,
        requiresComment
      });
      setTitle('');
      setDescription('');
      setCustomDays([]);
      setBusinessDaysOnly(false);
      setRequiresPhoto(false);
      setRequiresComment(false);
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao criar tarefa');
    }
  }

  async function removeTemplate(id: number) {
    if (!window.confirm('Remover esta rotina programada?')) return;
    setError(null);
    try {
      await apiDelete(`/routines/templates/${id}`);
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao remover');
    }
  }

  async function generateNow(id: number) {
    setError(null);
    try {
      await apiPost(`/routines/templates/${id}/generate`, {});
      await loadRuns(runStatus);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao gerar');
    }
  }

  function targetSummary(t: Template): string {
    if (t.targetType === 'USER') {
      const u = users.find((x) => x.id === t.targetUserId);
      return u ? u.fullName : 'Usuário';
    }
    if (t.targetType === 'SECTOR') {
      const s = sectors.find((x) => x.id === t.targetSectorId);
      return s ? `Setor: ${s.name}` : 'Setor';
    }
    return TARGET_LABEL[t.targetType] ?? t.targetType;
  }

  function scheduleSummary(t: Template): string {
    const rec = RECURRENCE_LABEL[t.recurrenceRule] ?? t.recurrenceRule;
    let extra = '';
    if (t.recurrenceRule === 'CUSTOM' && t.customDays) {
      extra = ` · dias ${t.customDays}`;
    } else if (t.recurrenceRule === 'MONTHLY' && t.dayOfMonth != null) {
      extra = ` · dia ${t.dayOfMonth}`;
    }
    if (t.businessDaysOnly) {
      extra += ' · dia útil seguinte se fim de semana';
    }
    const window = t.startTime && t.dueTime ? ` · ${t.startTime.slice(0, 5)}–${t.dueTime.slice(0, 5)}` : '';
    const reminder = t.reminderBeforeMinutes != null ? ` · aviso ${t.reminderBeforeMinutes}min antes` : '';
    return `${rec}${extra}${window} · ${targetSummary(t)}${reminder}`;
  }

  return (
    <div className="page">
      {error && <div className="alert-error">{error}</div>}

      <section className="card">
        <h2>Nova tarefa</h2>
        <form onSubmit={createTask} className="stack">
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Título (ex: Checklist de abertura)" required />
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Descrição / instruções (opcional)" />

          <label className="field-label">Recorrência</label>
          <select value={recurrence} onChange={(e) => setRecurrence(e.target.value)}>
            <option value="ONCE">Uma vez</option>
            <option value="DAILY">Diária</option>
            <option value="WEEKLY">Semanal</option>
            <option value="MONTHLY">Mensal</option>
            <option value="CUSTOM">Personalizado</option>
          </select>

          <label className="field-label">Para quem</label>
          <select value={targetType} onChange={(e) => setTargetType(e.target.value)}>
            <option value="MANAGERS">Todos os gerentes</option>
            <option value="ALL">Todos</option>
            <option value="SECTOR">Setor específico</option>
            <option value="USER">Usuário específico</option>
          </select>

          {canPickBranch && (
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

          {targetType === 'SECTOR' && (
            <select value={sectorId} onChange={(e) => setSectorId(e.target.value === '' ? '' : Number(e.target.value))} required>
              <option value="">Selecione o setor</option>
              {sectors.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          )}
          {targetType === 'USER' && (
            <select value={userId} onChange={(e) => setUserId(e.target.value)} required>
              <option value="">Selecione o usuário</option>
              {users
                .filter((u) => u.role !== 'MASTER')
                .map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName} ({u.role === 'OWNER' ? 'Dono da empresa' : u.role === 'MANAGER' ? 'Gerente' : u.role === 'OPERATOR' ? 'Funcionário' : u.role})
                  </option>
                ))}
            </select>
          )}

          {recurrence === 'WEEKLY' && (
            <>
              <label className="field-label">Dia da semana</label>
              <select value={weekday} onChange={(e) => setWeekday(Number(e.target.value))}>
                <option value={1}>Segunda</option>
                <option value={2}>Terça</option>
                <option value={3}>Quarta</option>
                <option value={4}>Quinta</option>
                <option value={5}>Sexta</option>
                <option value={6}>Sábado</option>
                <option value={7}>Domingo</option>
              </select>
            </>
          )}
          {recurrence === 'MONTHLY' && (
            <>
              <label className="field-label">Dia do mês</label>
              <input type="number" min={1} max={31} value={dayOfMonth} onChange={(e) => setDayOfMonth(Number(e.target.value))} />
            </>
          )}
          {recurrence === 'CUSTOM' && (
            <>
              <label className="field-label">Dias do mês</label>
              <p className="muted small">Toque nos números para selecionar os dias em que a tarefa deve repetir.</p>
              <div className="day-calendar" role="group" aria-label="Calendário de dias do mês">
                {Array.from({ length: 31 }, (_, i) => i + 1).map((day) => (
                  <button
                    key={day}
                    type="button"
                    className={`day-cell ${customDays.includes(day) ? 'selected' : ''}`}
                    onClick={() => toggleCustomDay(day)}
                    aria-pressed={customDays.includes(day)}
                  >
                    {day}
                  </button>
                ))}
              </div>
              {customDays.length > 0 && (
                <p className="muted small">Selecionados: {customDays.join(', ')}</p>
              )}
            </>
          )}
          {recurrence === 'ONCE' && (
            <>
              <label className="field-label">Data</label>
              <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
            </>
          )}
          {(recurrence === 'MONTHLY' || recurrence === 'CUSTOM') && (
            <label className="check">
              <input
                type="checkbox"
                checked={businessDaysOnly}
                onChange={(e) => setBusinessDaysOnly(e.target.checked)}
              />
              Considerar somente dias úteis
            </label>
          )}
          {(recurrence === 'MONTHLY' || recurrence === 'CUSTOM') && businessDaysOnly && (
            <p className="muted small">
              Se o dia cair no fim de semana ou feriado nacional, a tarefa é gerada no próximo dia útil.
            </p>
          )}

          <div className="time-row">
            <div className="field-block">
              <label className="field-label">Horário de início</label>
              <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
            </div>
            <div className="field-block">
              <label className="field-label">Horário de vencimento</label>
              <input type="time" value={dueTime} onChange={(e) => setDueTime(e.target.value)} required />
            </div>
          </div>

          <label className="field-label">Avisar quantos minutos antes do vencimento</label>
          <input type="number" min={0} max={1440} value={reminderMinutes} onChange={(e) => setReminderMinutes(Number(e.target.value))} />

          <label className="check">
            <input type="checkbox" checked={requiresPhoto} onChange={(e) => setRequiresPhoto(e.target.checked)} />
            Exige foto na conclusão
          </label>
          <label className="check">
            <input type="checkbox" checked={requiresComment} onChange={(e) => setRequiresComment(e.target.checked)} />
            Exige comentário na conclusão
          </label>
          <button type="submit" className="btn-primary">Criar tarefa</button>
        </form>
      </section>

      <section className="card">
        <h2>Rotinas programadas</h2>
        {templates.length === 0 ? (
          <p className="muted">Nenhuma rotina programada.</p>
        ) : (
          <ul className="list">
            {templates.map((t) => (
              <li key={t.id}>
                <div>
                  <strong>{t.title}</strong>
                  <div className="muted small">{scheduleSummary(t)}</div>
                  {(t.requiresPhoto || t.requiresComment) && (
                    <div className="muted small">
                      {t.requiresPhoto ? 'foto' : ''}
                      {t.requiresPhoto && t.requiresComment ? ' · ' : ''}
                      {t.requiresComment ? 'comentário' : ''}
                    </div>
                  )}
                </div>
                <div className="actions">
                  <button className="btn-ghost" onClick={() => generateNow(t.id)}>Gerar agora</button>
                  {canDelete && <button className="btn-ghost danger" onClick={() => removeTemplate(t.id)}>Excluir</button>}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card">
        <h2>Tarefas</h2>
        <div className="filter-row">
          {RUN_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              className={`filter-chip ${runStatus === f.value ? 'active' : ''}`}
              onClick={() => setRunStatus(f.value)}
            >
              {f.label}
            </button>
          ))}
        </div>
        {runs.length === 0 ? (
          <p className="muted">Nenhuma tarefa neste filtro.</p>
        ) : (
          <ul className="list">
            {runs.map((run) => {
              const template = templates.find((t) => t.id === run.templateId);
              return (
                <li key={run.id} className="clickable" onClick={() => navigate(`/routines/${run.id}`)}>
                  <div>
                    <strong>{template?.title ?? `Tarefa #${run.id}`}</strong>
                    <div className="muted small">Vence: {run.dueAt ? new Date(run.dueAt).toLocaleString() : '—'}</div>
                  </div>
                  <span className={`chip status-${run.status.toLowerCase()}`}>{STATUS_LABEL[run.status] ?? run.status}</span>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
