import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { apiDelete, apiGet, apiPatch, apiPost, apiPostIdempotent, apiUpload, apiUploadForm } from '../api';
import { idempotencyKeyFor, taskContextFromPath, VoiceDraft, VoiceStatus, VoiceUiState } from '../voice/voice';

type Props = { open: boolean; onClose: () => void };

export default function VoiceSheet({ open, onClose }: Props) {
  const location = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = useState<VoiceStatus | null>(null);
  const [ui, setUi] = useState<VoiceUiState>('idle');
  const [message, setMessage] = useState('');
  const [seconds, setSeconds] = useState(0);
  const [transcript, setTranscript] = useState('');
  const [draft, setDraft] = useState<VoiceDraft | null>(null);
  const [busy, setBusy] = useState(false);
  const mediaRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<number | null>(null);
  const photoRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!open) return;
    apiGet('/voice/status')
      .then((s) => {
        setStatus(s);
        if (!s.enabled) {
          setUi('error');
          setMessage('Comandos por voz estão desligados. Use a tela normalmente.');
        } else if (!navigator.mediaDevices?.getUserMedia && !s.manualTranscriptAllowed) {
          setUi('unsupported');
        } else {
          setUi('consent');
          setMessage('O microfone só grava depois do seu ok. Você pode cancelar a qualquer momento.');
        }
      })
      .catch(() => {
        setUi('error');
        setMessage('Não foi possível verificar a voz. A tela tradicional continua disponível.');
      });
  }, [open]);

  useEffect(() => () => stopTracks(), []);

  function stopTracks() {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (timerRef.current) window.clearInterval(timerRef.current);
    timerRef.current = null;
  }

  function reset() {
    stopTracks();
    setDraft(null);
    setTranscript('');
    setSeconds(0);
    setBusy(false);
    setUi(status?.enabled ? 'consent' : 'error');
  }

  async function startRecording() {
    if (!navigator.mediaDevices?.getUserMedia) {
      setUi('unsupported');
      setMessage('Este navegador não grava áudio. Digite o comando abaixo.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      const mime = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
        ? 'audio/webm;codecs=opus'
        : 'audio/webm';
      const rec = new MediaRecorder(stream, { mimeType: mime });
      chunksRef.current = [];
      rec.ondataavailable = (ev) => {
        if (ev.data.size > 0) chunksRef.current.push(ev.data);
      };
      rec.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: rec.mimeType || 'audio/webm' });
        void sendAudio(blob);
      };
      mediaRef.current = rec;
      rec.start();
      setSeconds(0);
      setUi('recording');
      setMessage('Ouvindo… toque em parar quando terminar.');
      timerRef.current = window.setInterval(() => {
        setSeconds((s) => {
          const next = s + 1;
          if (next >= (status?.maxSeconds ?? 60)) {
            stopRecording();
          }
          return next;
        });
      }, 1000);
    } catch {
      setUi('error');
      setMessage('Permissão de microfone negada. Você pode digitar o comando.');
    }
  }

  function stopRecording() {
    if (mediaRef.current && mediaRef.current.state !== 'inactive') {
      mediaRef.current.stop();
    }
    stopTracks();
  }

  function cancelRecording() {
    if (mediaRef.current && mediaRef.current.state !== 'inactive') {
      mediaRef.current.onstop = null;
      mediaRef.current.stop();
    }
    chunksRef.current = [];
    stopTracks();
    reset();
    onClose();
  }

  async function sendAudio(blob: Blob) {
    setUi('processing');
    setMessage('Interpretando o comando…');
    const form = new FormData();
    form.append('file', blob, 'command.webm');
    const ctx = taskContextFromPath(location.pathname);
    form.append('contextJson', JSON.stringify(ctx));
    if (transcript.trim()) form.append('transcript', transcript.trim());
    try {
      const created = await apiUploadForm('/voice/drafts', form);
      applyDraft(created);
    } catch (e) {
      setUi('error');
      setMessage(e instanceof Error ? e.message : 'Falha ao enviar o áudio.');
    }
  }

  async function sendText() {
    if (!transcript.trim()) {
      setMessage('Digite o que você quer fazer.');
      return;
    }
    setUi('processing');
    setBusy(true);
    try {
      const created = await apiPost('/voice/drafts', {
        transcript: transcript.trim(),
        ...taskContextFromPath(location.pathname)
      });
      applyDraft(created);
    } catch (e) {
      setUi('error');
      setMessage(e instanceof Error ? e.message : 'Falha ao interpretar.');
    } finally {
      setBusy(false);
    }
  }

  function applyDraft(d: VoiceDraft) {
    setDraft(d);
    if (d.status === 'FAILED' || d.status === 'EXPIRED') {
      setUi('error');
      setMessage(d.errorMessage || 'Não deu para concluir o comando.');
      return;
    }
    if (d.status === 'NEEDS_INPUT') {
      setUi('needs-input');
      setMessage(d.previewText || 'Preciso de mais informação.');
      return;
    }
    setUi('preview');
    setMessage(d.previewText || 'Confira e confirme.');
  }

  async function choose(field: string, key: string) {
    if (!draft) return;
    setBusy(true);
    try {
      const updated = await apiPatch(`/voice/drafts/${draft.id}`, { selectedOptions: { [field]: key } });
      applyDraft(updated);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Falha ao aplicar a escolha.');
    } finally {
      setBusy(false);
    }
  }

  async function patchField(fields: Record<string, string>) {
    if (!draft) return;
    setBusy(true);
    try {
      const updated = await apiPatch(`/voice/drafts/${draft.id}`, { fields });
      applyDraft(updated);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Falha ao atualizar.');
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    if (!draft) return;
    setBusy(true);
    try {
      const key = idempotencyKeyFor(draft.id);
      const updated = await apiPostIdempotent(`/voice/drafts/${draft.id}/confirm`, { idempotencyKey: key }, key);
      if (updated.status === 'NEEDS_INPUT') {
        applyDraft(updated);
        return;
      }
      setDraft(updated);
      setUi('success');
      setMessage(updated.result?.message || 'Pronto.');
      const to = updated.result?.navigateTo;
      if (to) {
        setTimeout(() => {
          onClose();
          navigate(to);
        }, 700);
      }
    } catch (e) {
      setUi('error');
      setMessage(e instanceof Error ? e.message : 'Falha ao confirmar.');
    } finally {
      setBusy(false);
    }
  }

  async function onPhoto(file: File) {
    if (!draft) return;
    const runId = draft.intent?.action === 'COMPLETE_TASK'
      ? (location.pathname.match(/\/routines\/(\d+)/) || [])[1]
      : null;
      const entityType = draft.resultEntityType || draft.result?.entityType;
    const taskId = draft.resultEntityId || draft.result?.entityId || runId;
    if (!taskId) {
      setMessage('Abra a tarefa e anexe a foto pela tela, depois confirme de novo.');
      return;
    }
    setBusy(true);
    try {
      const path = entityType === 'OCCURRENCE'
        ? `/occurrences/${taskId}/attachments`
        : `/routines/runs/${taskId}/attachments`;
      await apiUpload(path, file);
      const refreshed = await apiGet(`/voice/drafts/${draft.id}`);
      applyDraft(refreshed);
      setMessage('Foto anexada. Confirme a conclusão.');
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Falha ao enviar a foto.');
    } finally {
      setBusy(false);
    }
  }

  async function cancelDraft() {
    if (draft) {
      try { await apiDelete(`/voice/drafts/${draft.id}`); } catch { /* ignore */ }
    }
    reset();
    onClose();
  }

  if (!open) return null;

  const intent = draft?.intent;
  const missing = intent?.missingFields || [];
  const ambiguities = intent?.ambiguities || [];
  const needsPhoto = missing.includes('photo');

  return (
    <div className="voice-overlay" role="dialog" aria-modal="true" aria-labelledby="voice-title">
      <div className="voice-sheet">
        <div className="voice-head">
          <h2 id="voice-title">Comando por voz</h2>
          <button type="button" className="btn-ghost" onClick={cancelDraft} aria-label="Fechar">Fechar</button>
        </div>
        <p className="muted" aria-live="polite">{message}</p>
        {ui === 'recording' && (
          <div className="voice-rec" aria-live="assertive">
            <span className="voice-dot" />
            <strong>{formatSec(seconds)}</strong>
            <span className="muted small">gravando</span>
          </div>
        )}
        {(ui === 'consent' || ui === 'error' || ui === 'unsupported' || ui === 'recording') && (
          <textarea
            className="voice-text"
            placeholder="Ou digite: crie uma tarefa para o gerente João amanhã às 8, vencendo às 10…"
            value={transcript}
            onChange={(e) => setTranscript(e.target.value)}
            rows={3}
            aria-label="Comando em texto"
          />
        )}
        {ui === 'consent' && (
          <div className="voice-actions">
            <button type="button" className="btn-primary" onClick={startRecording}>Permitir microfone e gravar</button>
            <button type="button" className="btn-ghost" onClick={sendText} disabled={busy}>Enviar texto</button>
          </div>
        )}
        {ui === 'unsupported' && (
          <button type="button" className="btn-primary" onClick={sendText} disabled={busy}>Enviar texto</button>
        )}
        {ui === 'recording' && (
          <div className="voice-actions">
            <button type="button" className="btn-primary" onClick={stopRecording}>Parar</button>
            <button type="button" className="btn-ghost danger" onClick={cancelRecording}>Cancelar</button>
          </div>
        )}
        {ui === 'processing' && <p className="muted">Aguarde…</p>}
        {(ui === 'preview' || ui === 'needs-input') && draft && (
          <div className="voice-preview">
            {intent?.transcript && <p className="small"><strong>Você disse:</strong> {intent.transcript}</p>}
            {intent?.warnings?.map((w) => <p key={w} className="muted small">{w}</p>)}
            {ambiguities.map((a) => (
              <div key={a.field} className="voice-choices">
                <span className="field-label">Qual {labelField(a.field)}?</span>
                {a.options.map((o) => (
                  <button key={o.key} type="button" className="filter-chip" disabled={busy} onClick={() => choose(a.field, o.key)}>
                    {o.label}
                  </button>
                ))}
              </div>
            ))}
            {missing.includes('title') && (
              <input placeholder="Título" onBlur={(e) => e.target.value && patchField({ title: e.target.value })} />
            )}
            {missing.includes('startTime') && (
              <label className="field-label">Início
                <input type="time" onChange={(e) => patchField({ startTime: e.target.value })} />
              </label>
            )}
            {missing.includes('dueTime') && (
              <label className="field-label">Vencimento
                <input type="time" onChange={(e) => patchField({ dueTime: e.target.value })} />
              </label>
            )}
            {missing.includes('scheduledDate') && (
              <label className="field-label">Data
                <input type="date" onChange={(e) => patchField({ scheduledDate: e.target.value })} />
              </label>
            )}
            {missing.includes('comment') && (
              <textarea rows={2} placeholder="Comentário obrigatório" onBlur={(e) => e.target.value && patchField({ comment: e.target.value })} />
            )}
            {needsPhoto && (
              <div>
                <p className="muted small">Esta tarefa exige foto do responsável. Abra a câmera traseira.</p>
                <button type="button" className="btn-ghost" onClick={() => photoRef.current?.click()}>Tirar foto</button>
                <input ref={photoRef} type="file" accept="image/*" capture="environment" hidden onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) void onPhoto(f);
                }} />
              </div>
            )}
            <div className="voice-actions">
              <button type="button" className="btn-primary" disabled={busy || (ui === 'needs-input' && ambiguities.length > 0)} onClick={confirm}>
                Confirmar
              </button>
              <button type="button" className="btn-ghost danger" onClick={cancelDraft}>Cancelar</button>
            </div>
          </div>
        )}
        {ui === 'success' && <p><strong>{message}</strong></p>}
        {ui === 'error' && (
          <div className="voice-actions">
            <button type="button" className="btn-primary" onClick={reset}>Tentar de novo</button>
            <button type="button" className="btn-ghost" onClick={sendText} disabled={busy}>Enviar texto</button>
          </div>
        )}
      </div>
    </div>
  );
}

function formatSec(s: number) {
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${String(r).padStart(2, '0')}`;
}

function labelField(field: string) {
  if (field === 'branchReference') return 'filial';
  if (field === 'targetUserReference') return 'responsável';
  if (field === 'targetSectorReference') return 'setor';
  if (field === 'taskReference') return 'tarefa';
  if (field === 'companyReference') return 'empresa';
  return field;
}
