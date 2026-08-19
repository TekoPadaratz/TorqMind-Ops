import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiGet, apiPost, uploadOrQueue, currentGeo } from '../api';
import { useAuth } from '../auth';
import { useI18n } from '../i18n';
import { Thread } from '../components/Thread';
import { openAttachment } from '../components/AuthMedia';

const ROUTINE_ACTIONS: Record<string, Array<{ labelKey: string; status: string }>> = {
  PENDENTE: [
    { labelKey: 'rdetail.start', status: 'EM_ANDAMENTO' },
    { labelKey: 'rdetail.reject', status: 'REJEITADA' }
  ],
  EM_ANDAMENTO: [
    { labelKey: 'rdetail.complete', status: 'CONCLUIDA' },
    { labelKey: 'rdetail.reject', status: 'REJEITADA' }
  ],
  ATRASADA: [
    { labelKey: 'rdetail.start', status: 'EM_ANDAMENTO' },
    { labelKey: 'rdetail.complete', status: 'CONCLUIDA' }
  ]
};

export default function RoutineDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { session } = useAuth();
  const { t } = useI18n();
  const [detail, setDetail] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [checklist, setChecklist] = useState<Array<{ id: number; label: string; required: boolean; checked: boolean }>>([]);

  async function reload() {
    try {
      setDetail(await apiGet(`/routines/runs/${id}`));
      apiGet(`/routines/runs/${id}/checklist`).then(setChecklist).catch(() => setChecklist([]));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('common.loadError'));
    }
  }
  useEffect(() => {
    reload();
  }, [id]);

  async function toggleCheck(itemId: number, checked: boolean) {
    setError(null);
    try {
      await apiPost(`/routines/runs/${id}/checklist/${itemId}`, { checked });
      setChecklist(await apiGet(`/routines/runs/${id}/checklist`));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('rdetail.err.checklist'));
    }
  }

  async function transition(status: string) {
    setError(null);
    setBusy(true);
    try {
      await apiPost(`/routines/runs/${id}/transition`, { status });
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : t('rdetail.err.transition'));
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
      setError(e instanceof Error ? e.message : t('rdetail.err.comment'));
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
      const r = await uploadOrQueue(`/routines/runs/${id}/attachments`, file, geo);
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
    s.requiresPhoto && !hasPhoto ? t('rdetail.aPhoto') : null,
    s.requiresComment && !hasComment ? t('rdetail.aComment') : null
  ].filter(Boolean);
  const canComplete = missingRequirements.length === 0;

  return (
    <div className="page">
      <button className="btn-ghost back" onClick={() => navigate(-1)}>{t('common.back')}</button>
      {error && <div className="alert-error">{error}</div>}
      {notice && <div className="alert-ok">{notice}</div>}

      <section className="card">
        <div className="detail-head">
          <h2>{s.title}</h2>
          <span className={`chip status-${String(s.status).toLowerCase()}`}>
            {t('status.' + s.status)}
          </span>
        </div>
        {s.description && <p className="muted">{s.description}</p>}
        <div className="detail-meta">
          <Meta label={t('rdetail.assignee')} value={s.assignee?.name ?? t('rdetail.unassigned')} />
          <Meta label={t('rdetail.dueDate')} value={s.dueAt ? new Date(s.dueAt).toLocaleString() : '—'} />
          <Meta label={t('rdetail.startedAt')} value={s.startedAt ? new Date(s.startedAt).toLocaleString() : '—'} />
          <Meta label={t('rdetail.completedAt')} value={s.completedAt ? new Date(s.completedAt).toLocaleString() : '—'} />
        </div>
        <div className="requirements">
          {s.requiresPhoto && <span className="chip req">{hasPhoto ? t('rdetail.photoAttached') : t('rdetail.photoPending')}</span>}
          {s.requiresComment && <span className="chip req">{hasComment ? t('rdetail.commentDone') : t('rdetail.commentPending')}</span>}
        </div>
        {!canComplete && (
          <p className="muted small">{t('rdetail.toComplete', { items: missingRequirements.join(t('common.and')) })}</p>
        )}
        {!isAssignedExecutor && actions.some((action) => action.status === 'EM_ANDAMENTO' || action.status === 'CONCLUIDA') && (
          <p className="muted small">{t('rdetail.onlyAssignee')}</p>
        )}
        <div className="actions detail-actions">
          <button type="button" className="btn-ghost" onClick={() => openAttachment(`/routines/runs/${id}/report`)}>
            {t('rdetail.downloadPdf')}
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
                title={a.status === 'CONCLUIDA' && !canComplete ? t('rdetail.missing', { items: missingRequirements.join(t('common.and')) }) : undefined}
                onClick={() => transition(a.status)}
              >
                {t(a.labelKey)}
              </button>
            ))}
          </div>
        )}
      </section>

      {checklist.length > 0 && (
        <section className="card">
          <h2>{t('rdetail.checklist')}</h2>
          {checklist.filter((c) => c.required && !c.checked).length > 0 ? (
            <p className="muted small">
              {t('rdetail.checklistPending', { n: checklist.filter((c) => c.required && !c.checked).length })}
            </p>
          ) : (
            <p className="muted small">{t('rdetail.checklistDone')}</p>
          )}
          <ul className="list">
            {checklist.map((c) => (
              <li key={c.id}>
                <label className="check">
                  <input
                    type="checkbox"
                    checked={c.checked}
                    disabled={busy || !isAssignedExecutor || s.status === 'CONCLUIDA' || s.status === 'REJEITADA'}
                    onChange={(e) => toggleCheck(c.id, e.target.checked)}
                  />
                  {c.label}
                  {!c.required && <span className="muted small"> ({t('common.optional')})</span>}
                </label>
              </li>
            ))}
          </ul>
        </section>
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
