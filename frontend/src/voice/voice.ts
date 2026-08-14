export type VoiceStatus = {
  enabled: boolean;
  transcriptionProvider: string;
  intentProvider: string;
  maxSeconds: number;
  maxBytes: number;
  manualTranscriptAllowed: boolean;
};

export type VoiceOption = { key: string; label: string };
export type VoiceAmbiguity = { field: string; query: string; options: VoiceOption[] };

export type VoiceIntent = {
  schemaVersion?: string;
  action?: string;
  transcript?: string;
  title?: string;
  description?: string;
  comment?: string;
  startTime?: string;
  dueTime?: string;
  scheduledDate?: string;
  missingFields?: string[];
  ambiguities?: VoiceAmbiguity[];
  warnings?: string[];
  requiresPhoto?: boolean;
  requiresComment?: boolean;
};

export type VoiceDraft = {
  id: string;
  status: string;
  action?: string;
  transcript?: string;
  previewText?: string;
  errorMessage?: string;
  correlationId?: string;
  intent?: VoiceIntent;
  resultEntityType?: string;
  resultEntityId?: number;
  result?: {
    entityType?: string;
    entityId?: number;
    message?: string;
    navigateTo?: string;
    items?: Array<{ id: number; title?: string; status?: string }>;
  };
};

export type VoiceUiState =
  | 'idle'
  | 'consent'
  | 'recording'
  | 'processing'
  | 'preview'
  | 'needs-input'
  | 'success'
  | 'error'
  | 'unsupported';

export function taskContextFromPath(pathname: string, title?: string): {
  currentTaskType?: string;
  currentTaskId?: number;
  currentTaskTitle?: string;
} {
  const routine = pathname.match(/^\/routines\/(\d+)/);
  if (routine) {
    return { currentTaskType: 'ROUTINE_RUN', currentTaskId: Number(routine[1]), currentTaskTitle: title };
  }
  const occ = pathname.match(/^\/occurrences\/(\d+)/);
  if (occ) {
    return { currentTaskType: 'OCCURRENCE', currentTaskId: Number(occ[1]), currentTaskTitle: title };
  }
  return {};
}

export function idempotencyKeyFor(draftId: string): string {
  const storeKey = `torqmind.voice.idem.${draftId}`;
  const existing = sessionStorage.getItem(storeKey);
  if (existing) return existing;
  const created = crypto.randomUUID();
  sessionStorage.setItem(storeKey, created);
  return created;
}
