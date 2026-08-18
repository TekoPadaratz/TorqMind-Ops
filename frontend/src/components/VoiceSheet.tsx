import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { apiDelete, apiGet, apiPatch, apiPost, apiPostIdempotent, apiUpload } from '../api';
import {
  browserSpeechRecognitionConstructor,
  browserSpeechRecognitionSupported,
  BrowserSpeechRecognition,
  idempotencyKeyFor,
  speak,
  taskContextFromPath,
  VoiceDraft,
  VoiceStatus,
  VoiceUiState
} from '../voice/voice';

type Props = { open: boolean; onClose: () => void };

export default function VoiceSheet({ open, onClose }: Props) {
  const location = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = useState<VoiceStatus | null>(null);
  const [ui, setUi] = useState<VoiceUiState>('consent');
  const [message, setMessage] = useState('Preparando comando por voz…');
  const [seconds, setSeconds] = useState(0);
  const [transcript, setTranscript] = useState('');
  const [draft, setDraft] = useState<VoiceDraft | null>(null);
  const [busy, setBusy] = useState(false);
  const [stopping, setStopping] = useState(false);
  const recognitionRef = useRef<BrowserSpeechRecognition | null>(null);
  const recognizedTextRef = useRef('');
  const stoppingRef = useRef(false);
  const timerRef = useRef<number | null>(null);
  const stopFallbackRef = useRef<number | null>(null);
  const photoRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!open) return;
    setUi('consent');
    setMessage('Preparando comando por voz…');
    setDraft(null);
    setBusy(false);
    setSeconds(0);
    stoppingRef.current = false;
    setStopping(false);
    let cancelled = false;
    apiGet('/voice/status')
      .then((s) => {
        if (cancelled) return;
        setStatus(s);
        if (!s.enabled) {
          setUi('error');
          setMessage('Comandos por voz estão desligados. Use a tela normalmente.');
        } else if (!browserSpeechRecognitionSupported() && !s.manualTranscriptAllowed) {
          setUi('unsupported');
          setMessage('Este navegador não reconhece fala e o texto manual está desligado.');
        } else {
          setUi('consent');
          setMessage(browserSpeechRecognitionSupported()
            ? 'Toque em Falar. Quando terminar, toque em Parar uma vez.'
            : 'Este navegador não reconhece fala. Digite o comando abaixo.');
        }
      })
      .catch(() => {
        if (cancelled) return;
        setUi('error');
        setMessage('Não foi possível verificar a voz. A tela tradicional continua disponível.');
      });
    return () => {
      cancelled = true;
      hardStopRecognition();
    };
  }, [open]);

  useEffect(() => () => hardStopRecognition(), []);

  function clearTimer() {
    if (timerRef.current) window.clearInterval(timerRef.current);
    timerRef.current = null;
  }

  function clearStopFallback() {
    if (stopFallbackRef.current) window.clearTimeout(stopFallbackRef.current);
    stopFallbackRef.current = null;
  }

  function hardStopRecognition() {
    clearTimer();
    clearStopFallback();
    const recognition = recognitionRef.current;
    recognitionRef.current = null;
    if (!recognition) return;
    try {
      recognition.onresult = null;
      recognition.onerror = null;
      recognition.onend = null;
      recognition.abort();
    } catch {
      /* ignore */
    }
  }

  function reset() {
    hardStopRecognition();
    stoppingRef.current = false;
    setStopping(false);
    setDraft(null);
    setTranscript('');
    recognizedTextRef.current = '';
    setSeconds(0);
    setBusy(false);
    setUi(status?.enabled === false ? 'error' : 'consent');
    setMessage(browserSpeechRecognitionSupported()
      ? 'Toque em Falar. Quando terminar, toque em Parar uma vez.'
      : 'Digite o comando abaixo.');
  }

  function startRecording() {
    if (busy || stoppingRef.current || recognitionRef.current) return;
    const Recognition = browserSpeechRecognitionConstructor();
    if (!Recognition) {
      setUi('unsupported');
      setMessage('Este navegador não reconhece fala. Digite o comando abaixo.');
      return;
    }

    hardStopRecognition();
    stoppingRef.current = false;
    recognizedTextRef.current = '';
    setTranscript('');
    setSeconds(0);
    setMessage('Abrindo microfone…');

    try {
      const recognition = new Recognition();
      recognition.lang = 'pt-BR';
      // continuous=true: o botão Parar controla o fim (no celular, continuous=false ignora stop).
      recognition.continuous = true;
      recognition.interimResults = true;
      recognition.maxAlternatives = 1;

      recognition.onresult = (event) => {
        if (stoppingRef.current) return;
        let text = '';
        for (let index = 0; index < event.results.length; index += 1) {
          text += event.results[index]?.[0]?.transcript ?? '';
        }
        recognizedTextRef.current = text.trim();
        setTranscript(recognizedTextRef.current);
      };

      recognition.onerror = (event) => {
        if (stoppingRef.current) return;
        hardStopRecognition();
        const denied = event.error === 'not-allowed' || event.error === 'service-not-allowed';
        const aborted = event.error === 'aborted';
        if (aborted) return;
        setUi('error');
        setMessage(denied
          ? 'Permissão de microfone negada. Você pode digitar o comando.'
          : 'Não consegui reconhecer a fala. Tente novamente ou digite o comando.');
      };

      recognition.onend = () => {
        // Se o browser encerrar sozinho sem o usuário tocar em Parar, volta ao consentimento.
        if (recognitionRef.current !== recognition) return;
        recognitionRef.current = null;
        clearTimer();
        if (stoppingRef.current) return;
        setUi('consent');
        setMessage(recognizedTextRef.current
          ? 'Reconhecimento pausado. Toque em Falar de novo ou envie o texto.'
          : 'Toque em Falar. Quando terminar, toque em Parar uma vez.');
      };

      recognitionRef.current = recognition;
      recognition.start();
      setUi('recording');
      setMessage('Ouvindo… toque em Parar quando terminar.');
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
      hardStopRecognition();
      setUi('error');
      setMessage('Não foi possível abrir o reconhecimento de fala. Digite o comando abaixo.');
    }
  }

  function finishWithTranscript(source: string) {
    clearStopFallback();
    const finalText = (recognizedTextRef.current || source || transcript).trim();
    stoppingRef.current = false;
    setStopping(false);
    if (finalText) {
      void sendRecognizedText(finalText);
      return;
    }
    setBusy(false);
    setUi('consent');
    setMessage('Não ouvi um comando. Tente novamente ou digite abaixo.');
  }

  function stopRecording() {
    if (stoppingRef.current) return;
    if (!recognitionRef.current && ui !== 'recording') return;

    stoppingRef.current = true;
    setStopping(true);
    clearTimer();
    setBusy(true);
    setUi('processing');
    setMessage('Finalizando o reconhecimento…');

    const snapshot = (recognizedTextRef.current || transcript).trim();
    const recognition = recognitionRef.current;
    recognitionRef.current = null;

    if (recognition) {
      try {
        recognition.onresult = (event) => {
          let text = '';
          for (let index = 0; index < event.results.length; index += 1) {
            text += event.results[index]?.[0]?.transcript ?? '';
          }
          if (text.trim()) recognizedTextRef.current = text.trim();
        };
        recognition.onerror = null;
        recognition.onend = () => {
          try { recognition.abort(); } catch { /* ignore */ }
          finishWithTranscript(snapshot);
        };
        recognition.stop();
      } catch {
        try { recognition.abort(); } catch { /* ignore */ }
        finishWithTranscript(snapshot);
        return;
      }
    }

    // Se o browser não disparar onend (comum no mobile), segue mesmo assim.
    clearStopFallback();
    stopFallbackRef.current = window.setTimeout(() => {
      try { recognition?.abort(); } catch { /* ignore */ }
      finishWithTranscript(snapshot);
    }, 700);
  }

  function cancelRecording() {
    stoppingRef.current = true;
    setStopping(true);
    hardStopRecognition();
    stoppingRef.current = false;
    setStopping(false);
    reset();
    onClose();
  }

  async function sendText() {
    await sendRecognizedText(transcript);
  }

  async function sendRecognizedText(value: string) {
    const normalized = value.trim();
    if (!normalized) {
      setBusy(false);
      setMessage('Digite o que você quer fazer.');
      return;
    }
    setTranscript(normalized);
    setUi('processing');
    setBusy(true);
    try {
      const created = await apiPost('/voice/drafts', {
        transcript: normalized,
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
    if (d.status === 'READY_FOR_CONFIRMATION') {
      if (d.intent?.requiresConfirmation === true) {
        // Ação destrutiva (excluir/rejeitar): pede confirmação e fala a pergunta.
        setUi('preview');
        const question = d.previewText || 'Confirma?';
        setMessage(question);
        speak(question);
        return;
      }
      // Execução direta: comando claro e resolvido cria na hora, sem confirmação manual.
      setUi('processing');
      setMessage(d.previewText || 'Executando…');
      void confirm(d);
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

  async function confirm(target: VoiceDraft | null = draft) {
    if (!target) return;
    setBusy(true);
    try {
      const key = idempotencyKeyFor(target.id);
      const updated = await apiPostIdempotent(`/voice/drafts/${target.id}/confirm`, { idempotencyKey: key }, key);
      if (updated.status === 'NEEDS_INPUT') {
        applyDraft(updated);
        return;
      }
      setDraft(updated);
      setUi('success');
      const spokenAnswer = updated.result?.spoken || updated.result?.message || 'Pronto.';
      setMessage(spokenAnswer);
      speak(spokenAnswer);
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
    stoppingRef.current = true;
    hardStopRecognition();
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
  const canType = ui === 'consent' || ui === 'error' || ui === 'unsupported' || ui === 'recording';

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
            <span className="muted small">ouvindo</span>
          </div>
        )}
        {canType && (
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
            {browserSpeechRecognitionSupported() && (
              <button type="button" className="btn-primary" onClick={startRecording} disabled={busy}>
                Falar comando (grátis)
              </button>
            )}
            <button type="button" className="btn-ghost" onClick={sendText} disabled={busy || !transcript.trim()}>
              Enviar texto
            </button>
          </div>
        )}
        {ui === 'unsupported' && (
          <button type="button" className="btn-primary" onClick={sendText} disabled={busy || !transcript.trim()}>
            Enviar texto
          </button>
        )}
        {ui === 'recording' && (
          <div className="voice-actions">
            <button type="button" className="btn-primary" onClick={stopRecording} disabled={stopping}>
              Parar
            </button>
            <button type="button" className="btn-ghost danger" onClick={cancelRecording}>Cancelar</button>
          </div>
        )}
        {ui === 'processing' && <p className="muted">Aguarde…</p>}
        {(ui === 'preview' || ui === 'needs-input') && draft && (
          <div className="voice-preview">
            {intent?.transcript && <p className="small"><strong>Você disse:</strong> {intent.transcript}</p>}
            {intent?.action && (
              <p className="small muted">Ação: {intent.action === 'CREATE_TASK' ? 'Criar tarefa' : intent.action === 'CREATE_OCCURRENCE' ? 'Abrir ocorrência' : intent.action === 'OPEN_QUALITY_ANALYSIS' ? 'Abrir análise de qualidade (sem salvar)' : intent.action}</p>
            )}
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
              <button type="button" className="btn-primary" disabled={busy || (ui === 'needs-input' && ambiguities.length > 0)} onClick={() => confirm()}>
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
            <button type="button" className="btn-ghost" onClick={sendText} disabled={busy || !transcript.trim()}>Enviar texto</button>
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
