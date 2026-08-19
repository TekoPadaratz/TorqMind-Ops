import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiBlob, apiDelete, apiGet, apiPost } from '../api';
import { useAuth } from '../auth';
import { roleLabel } from '../roles';
import { useI18n } from '../i18n';

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
type SectorOpt = Option & { branchId: number | null };
type UserOpt = { id: string; username: string; fullName: string; role: string; branchId: number | null };

const REASSIGNABLE = new Set(['PENDENTE', 'EM_ANDAMENTO', 'ATRASADA']);

const RUN_FILTERS: Array<{ value: string; key: string }> = [
  { value: '', key: 'routines.filter.all' },
  { value: 'PENDENTE', key: 'routines.filter.pending' },
  { value: 'EM_ANDAMENTO', key: 'routines.filter.inProgress' },
  { value: 'CONCLUIDA', key: 'routines.filter.done' },
  { value: 'ATRASADA', key: 'routines.filter.late' },
  { value: 'REJEITADA', key: 'routines.filter.rejected' }
];

export default function Routines() {
  const navigate = useNavigate();
  const { session } = useAuth();
  const i18n = useI18n();
  const canDelete = session?.role === 'MASTER' || session?.role === 'OWNER';
  const isManager = session?.role === 'MANAGER' || session?.role === 'OPERATOR';
  const canPickBranch = session?.role === 'MASTER' || session?.role === 'OWNER';
  const canReassign = session?.role === 'MASTER' || session?.role === 'OWNER' || session?.role === 'MANAGER';

  const [templates, setTemplates] = useState<Template[]>([]);
  const [runs, setRuns] = useState<Run[]>([]);
  const [sectors, setSectors] = useState<SectorOpt[]>([]);
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
  const [search, setSearch] = useState('');
  const [checklistsEnabled, setChecklistsEnabled] = useState(false);
  const [checklistItems, setChecklistItems] = useState<Array<{ label: string; required: boolean }>>([]);
  const [checklistDraft, setChecklistDraft] = useState('');
  const [selTemplates, setSelTemplates] = useState<Set<number>>(new Set());
  const [selRuns, setSelRuns] = useState<Set<number>>(new Set());
  const [reassignUser, setReassignUser] = useState('');
  const [reassignMode, setReassignMode] = useState(false);
  const [deleteMode, setDeleteMode] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const usersForBranch = users.filter((user) => branchId === '' || user.branchId === branchId);
  const sectorsForBranch = sectors.filter(
    (sector) => branchId === '' || sector.branchId == null || sector.branchId === branchId
  );

  function toggleCustomDay(day: number) {
    setCustomDays((prev) => (prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day].sort((a, b) => a - b)));
  }

  useEffect(() => {
    apiGet('/company/settings')
      .then((s) => setChecklistsEnabled(!!s.checklistsEnabled))
      .catch(() => undefined);
  }, []);

  function addChecklistItem() {
    const label = checklistDraft.trim();
    if (!label) return;
    setChecklistItems([...checklistItems, { label, required: true }]);
    setChecklistDraft('');
  }
  function removeChecklistItem(idx: number) {
    setChecklistItems(checklistItems.filter((_, i) => i !== idx));
  }
  function toggleItemRequired(idx: number) {
    setChecklistItems(checklistItems.map((it, i) => (i === idx ? { ...it, required: !it.required } : it)));
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
      setError(e instanceof Error ? e.message : i18n.t('common.loadError'));
    }
  }

  useEffect(() => {
    reload();
  }, []);

  useEffect(() => {
    loadRuns(runStatus).catch((e) => setError(e instanceof Error ? e.message : i18n.t('common.loadError')));
  }, [runStatus]);

  async function createTask(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (recurrence === 'CUSTOM' && customDays.length === 0) {
      setError(i18n.t('routines.err.selectDay'));
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
        requiresComment,
        checklistItems: checklistsEnabled ? checklistItems : []
      });
      setTitle('');
      setDescription('');
      setCustomDays([]);
      setBusinessDaysOnly(false);
      setRequiresPhoto(false);
      setRequiresComment(false);
      setChecklistItems([]);
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : i18n.t('routines.err.create'));
    }
  }

  async function removeTemplate(id: number) {
    if (!window.confirm(i18n.t('routines.confirm.remove'))) return;
    setError(null);
    try {
      await apiDelete(`/routines/templates/${id}`);
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : i18n.t('routines.err.remove'));
    }
  }

  async function generateNow(id: number) {
    setError(null);
    try {
      await apiPost(`/routines/templates/${id}/generate`, {});
      await loadRuns(runStatus);
    } catch (e) {
      setError(e instanceof Error ? e.message : i18n.t('routines.err.generate'));
    }
  }

  async function exportCsv() {
    setError(null);
    try {
      const q = runStatus ? `?status=${runStatus}` : '';
      const blob = await apiBlob(`/routines/runs/export.csv${q}`);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'rotinas.csv';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
    } catch (e) {
      setError(e instanceof Error ? e.message : i18n.t('routines.err.export'));
    }
  }

  function toggleSet(set: Set<number>, setter: (s: Set<number>) => void, id: number) {
    const next = new Set(set);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setter(next);
  }

  function toggleDeleteMode() {
    setDeleteMode((m) => !m);
    setSelTemplates(new Set());
  }

  function toggleReassignMode() {
    setNotice(null);
    setReassignMode((m) => !m);
    setSelRuns(new Set());
    setReassignUser('');
  }

  function onRunRow(run: Run) {
    if (reassignMode) {
      if (REASSIGNABLE.has(run.status)) toggleSet(selRuns, setSelRuns, run.id);
      return;
    }
    navigate(`/routines/${run.id}`);
  }

  async function bulkDeleteTemplates() {
    if (selTemplates.size === 0) return;
    if (!window.confirm(i18n.t('routines.confirm.bulkDelete', { n: selTemplates.size }))) return;
    setError(null);
    try {
      await apiPost('/routines/templates/bulk-delete', { ids: Array.from(selTemplates) });
      setSelTemplates(new Set());
      setDeleteMode(false);
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : i18n.t('routines.err.delete'));
    }
  }

  async function bulkReassignRuns() {
    if (selRuns.size === 0 || !reassignUser) return;
    setError(null);
    setNotice(null);
    try {
      const res = await apiPost('/routines/runs/bulk-reassign', { ids: Array.from(selRuns), assignedUserId: reassignUser });
      const target = users.find((u) => u.id === reassignUser);
      const done = res?.reassigned ?? 0;
      const skipped = res?.skipped ?? 0;
      setNotice(
        i18n.t('routines.reassigned', { done, to: target ? i18n.t('routines.reassigned.to', { name: target.fullName }) : '' }) +
          (skipped ? i18n.t('routines.reassigned.skipped', { n: skipped }) : '') + '.'
      );
      setSelRuns(new Set());
      setReassignUser('');
      setReassignMode(false);
      await loadRuns(runStatus);
    } catch (e) {
      setError(e instanceof Error ? e.message : i18n.t('routines.err.reassign'));
    }
  }

  function targetSummary(t: Template): string {
    if (t.targetType === 'USER') {
      const u = users.find((x) => x.id === t.targetUserId);
      return u ? u.fullName : i18n.t('target.USER');
    }
    if (t.targetType === 'SECTOR') {
      const s = sectors.find((x) => x.id === t.targetSectorId);
      return s ? `${i18n.t('target.SECTOR')}: ${s.name}` : i18n.t('target.SECTOR');
    }
    return i18n.t('target.' + t.targetType);
  }

  function scheduleSummary(t: Template): string {
    const rec = i18n.t('rec.' + t.recurrenceRule);
    let extra = '';
    if (t.recurrenceRule === 'CUSTOM' && t.customDays) {
      extra = ` · ${i18n.t('sched.days', { days: t.customDays })}`;
    } else if (t.recurrenceRule === 'MONTHLY' && t.dayOfMonth != null) {
      extra = ` · ${i18n.t('sched.day', { n: t.dayOfMonth })}`;
    }
    if (t.businessDaysOnly) {
      extra += ` · ${i18n.t('sched.businessDayNext')}`;
    }
    const window = t.startTime && t.dueTime ? ` · ${t.startTime.slice(0, 5)}–${t.dueTime.slice(0, 5)}` : '';
    const reminder = t.reminderBeforeMinutes != null ? ` · ${i18n.t('sched.reminderBefore', { n: t.reminderBeforeMinutes })}` : '';
    return `${rec}${extra}${window} · ${targetSummary(t)}${reminder}`;
  }

  const visibleRuns = search.trim()
    ? runs.filter((run) => {
        const t = templates.find((x) => x.id === run.templateId);
        return (t?.title ?? '').toLowerCase().includes(search.trim().toLowerCase());
      })
    : runs;

  return (
    <div className="page">
      {error && <div className="alert-error">{error}</div>}

      <section className="card">
        <details>
          <summary className="section-summary">{i18n.t('routines.newTask')}</summary>
        <form onSubmit={createTask} className="stack">
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder={i18n.t('routines.title.placeholder')} required />
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder={i18n.t('routines.desc.placeholder')} />

          <label className="field-label">{i18n.t('routines.recurrence')}</label>
          <select value={recurrence} onChange={(e) => setRecurrence(e.target.value)}>
            <option value="ONCE">{i18n.t('rec.ONCE')}</option>
            <option value="DAILY">{i18n.t('rec.DAILY')}</option>
            <option value="WEEKLY">{i18n.t('rec.WEEKLY')}</option>
            <option value="MONTHLY">{i18n.t('rec.MONTHLY')}</option>
            <option value="CUSTOM">{i18n.t('rec.CUSTOM')}</option>
          </select>

          <label className="field-label">{i18n.t('routines.forWhom')}</label>
          <select value={targetType} onChange={(e) => setTargetType(e.target.value)}>
            <option value="MANAGERS">{i18n.t('routines.target.managers')}</option>
            <option value="ALL">{i18n.t('routines.target.all')}</option>
            <option value="SECTOR">{i18n.t('routines.target.sector')}</option>
            <option value="USER">{i18n.t('routines.target.user')}</option>
          </select>

          {canPickBranch && (
            <>
              <label className="field-label">{i18n.t('routines.branch')}</label>
              <select
                value={branchId}
                onChange={(e) => {
                  setBranchId(e.target.value === '' ? '' : Number(e.target.value));
                  setUserId('');
                  setSectorId('');
                }}
                required
              >
                <option value="">{i18n.t('routines.selectBranch')}</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
            </>
          )}

          {targetType === 'SECTOR' && (
            <select value={sectorId} onChange={(e) => setSectorId(e.target.value === '' ? '' : Number(e.target.value))} required>
              <option value="">{i18n.t('routines.selectSector')}</option>
              {sectorsForBranch.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          )}
          {targetType === 'USER' && (
            <select value={userId} onChange={(e) => setUserId(e.target.value)} required>
              <option value="">{i18n.t('routines.selectUser')}</option>
              {usersForBranch
                .filter((u) => u.role !== 'MASTER')
                .map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName} ({u.role === 'OWNER' ? i18n.t('user.owner') : u.role === 'MANAGER' ? i18n.t('user.manager') : u.role === 'OPERATOR' ? i18n.t('user.operator') : u.role})
                  </option>
                ))}
            </select>
          )}

          {recurrence === 'WEEKLY' && (
            <>
              <label className="field-label">{i18n.t('routines.weekday')}</label>
              <select value={weekday} onChange={(e) => setWeekday(Number(e.target.value))}>
                <option value={1}>{i18n.t('wd.1')}</option>
                <option value={2}>{i18n.t('wd.2')}</option>
                <option value={3}>{i18n.t('wd.3')}</option>
                <option value={4}>{i18n.t('wd.4')}</option>
                <option value={5}>{i18n.t('wd.5')}</option>
                <option value={6}>{i18n.t('wd.6')}</option>
                <option value={7}>{i18n.t('wd.7')}</option>
              </select>
            </>
          )}
          {recurrence === 'MONTHLY' && (
            <>
              <label className="field-label">{i18n.t('routines.dayOfMonth')}</label>
              <input type="number" min={1} max={31} value={dayOfMonth} onChange={(e) => setDayOfMonth(Number(e.target.value))} />
            </>
          )}
          {recurrence === 'CUSTOM' && (
            <>
              <label className="field-label">{i18n.t('routines.daysOfMonth')}</label>
              <p className="muted small">{i18n.t('routines.daysOfMonth.hint')}</p>
              <div className="day-calendar" role="group" aria-label={i18n.t('routines.daysOfMonth')}>
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
                <p className="muted small">{i18n.t('routines.selected', { days: customDays.join(', ') })}</p>
              )}
            </>
          )}
          {recurrence === 'ONCE' && (
            <>
              <label className="field-label">{i18n.t('routines.date')}</label>
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
              {i18n.t('routines.businessDaysOnly')}
            </label>
          )}
          {(recurrence === 'MONTHLY' || recurrence === 'CUSTOM') && businessDaysOnly && (
            <p className="muted small">
              {i18n.t('routines.businessDaysOnly.hint')}
            </p>
          )}

          <div className="time-row">
            <div className="field-block">
              <label className="field-label">{i18n.t('routines.startTime')}</label>
              <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
            </div>
            <div className="field-block">
              <label className="field-label">{i18n.t('routines.dueTime')}</label>
              <input type="time" value={dueTime} onChange={(e) => setDueTime(e.target.value)} required />
            </div>
          </div>

          <label className="field-label">{i18n.t('routines.reminderMinutes')}</label>
          <input type="number" min={0} max={1440} value={reminderMinutes} onChange={(e) => setReminderMinutes(Number(e.target.value))} />

          <label className="check">
            <input type="checkbox" checked={requiresPhoto} onChange={(e) => setRequiresPhoto(e.target.checked)} />
            {i18n.t('routines.requiresPhoto')}
          </label>
          <label className="check">
            <input type="checkbox" checked={requiresComment} onChange={(e) => setRequiresComment(e.target.checked)} />
            {i18n.t('routines.requiresComment')}
          </label>
          {checklistsEnabled && (
            <div className="stack">
              <label className="field-label">{i18n.t('routines.checklist')}</label>
              <div className="row-between">
                <input
                  value={checklistDraft}
                  onChange={(e) => setChecklistDraft(e.target.value)}
                  placeholder={i18n.t('routines.checklist.placeholder')}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addChecklistItem();
                    }
                  }}
                />
                <button type="button" className="btn-ghost" onClick={addChecklistItem}>{i18n.t('common.add')}</button>
              </div>
              {checklistItems.length > 0 && (
                <ul className="list">
                  {checklistItems.map((it, idx) => (
                    <li key={idx}>
                      <span>{it.label}</span>
                      <span className="actions">
                        <label className="check">
                          <input type="checkbox" checked={it.required} onChange={() => toggleItemRequired(idx)} />
                          {i18n.t('routines.required')}
                        </label>
                        <button type="button" className="btn-ghost danger" onClick={() => removeChecklistItem(idx)}>×</button>
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
          <button type="submit" className="btn-primary">{i18n.t('routines.create')}</button>
        </form>
        </details>
      </section>

      <section className="card">
        <details>
          <summary className="section-summary">{i18n.t('routines.scheduled', { n: templates.length })}</summary>
        {canDelete && templates.length > 0 && (
          <div className="row-between">
            <span className="muted small">{deleteMode ? i18n.t('routines.deleteMode.hint') : ''}</span>
            <button type="button" className={`btn-ghost ${deleteMode ? 'active' : ''}`} onClick={toggleDeleteMode}>
              {deleteMode ? i18n.t('common.cancel') : i18n.t('routines.deleteMany')}
            </button>
          </div>
        )}
        {deleteMode && selTemplates.size > 0 && (
          <div className="action-panel">
            <span className="muted small">{i18n.t('routines.selectedCount', { n: selTemplates.size })}</span>
            <button type="button" className="btn-primary" onClick={bulkDeleteTemplates}>{i18n.t('routines.deleteSelected')}</button>
          </div>
        )}
        {templates.length === 0 ? (
          <p className="muted">{i18n.t('routines.noScheduled')}</p>
        ) : (
          <ul className="list">
            {templates.map((t) => (
              <li key={t.id}>
                {deleteMode && canDelete && (
                  <input
                    type="checkbox"
                    checked={selTemplates.has(t.id)}
                    onChange={() => toggleSet(selTemplates, setSelTemplates, t.id)}
                  />
                )}
                <div className="item-main">
                  <strong>{t.title}</strong>
                  <div className="muted small">{scheduleSummary(t)}</div>
                  {(t.requiresPhoto || t.requiresComment) && (
                    <div className="muted small">
                      {t.requiresPhoto ? i18n.t('routines.photo') : ''}
                      {t.requiresPhoto && t.requiresComment ? ' · ' : ''}
                      {t.requiresComment ? i18n.t('routines.comment') : ''}
                    </div>
                  )}
                </div>
                {!deleteMode && (
                  <div className="actions">
                    <button className="btn-ghost" onClick={() => generateNow(t.id)}>{i18n.t('routines.generateNow')}</button>
                    {canDelete && <button className="btn-ghost danger" onClick={() => removeTemplate(t.id)}>{i18n.t('routines.remove')}</button>}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
        </details>
      </section>

      <section className="card">
        <div className="row-between">
          <h2>{i18n.t('routines.tasks')}</h2>
          <span className="actions">
            <button type="button" className="btn-ghost" onClick={() => navigate('/calendar')}>📅 {i18n.t('routines.calendar')}</button>
            <button type="button" className="btn-ghost" onClick={exportCsv}>{i18n.t('routines.exportCsv')}</button>
            {canReassign && (
              <button type="button" className={`btn-ghost ${reassignMode ? 'active' : ''}`} onClick={toggleReassignMode}>
                {reassignMode ? i18n.t('common.cancel') : i18n.t('routines.reassign')}
              </button>
            )}
          </span>
        </div>
        {notice && <div className="alert-ok">{notice}</div>}
        {reassignMode && (
          <div className="action-panel">
            <p className="muted small">
              {i18n.t('routines.reassign.hint')}
            </p>
            <select value={reassignUser} onChange={(e) => setReassignUser(e.target.value)}>
              <option value="">{i18n.t('routines.transferTo')}</option>
              {users.filter((u) => u.role !== 'MASTER').map((u) => {
                const bName = u.branchId != null ? branches.find((b) => b.id === u.branchId)?.name : null;
                return (
                  <option key={u.id} value={u.id}>
                    {u.fullName} · {roleLabel(u.role)}{bName ? ` · ${bName}` : ''}
                  </option>
                );
              })}
            </select>
            <button
              type="button"
              className="btn-primary"
              disabled={!reassignUser || selRuns.size === 0}
              onClick={bulkReassignRuns}
            >
              {i18n.t('routines.reassign')} {selRuns.size > 0 ? `(${selRuns.size})` : ''}
            </button>
          </div>
        )}
        <div className="filter-row">
          {RUN_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              className={`filter-chip ${runStatus === f.value ? 'active' : ''}`}
              onClick={() => setRunStatus(f.value)}
            >
              {i18n.t(f.key)}
            </button>
          ))}
        </div>
        <input
          className="search-input"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={i18n.t('routines.search.placeholder')}
        />
        {visibleRuns.length === 0 ? (
          <p className="muted">{i18n.t('routines.noTasks')}</p>
        ) : (
          <ul className="list">
            {visibleRuns.map((run) => {
              const template = templates.find((t) => t.id === run.templateId);
              const eligible = REASSIGNABLE.has(run.status);
              return (
                <li key={run.id} className={reassignMode && !eligible ? 'row-muted' : ''}>
                  {reassignMode && (eligible ? (
                    <input
                      type="checkbox"
                      checked={selRuns.has(run.id)}
                      onChange={() => toggleSet(selRuns, setSelRuns, run.id)}
                    />
                  ) : (
                    <span className="cb-spacer" aria-hidden="true" />
                  ))}
                  <div
                    className={!reassignMode || eligible ? 'clickable item-main' : 'item-main'}
                    onClick={() => onRunRow(run)}
                  >
                    <strong>{template?.title ?? i18n.t('routines.taskNum', { id: run.id })}</strong>
                    <div className="muted small">{i18n.t('routines.due')} {run.dueAt ? new Date(run.dueAt).toLocaleString() : '—'}</div>
                    {reassignMode && !eligible && (
                      <div className="muted small">{i18n.t('routines.notReassignable', { status: i18n.t('status.' + run.status) })}</div>
                    )}
                  </div>
                  <span className={`chip status-${run.status.toLowerCase()}`}>{i18n.t('status.' + run.status)}</span>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
