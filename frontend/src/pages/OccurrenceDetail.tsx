import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiGet, apiPost, uploadOrQueue, currentGeo } from '../api';
import { useI18n } from '../i18n';
import { Thread } from '../components/Thread';
import { FuelQualityForm } from './FuelQualityOccurrence';
import { openAttachment } from '../components/AuthMedia';

const OCCURRENCE_ACTIONS: Record<string, Array<{ labelKey: string; status: string }>> = {
  ABERTA: [
    { labelKey: 'odetail.attend', status: 'EM_ATENDIMENTO' },
    { labelKey: 'odetail.reject', status: 'REJEITADA' }
  ],
  EM_ATENDIMENTO: [
    { labelKey: 'odetail.sendValidation', status: 'AGUARDANDO_VALIDACAO' },
    { labelKey: 'odetail.reject', status: 'REJEITADA' }
  ],
  AGUARDANDO_VALIDACAO: [
    { labelKey: 'odetail.close', status: 'ENCERRADA' },
    { labelKey: 'odetail.reopen', status: 'EM_ATENDIMENTO' },
    { labelKey: 'odetail.reject', status: 'REJEITADA' }
  ]
};

function ToRoutineForm({ occurrenceId }: { occurrenceId: number }) {
  const navigate = useNavigate();
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [recurrence, setRecurrence] = useState('MONTHLY');
  const [startTime, setStartTime] = useState('08:00');
  const [dueTime, setDueTime] = useState('17:00');
  const [weekday, setWeekday] = useState(1);
  const [dayOfMonth, setDayOfMonth] = useState(1);
  const [reminder, setReminder] = useState(15);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function submit() {
    setErr(null);
    setBusy(true);
    try {
      await apiPost(`/occurrences/${occurrenceId}/to-routine`, {
        recurrence,
        targetType: 'MANAGERS',
        startTime,
        dueTime,
        weekday: recurrence === 'WEEKLY' ? weekday : null,
        dayOfMonth: recurrence === 'MONTHLY' ? dayOfMonth : null,
        reminderBeforeMinutes: reminder,
        requiresPhoto: true,
        requiresComment: true
      });
      navigate('/routines');
    } catch (e) {
      setErr(e instanceof Error ? e.message : t('odetail.err.toRoutine'));
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <button type="button" className="btn-ghost" onClick={() => setOpen(true)}>
        {t('odetail.toRoutine')}
      </button>
    );
  }
  return (
    <section className="card">
      <h3>{t('odetail.toRoutine')}</h3>
      {err && <div className="alert-error">{err}</div>}
      <label className="field-label">{t('routines.recurrence')}
        <select value={recurrence} onChange={(e) => setRecurrence(e.target.value)}>
          <option value="DAILY">{t('rec.DAILY')}</option>
          <option value="WEEKLY">{t('rec.WEEKLY')}</option>
          <option value="MONTHLY">{t('rec.MONTHLY')}</option>
        </select>
      </label>
      {recurrence === 'WEEKLY' && (
        <label className="field-label">{t('routines.weekday')}
          <select value={weekday} onChange={(e) => setWeekday(Number(e.target.value))}>
            <option value={1}>{t('wd.1')}</option>
            <option value={2}>{t('wd.2')}</option>
            <option value={3}>{t('wd.3')}</option>
            <option value={4}>{t('wd.4')}</option>
            <option value={5}>{t('wd.5')}</option>
            <option value={6}>{t('wd.6')}</option>
            <option value={7}>{t('wd.7')}</option>
          </select>
        </label>
      )}
      {recurrence === 'MONTHLY' && (
        <label className="field-label">{t('routines.dayOfMonth')}
          <input type="number" min={1} max={31} value={dayOfMonth} onChange={(e) => setDayOfMonth(Number(e.target.value))} />
        </label>
      )}
      <label className="field-label">{t('odetail.startLabel')}
        <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
      </label>
      <label className="field-label">{t('odetail.dueLabel')}
        <input type="time" value={dueTime} onChange={(e) => setDueTime(e.target.value)} />
      </label>
      <label className="field-label">{t('odetail.reminderLabel')}
        <input type="number" min={0} max={1440} value={reminder} onChange={(e) => setReminder(Number(e.target.value))} />
      </label>
      <div className="voice-actions">
        <button type="button" className="btn-primary" onClick={submit} disabled={busy}>{t('odetail.createRoutine')}</button>
        <button type="button" className="btn-ghost" onClick={() => setOpen(false)}>{t('common.cancel')}</button>
      </div>
    </section>
  );
}

export default function OccurrenceDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { t } = useI18n();
  const [detail, setDetail] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function reload() {
    try {
      setDetail(await apiGet(`/occurrences/${id}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('common.loadError'));
    }
  }
  useEffect(() => {
    reload();
  }, [id]);

  async function transition(status: string) {
    setError(null);
    setBusy(true);
    try {
      await apiPost(`/occurrences/${id}/transition`, { status });
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : t('rdetail.err.transition'));
    } finally {
      setBusy(false);
    }
  }
  async function onComment(text: string) {
    setBusy(true);
    try {
      await apiPost(`/occurrences/${id}/comments`, { body: text });
      await reload();
    } finally {
      setBusy(false);
    }
  }
  async function onUpload(file: File) {
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      const geo = file.type.startsWith('image/') ? await currentGeo() : null;
      const r = await uploadOrQueue(`/occurrences/${id}/attachments`, file, geo);
      if (r.queued) {
        setNotice(t('rdetail.offlineQueued'));
      } else {
        await reload();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : t('rdetail.err.upload'));
    } finally {
      setBusy(false);
    }
  }

  if (error && !detail) return <div className="page"><div className="alert-error">{error}</div></div>;
  if (!detail) return <div className="page muted">{t('common.loading')}</div>;

  const s = detail.summary;
  const actions = s.kind === 'FUEL_QUALITY_RECEIPT' ? [] : (OCCURRENCE_ACTIONS[s.status] || []);
  const quality = s.kind === 'FUEL_QUALITY_RECEIPT';

  return (
    <div className="page">
      <button className="btn-ghost back" onClick={() => navigate(-1)}>{t('common.back')}</button>
      {error && <div className="alert-error">{error}</div>}
      {notice && <div className="alert-ok">{notice}</div>}

      {quality ? (
        <FuelQualityForm occurrenceId={Number(id)} />
      ) : (
      <section className="card">
        <div className="detail-head">
          <h2>{s.title}</h2>
          <span className={`chip status-${String(s.status).toLowerCase()}`}>{t('ostatus.' + s.status)}</span>
        </div>
        <p className="muted">{s.description}</p>
        <div className="detail-meta">
          <Meta label={t('odetail.priority')} value={t('prio.' + s.priority)} />
          <Meta label={t('odetail.openedBy')} value={s.openedBy?.name ?? '—'} />
          <Meta label={t('rdetail.assignee')} value={s.assignee?.name ?? t('rdetail.unassigned')} />
          <Meta label={t('odetail.openedAt')} value={s.createdAt ? new Date(s.createdAt).toLocaleString() : '—'} />
        </div>
        {actions.length > 0 && (
          <div className="actions detail-actions">
            {actions.map((a) => (
              <button key={a.status} className="btn-primary" disabled={busy} onClick={() => transition(a.status)}>
                {t(a.labelKey)}
              </button>
            ))}
          </div>
        )}
      </section>
      )}

      {!quality && <ToRoutineForm occurrenceId={Number(id)} />}

      {!quality && s.documentUrl && (
        <button type="button" className="btn-ghost" onClick={() => openAttachment(s.documentUrl)}>
          {t('odetail.openPdf')}
        </button>
      )}

      <Thread
        comments={detail.comments}
        attachments={detail.attachments}
        activities={detail.activities}
        onComment={onComment}
        onUpload={onUpload}
        busy={busy}
      />
    </div>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className="meta-item">
      <span className="muted small">{label}</span>
      <span>{value}</span>
    </div>
  );
}
