import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiGet, apiPost, apiUpload } from '../api';
import { useAuth } from '../auth';
import { Thread } from '../components/Thread';
import { openAttachment } from '../components/AuthMedia';

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

const STATUS_LABEL: Record<string, string> = {
  PENDENTE: 'Pendente',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  ATRASADA: 'Atrasada',
  REJEITADA: 'Rejeitada'
};

export default function RoutineDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { session } = useAuth();
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
    setError(null);
    setBusy(true);
    try {
      await apiPost(`/routines/runs/${id}/comments`, { body: text });
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao adicionar comentário');
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
  const isAssignedExecutor = !s.assignee || session?.userId === s.assignee.id;
  const hasPhoto = detail.attachments.some((attachment: any) =>
    String(attachment.mimeType).startsWith('image/')
      && (!s.assignee || attachment.uploadedBy?.id === s.assignee.id)
  );
  const hasComment = detail.comments.some((comment: any) =>
    !s.assignee || comment.author?.id === s.assignee.id
  ) || Boolean(s.executionComment?.trim());
  const missingRequirements = [
    s.requiresPhoto && !hasPhoto ? 'uma foto' : null,
    s.requiresComment && !hasComment ? 'um comentário' : null
  ].filter(Boolean);
  const canComplete = missingRequirements.length === 0;

  return (
    <div className="page">
      <button className="btn-ghost back" onClick={() => navigate(-1)}>← Voltar</button>
      {error && <div className="alert-error">{error}</div>}

      <section className="card">
        <div className="detail-head">
          <h2>{s.title}</h2>
          <span className={`chip status-${String(s.status).toLowerCase()}`}>
            {STATUS_LABEL[s.status] ?? s.status}
          </span>
        </div>
        {s.description && <p className="muted">{s.description}</p>}
        <div className="detail-meta">
          <Meta label="Responsável" value={s.assignee?.name ?? 'Não atribuído'} />
          <Meta label="Prazo" value={s.dueAt ? new Date(s.dueAt).toLocaleString() : '—'} />
          <Meta label="Iniciada" value={s.startedAt ? new Date(s.startedAt).toLocaleString() : '—'} />
          <Meta label="Concluída" value={s.completedAt ? new Date(s.completedAt).toLocaleString() : '—'} />
        </div>
        <div className="requirements">
          {s.requiresPhoto && <span className="chip req">{hasPhoto ? 'Foto anexada' : 'Foto pendente'}</span>}
          {s.requiresComment && <span className="chip req">{hasComment ? 'Comentário registrado' : 'Comentário pendente'}</span>}
        </div>
        {!canComplete && (
          <p className="muted small">Para concluir, registre {missingRequirements.join(' e ')}.</p>
        )}
        {!isAssignedExecutor && actions.some((action) => action.status === 'EM_ANDAMENTO' || action.status === 'CONCLUIDA') && (
          <p className="muted small">Somente o responsável pode iniciar ou concluir esta tarefa.</p>
        )}
        <div className="actions detail-actions">
          <button type="button" className="btn-ghost" onClick={() => openAttachment(`/routines/runs/${id}/report`)}>
            Baixar comprovante PDF
          </button>
        </div>
        {actions.length > 0 && (
          <div className="actions detail-actions">
            {actions.map((a) => (
              <button
                key={a.status}
                className="btn-primary"
                disabled={busy
                  || ((a.status === 'EM_ANDAMENTO' || a.status === 'CONCLUIDA') && !isAssignedExecutor)
                  || (a.status === 'CONCLUIDA' && !canComplete)}
                title={a.status === 'CONCLUIDA' && !canComplete ? `Falta ${missingRequirements.join(' e ')}` : undefined}
                onClick={() => transition(a.status)}
              >
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
