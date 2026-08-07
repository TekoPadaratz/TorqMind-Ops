import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiGet, apiPost, apiUpload } from '../api';
import { Thread } from '../components/Thread';

const ROUTINE_ACTIONS: Record<string, Array<{ label: string; status: string }>> = {
  PENDENTE: [
    { label: 'Iniciar', status: 'EM_ANDAMENTO' },
    { label: 'Rejeitar', status: 'REJEITADA' }
  ],
  EM_ANDAMENTO: [
    { label: 'Concluir', status: 'CONCLUIDA' },
    { label: 'Rejeitar', status: 'REJEITADA' }
  ],
  ATRASADA: [
    { label: 'Iniciar', status: 'EM_ANDAMENTO' },
    { label: 'Concluir', status: 'CONCLUIDA' }
  ]
};

export default function RoutineDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function reload() {
    try {
      setDetail(await apiGet(`/routines/runs/${id}`));
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
      await apiPost(`/routines/runs/${id}/transition`, { status });
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
      await apiPost(`/routines/runs/${id}/comments`, { body: text });
      await reload();
    } finally {
      setBusy(false);
    }
  }
  async function onUpload(file: File) {
    setError(null);
    setBusy(true);
    try {
      await apiUpload(`/routines/runs/${id}/attachments`, file);
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
  const actions = ROUTINE_ACTIONS[s.status] || [];

  return (
    <div className="page">
      <button className="btn-ghost back" onClick={() => navigate(-1)}>← Voltar</button>
      {error && <div className="alert-error">{error}</div>}

      <section className="card">
        <div className="detail-head">
          <h2>{s.title}</h2>
          <span className={`chip status-${String(s.status).toLowerCase()}`}>{s.status}</span>
        </div>
        {s.description && <p className="muted">{s.description}</p>}
        <div className="detail-meta">
          <Meta label="Responsável" value={s.assignee?.name ?? 'Não atribuído'} />
          <Meta label="Prazo" value={s.dueAt ? new Date(s.dueAt).toLocaleString() : '—'} />
          <Meta label="Iniciada" value={s.startedAt ? new Date(s.startedAt).toLocaleString() : '—'} />
          <Meta label="Concluída" value={s.completedAt ? new Date(s.completedAt).toLocaleString() : '—'} />
        </div>
        <div className="requirements">
          {s.requiresPhoto && <span className="chip req">Exige foto</span>}
          {s.requiresComment && <span className="chip req">Exige comentário</span>}
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
