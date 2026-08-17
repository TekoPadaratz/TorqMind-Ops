import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiGet, apiPost, apiUpload } from '../api';
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
      await apiUpload(`/occurrences/${id}/attachments`, file);
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
