import type { VoiceAmbiguity, VoiceDraft } from './voice';

export type ConfirmationIntent = 'confirm' | 'deny' | 'none';

export function confirmationIntent(transcript: string): ConfirmationIntent {
  const n = normalize(transcript);
  if (!n) return 'none';
  const deny = ['nao', 'não', 'cancela', 'cancelar', 'negativo', 'deixa', 'para', 'pare'];
  const yes = ['sim', 'confirmo', 'confirmar', 'pode', 'pode fazer', 'pode sim', 'ok', 'isso', 'exato', 'correto', 'positivo', 'manda ver', 'vai em frente'];
  const hasDeny = deny.some((w) => n.includes(w));
  const hasYes = yes.some((w) => n.includes(w));
  if (hasDeny && !hasYes) return 'deny';
  if (hasYes) return 'confirm';
  return 'none';
}

export function matchAmbiguityOption(transcript: string, ambiguities: VoiceAmbiguity[]): { field: string; key: string } | null {
  const spoken = normalize(transcript);
  if (!spoken || !ambiguities.length) return null;
  for (const amb of ambiguities) {
    const ordinal = parseOrdinal(spoken);
    if (ordinal != null && ordinal >= 1 && ordinal <= amb.options.length) {
      return { field: amb.field, key: amb.options[ordinal - 1].key };
    }
    let best: { key: string; score: number } | null = null;
    for (const opt of amb.options) {
      const score = matchScore(spoken, opt.label);
      if (!best || score > best.score) {
        best = { key: opt.key, score };
      }
    }
    if (best && best.score >= 2) {
      return { field: amb.field, key: best.key };
    }
  }
  return null;
}

export function fieldsFromSpeech(transcript: string, missing: string[]): Record<string, string> {
  const raw = transcript.trim();
  const spoken = normalize(transcript);
  const out: Record<string, string> = {};
  if (!raw) return out;
  if (missing.includes('title')) out.title = capitalize(raw);
  if (missing.includes('comment')) out.comment = raw;
  if (missing.includes('description')) out.description = raw;
  if (missing.includes('targetUserReference')) out.targetUserReference = raw;
  if (missing.includes('targetSectorReference')) out.targetSectorReference = raw;
  if (missing.includes('branchReference')) out.branchReference = raw;
  const time = extractTime(spoken);
  if (time) {
    if (missing.includes('startTime')) out.startTime = time;
    if (missing.includes('dueTime')) out.dueTime = time;
  }
  if (missing.includes('scheduledDate')) {
    if (spoken.includes('hoje')) out.scheduledDate = todayIso();
    else if (spoken.includes('amanha') || spoken.includes('amanhã')) out.scheduledDate = tomorrowIso();
  }
  return out;
}

export function shouldContinueConversation(draft: VoiceDraft | null): boolean {
  if (!draft) return false;
  if (draft.status === 'NEEDS_INPUT') return true;
  if (draft.status === 'READY_FOR_CONFIRMATION' && draft.intent?.requiresConfirmation === true) return true;
  return false;
}

function parseOrdinal(spoken: string): number | null {
  const num = spoken.match(/\b(?:opcao|opção)?\s*(\d{1,2})\b/);
  if (num) return Number(num[1]);
  if (/\bprimeir/.test(spoken)) return 1;
  if (/\bsegund/.test(spoken)) return 2;
  if (/\bterceir/.test(spoken)) return 3;
  if (/\bquart/.test(spoken)) return 4;
  if (/\bquint/.test(spoken)) return 5;
  return null;
}

function matchScore(spoken: string, label: string): number {
  const nLabel = normalize(label);
  if (!nLabel) return 0;
  if (spoken === nLabel) return 100;
  if (spoken.includes(nLabel) || nLabel.includes(spoken)) return 10;
  let score = 0;
  for (const token of nLabel.split(/\s+/)) {
    if (token.length >= 3 && spoken.includes(token)) score += 2;
  }
  return score;
}

function extractTime(spoken: string): string | null {
  const m = spoken.match(/\b(\d{1,2})(?::(\d{2}))?\b/);
  if (m) {
    const h = Number(m[1]);
    if (h <= 23) {
      const mm = m[2] ?? '00';
      return `${String(h).padStart(2, '0')}:${mm}`;
    }
  }
  return null;
}

function normalize(value: string): string {
  return value
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ');
}

function capitalize(s: string): string {
  if (!s) return s;
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function todayIso(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'America/Sao_Paulo' });
}

function tomorrowIso(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return d.toLocaleDateString('en-CA', { timeZone: 'America/Sao_Paulo' });
}
