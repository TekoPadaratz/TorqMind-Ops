# VOICE_COMMANDS — contrato v1

Interface nova para os **mesmos** casos de uso. IDs só no backend.

## Endpoints

| Método | Path | Função |
|--------|------|--------|
| GET | `/api/voice/status` | `{ enabled, transcriptionProvider, intentProvider, maxSeconds, maxBytes }` |
| POST | `/api/voice/drafts` | Áudio (`file`) e/ou `transcript` + `contextJson` opcional |
| GET | `/api/voice/drafts/{id}` | Rascunho do próprio usuário |
| POST | `/api/voice/drafts/{id}/confirm` | Header `Idempotency-Key` obrigatório em mutações |
| PATCH | `/api/voice/drafts/{id}` | Correção de campos, `selectedOptions`, ou **`transcript`** (resposta falada em diálogo) |
| DELETE | `/api/voice/drafts/{id}` | Cancela |

Estados: `PROCESSING | NEEDS_INPUT | READY_FOR_CONFIRMATION | CONFIRMED | CANCELLED | EXPIRED | FAILED`

## Intent (`schemaVersion: "1"`)

Campos conhecidos (extras rejeitados):

`schemaVersion, action, transcript, taskReference, title, description, companyReference, branchReference, cityReference, targetType, targetUserReference, targetSectorReference, recurrence, scheduledDate, startTime, dueTime, reminderBeforeMinutes, requiresPhoto, requiresComment, comment, occurrencePriority, fuel, requestedStatus, missingFields, ambiguities, warnings, confidence, requiresConfirmation`

Ações: `CREATE_TASK | CREATE_OCCURRENCE | START_TASK | ADD_COMMENT | COMPLETE_TASK | REJECT_TASK | OPEN_TASK | OPEN_QUALITY_ANALYSIS | LIST_TASKS | LIST_OCCURRENCES | LIST_MY_TASKS | QUERY_TASK | DELETE_TASK | HELP | ADMIN_DENIED | START_OCCURRENCE | CLOSE_OCCURRENCE | OPEN_NOTIFICATIONS | SUMMARY_TODAY`

`OPEN_QUALITY_ANALYSIS` só abre `/occurrences/new/fuel-quality` (query `fuel=` se falado). Não persiste ocorrência.

`fuel`: `DIESEL_S10 | DIESEL_S500 | ETANOL | GASOLINA_ADITIVADA | GASOLINA_COMUM` (opcional)


`targetType`: `USER | SECTOR | MANAGERS | ALL`
`recurrence`: `ONCE | DAILY | WEEKLY | MONTHLY | CUSTOM`
Datas/horas normalizadas em `America/Sao_Paulo`.

## Resolução

Referências viram IDs só contra catálogo autorizado (`AuthorizedEntityResolver`). 0 matches → missing; 2+ → `ambiguities[]` com opções `{id,label}` (id interno **não** é enviado ao cliente — só `key` opaca + label). Cliente escolhe pela chave da opção.

## Execução, confirmação e voz (v2 — conversacional)

- **Diálogo por voz (PATCH `transcript`)**: quando o rascunho está em `NEEDS_INPUT` ou aguardando confirmação destrutiva, o cliente envia a fala em `transcript`; o backend resolve ambiguidades, preenche slots e revalida. Confirmação destrutiva também aceita "sim"/"não" falado (cliente ou servidor).
- **Execução direta**: comandos não destrutivos (criar, consultar, listar, **HELP**) executam na hora, sem tela de confirmação (`requiresConfirmation=false`). O app **fala a resposta** (TTS `SpeechSynthesis` pt-BR) via `result.spoken`.
- **Destrutivos exigem confirmação** (`requiresConfirmation=true`): `DELETE_TASK` e `REJECT_TASK` mostram preview e falam a pergunta antes de executar.
- **Exclusão em massa recusada**: "excluir todas/tudo" nunca executa (mesmo MASTER/OWNER) — resposta de segurança falada.
- **Exclusão ambígua** (vários com o nome) → `ambiguities[]` com opções por filial/usuário (key `t:ID`); usuário escolhe → confirma → exclui.
- `DELETE_TASK`: soft-delete de rotina (template) com permissão `TenantResolver.canDeleteTemplate` (MASTER/OWNER na empresa; criador só a sua; executor não).
- `QUERY_TASK`: consulta status por nome (título+responsável+data) e responde **falando**; não navega.
- `LIST_TASKS`: resumo falado (contagem + primeiras rotinas), status `PENDENTE|ATRASADA|HOJE`.
- `LIST_OCCURRENCES`: resumo falado das ocorrências abertas (contagem + primeiras).
- `HELP`: resume capacidades da assistente (sempre responde; não exige confirmação).
- `ADMIN_DENIED`: recusa cadastros/gestão administrativa com mensagem por papel.
- `START_OCCURRENCE` / `CLOSE_OCCURRENCE`: assumir e encerrar ocorrências via `OccurrenceService.transition`.
- `LIST_MY_TASKS`: rotinas atribuídas ao usuário do JWT.
- `OPEN_NOTIFICATIONS`: contagem + último aviso; navega `/notifications`.
- `SUMMARY_TODAY`: resumo falado (pendentes, atrasadas, ocorrências, avisos).
- **Aprendizado (V26):** tabela `voice_phrase_learnings`; grava após confirm; ver `PERSONAL_ASSISTANT_MAP.md`.
- Idempotência preservada: mesma `Idempotency-Key` + rascunho CONFIRMED devolve o resultado original. Áudio temporário apagado em `finally`.
- **Defaults por empresa** (`company_settings`, V16, só MASTER): foto/comentário obrigatórios e lembrete padrão preenchem o que não foi dito em `CREATE_TASK` (via `VoiceDraftService.applyCompanyDefaults`).

## Segurança

Consentimento na UI. Rate limit por usuário. Magic bytes de áudio. Timeout STT/LLM. Prompt injection ignorada (só extração de intent). Sem chave no frontend. Sem log de transcript/áudio. Retenção do rascunho: `app.voice.draft-ttl-minutes` (padrão 15).

## Evidência na conclusão

Se `requiresPhoto` e não há imagem válida: estado `NEEDS_INPUT`, `missingFields: ["photo"]`. UI abre câmera traseira, faz upload no endpoint tradicional, reconsulta o draft e confirma de novo.

## Erros

`voice_disabled` 503 · `rate_limited` 429 · `validation_error` 422 · `forbidden` 403 · `voice_provider_unavailable` 503

## Aceite

Ver `docs/engineering/VALIDATION_REPORT.md`.
