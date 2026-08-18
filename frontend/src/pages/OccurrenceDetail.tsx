import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiGet, apiPost, apiUpload, currentGeo } from '../api';
import { Thread } from '../components/Thread';
import { FuelQualityForm } from './FuelQualityOccurrence';
import { openAttachment } from '../components/AuthMedia';

const OCCURRENCE_ACTIONS: Record<string, Array<{ label: string; status: string }>> = {
  ABERTA: [
    { label: 'Atender', status: 'EM_ATENDIMENTO' },
    { label: 'Rejeitar', status: 'REJEITADA' }
  ],
  EM_ATENDIMENTO: [
    { label: 'Enviar p/ validação', status: 'AGUARDANDO_VALIDACAO' },
    { label: 'Rejeitar', status: 'REJEITADA' }
  ],
  AGUARDANDO_VALIDACAO: [
    { label: 'Encerrar', status: 'ENCERRADA' },
    { label: 'Reabrir', status: 'EM_ATENDIMENTO' },
    { label: 'Rejeitar', status: 'REJEITADA' }
  ]
};

function ToRoutineForm({ occurrenceId }: { occurrenceId: number }) {
  const navigate = useNavigate();
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
      setErr(e instanceof Error ? e.message : 'Falha ao transformar em rotina.');
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <button type="button" className="btn-ghost" onClick={() => setOpen(true)}>
        Transformar em rotina
      </button>
    );
  }
  return (
    <section className="card">
      <h3>Transformar em rotina</h3>
      {err && <div className="alert-error">{err}</div>}
      <label className="field-label">Recorrência
        <select value={recurrence} onChange={(e) => setRecurrence(e.target.value)}>
          <option value="DAILY">Diária</option>
          <option value="WEEKLY">Semanal</option>
          <option value="MONTHLY">Mensal</option>
        </select>
      </label>
      {recurrence === 'WEEKLY' && (
        <label className="field-label">Dia da semana
          <select value={weekday} onChange={(e) => setWeekday(Number(e.target.value))}>
            <option value={1}>Segunda</option>
            <option value={2}>Terça</option>
            <option value={3}>Quarta</option>
            <option value={4}>Quinta</option>
            <option value={5}>Sexta</option>
            <option value={6}>Sábado</option>
            <option value={7}>Domingo</option>
          </select>
        </label>
      )}
      {recurrence === 'MONTHLY' && (
        <label className="field-label">Dia do mês
          <input type="number" min={1} max={31} value={dayOfMonth} onChange={(e) => setDayOfMonth(Number(e.target.value))} />
        </label>
      )}
      <label className="field-label">Início
        <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
      </label>
      <label className="field-label">Vencimento
        <input type="time" value={dueTime} onChange={(e) => setDueTime(e.target.value)} />
      </label>
      <label className="field-label">Lembrete (min antes)
        <input type="number" min={0} max={1440} value={reminder} onChange={(e) => setReminder(Number(e.target.value))} />
      </label>
      <div className="voice-actions">
        <button type="button" className="btn-primary" onClick={submit} disabled={busy}>Criar rotina (gerentes)</button>
        <button type="button" className="btn-ghost" onClick={() => setOpen(false)}>Cancelar</button>
      </div>
    </section>
  );
}

export default function OccurrenceDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function reload() {
    try {
      setDetail(await apiGet(`/occurrences/${id}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar');
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
      setError(e instanceof Error ? e.message : 'Erro na transição');
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
    setBusy(true);
    try {
      const geo = file.type.startsWith('image/') ? await currentGeo() : null;
      await apiUpload(`/occurrences/${id}/attachments`, file, geo);
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha no upload');
    } finally {
      setBusy(false);
    }
  }

  if (error && !detail) return <div className="page"><div className="alert-error">{error}</div></div>;
  if (!detail) return <div className="page muted">Carregando...</div>;

  const s = detail.summary;
  const actions = s.kind === 'FUEL_QUALITY_RECEIPT' ? [] : (OCCURRENCE_ACTIONS[s.status] || []);
  const quality = s.kind === 'FUEL_QUALITY_RECEIPT';

  return (
    <div className="page">
      <button className="btn-ghost back" onClick={() => navigate(-1)}>← Voltar</button>
      {error && <div className="alert-error">{error}</div>}

      {quality ? (
        <FuelQualityForm occurrenceId={Number(id)} />
      ) : (
      <section className="card">
        <div className="detail-head">
          <h2>{s.title}</h2>
          <span className={`chip status-${String(s.status).toLowerCase()}`}>{s.status}</span>
        </div>
        <p className="muted">{s.description}</p>
        <div className="detail-meta">
          <Meta label="Prioridade" value={s.priority} />
          <Meta label="Aberta por" value={s.openedBy?.name ?? '—'} />
          <Meta label="Responsável" value={s.assignee?.name ?? 'Não atribuído'} />
          <Meta label="Aberta em" value={s.createdAt ? new Date(s.createdAt).toLocaleString() : '—'} />
        </div>
        {actions.length > 0 && (
          <div className="actions detail-actions">
            {actions.map((a) => (
              <button key={a.status} className="btn-primary" disabled={busy} onClick={() => transition(a.status)}>
                {a.label}
              </button>
            ))}
          </div>
        )}
      </section>
      )}

      {!quality && <ToRoutineForm occurrenceId={Number(id)} />}

      {!quality && s.documentUrl && (
        <button type="button" className="btn-ghost" onClick={() => openAttachment(s.documentUrl)}>
          Abrir documento PDF
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
