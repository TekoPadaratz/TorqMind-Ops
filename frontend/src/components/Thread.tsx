import React, { useRef, useState } from 'react';
import { AuthImage, openAttachment } from './AuthMedia';

export type UserRef = { id: string; name: string };
export type Comment = { id: number; author: UserRef | null; body: string; createdAt: string };
export type Attachment = {
  id: number;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  url: string;
  uploadedBy: UserRef | null;
  createdAt: string;
};
export type Activity = {
  id: number;
  actor: UserRef | null;
  type: string;
  fromStatus: string | null;
  toStatus: string | null;
  message: string | null;
  createdAt: string;
};

type TimelineEntry =
  | { kind: 'comment'; at: string; data: Comment }
  | { kind: 'activity'; at: string; data: Activity };

function activityLabel(a: Activity): string {
  const who = a.actor?.name ?? 'Alguém';
  switch (a.type) {
    case 'CREATED':
      return `${who} criou a tarefa`;
    case 'STATUS_CHANGED':
      return `${who} alterou o status: ${a.fromStatus ?? '—'} → ${a.toStatus ?? '—'}`;
    case 'ATTACHMENT':
      return `${who} anexou ${a.message ?? 'um arquivo'}`;
    case 'COMMENT':
      return `${who} comentou`;
    default:
      return `${who}: ${a.type}`;
  }
}

export function Thread({
  comments,
  attachments,
  activities,
  onComment,
  onUpload,
  busy
}: {
  comments: Comment[];
  attachments: Attachment[];
  activities: Activity[];
  onComment: (text: string) => Promise<void>;
  onUpload: (file: File) => Promise<void>;
  busy: boolean;
}) {
  const [text, setText] = useState('');
  const [preview, setPreview] = useState<string | null>(null);
  const cameraRef = useRef<HTMLInputElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const timeline: TimelineEntry[] = [
    ...comments.map((c) => ({ kind: 'comment' as const, at: c.createdAt, data: c })),
    ...activities
      .filter((a) => a.type !== 'COMMENT')
      .map((a) => ({ kind: 'activity' as const, at: a.createdAt, data: a }))
  ].sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime());

  const images = attachments.filter((a) => a.mimeType.startsWith('image/'));
  const docs = attachments.filter((a) => !a.mimeType.startsWith('image/'));

  return (
    <>
      <section className="card">
        <h2>Evidências</h2>
        {attachments.length === 0 ? (
          <p className="muted">Nenhum anexo ainda.</p>
        ) : (
          <>
            {images.length > 0 && (
              <div className="gallery">
                {images.map((a) => (
                  <button
                    key={a.id}
                    type="button"
                    className="thumb"
                    onClick={() => setPreview(a.url)}
                    title={a.fileName}
                  >
                    <AuthImage url={a.url} alt={a.fileName} />
                  </button>
                ))}
              </div>
            )}
            {docs.length > 0 && (
              <ul className="list">
                {docs.map((a) => (
                  <li key={a.id}>
                    <button type="button" className="linklike" onClick={() => openAttachment(a.url)}>
                      {a.fileName}
                    </button>
                    <span className="muted small">{Math.round(a.sizeBytes / 1024)} KB</span>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}

        <div className="upload-row">
          <button type="button" className="btn-ghost" disabled={busy} onClick={() => cameraRef.current?.click()}>
            📷 Tirar foto
          </button>
          <button type="button" className="btn-ghost" disabled={busy} onClick={() => fileRef.current?.click()}>
            📎 Anexar arquivo
          </button>
          <input
            ref={cameraRef}
            type="file"
            accept="image/*"
            capture="environment"
            hidden
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) onUpload(f);
              e.target.value = '';
            }}
          />
          <input
            ref={fileRef}
            type="file"
            accept="image/*,application/pdf"
            hidden
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) onUpload(f);
              e.target.value = '';
            }}
          />
        </div>
      </section>

      <section className="card">
        <h2>Histórico</h2>
        {timeline.length === 0 ? (
          <p className="muted">Sem atividade ainda.</p>
        ) : (
          <ul className="timeline">
            {timeline.map((entry) =>
              entry.kind === 'comment' ? (
                <li key={`c${entry.data.id}`} className="timeline-item comment">
                  <div className="timeline-head">
                    <strong>{entry.data.author?.name ?? 'Usuário'}</strong>
                    <span className="muted small">{new Date(entry.at).toLocaleString()}</span>
                  </div>
                  <div className="timeline-body">{entry.data.body}</div>
                </li>
              ) : (
                <li key={`a${entry.data.id}`} className="timeline-item activity">
                  <div className="timeline-body muted">{activityLabel(entry.data)}</div>
                  <span className="muted small">{new Date(entry.at).toLocaleString()}</span>
                </li>
              )
            )}
          </ul>
        )}

        <form
          className="comment-box"
          onSubmit={async (e) => {
            e.preventDefault();
            if (!text.trim()) return;
            await onComment(text.trim());
            setText('');
          }}
        >
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Escreva um comentário (não altera o status)"
          />
          <button className="btn-primary" type="submit" disabled={busy || !text.trim()}>
            Comentar
          </button>
        </form>
      </section>

      {preview && (
        <div className="lightbox" onClick={() => setPreview(null)} role="dialog" aria-modal="true">
          <button type="button" className="lightbox-close" aria-label="Fechar">×</button>
          <AuthImage url={preview} alt="Anexo" />
        </div>
      )}
    </>
  );
}
