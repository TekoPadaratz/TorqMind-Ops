# VOICE_COMMANDS — contrato v1

Interface nova para os **mesmos** casos de uso. IDs só no backend.

## Endpoints

| Método | Path | Função |
|--------|------|--------|
| GET | `/api/voice/status` | `{ enabled, transcriptionProvider, intentProvider, maxSeconds, maxBytes }` |
| POST | `/api/voice/drafts` | Áudio (`file`) e/ou `transcript` + `contextJson` opcional |
| GET | `/api/voice/drafts/{id}` | Rascunho do próprio usuário |
| POST | `/api/voice/drafts/{id}/confirm` | Header `Idempotency-Key` obrigatório em mutações |
| PATCH | `/api/voice/drafts/{id}` | Correção de campos (revalida) |
| DELETE | `/api/voice/drafts/{id}` | Cancela |

Estados: `PROCESSING | NEEDS_INPUT | READY_FOR_CONFIRMATION | CONFIRMED | CANCELLED | EXPIRED | FAILED`

## Intent (`schemaVersion: "1"`)

Campos conhecidos (extras rejeitados):

`schemaVersion, action, transcript, taskReference, title, description, companyReference, branchReference, cityReference, targetType, targetUserReference, targetSectorReference, recurrence, scheduledDate, startTime, dueTime, reminderBeforeMinutes, requiresPhoto, requiresComment, comment, occurrencePriority, fuel, requestedStatus, missingFields, ambiguities, warnings, confidence, requiresConfirmation`

Ações: `CREATE_TASK | CREATE_OCCURRENCE | START_TASK | ADD_COMMENT | COMPLETE_TASK | REJECT_TASK | OPEN_TASK | OPEN_QUALITY_ANALYSIS | LIST_TASKS`

`OPEN_QUALITY_ANALYSIS` só abre `/occurrences/new/fuel-quality` (query `fuel=` se falado). Não persiste ocorrência.

`fuel`: `DIESEL_S10 | DIESEL_S500 | ETANOL | GASOLINA_ADITIVADA | GASOLINA_COMUM` (opcional)


`targetType`: `USER | SECTOR | MANAGERS | ALL`  
`recurrence`: `ONCE | DAILY | WEEKLY | MONTHLY | CUSTOM`  
Datas/horas normalizadas em `America/Sao_Paulo`.

## Resolução

Referências viram IDs só contra catálogo autorizado (`AuthorizedEntityResolver`). 0 matches → missing; 2+ → `ambiguities[]` com opções `{id,label}` (id interno **não** é enviado ao cliente — só `key` opaca + label). Cliente escolhe pela chave da opção.

## Confirmação e idempotência

Nenhuma mutação sem confirm. Mesma `Idempotency-Key` + rascunho CONFIRMED devolve o resultado original. Áudio temporário apagado em `finally`.

## Segurança

Consentimento na UI. Rate limit por usuário. Magic bytes de áudio. Timeout STT/LLM. Prompt injection ignorada (só extração de intent). Sem chave no frontend. Sem log de transcript/áudio. Retenção do rascunho: `app.voice.draft-ttl-minutes` (padrão 15).

## Evidência na conclusão

Se `requiresPhoto` e não há imagem válida: estado `NEEDS_INPUT`, `missingFields: ["photo"]`. UI abre câmera traseira, faz upload no endpoint tradicional, reconsulta o draft e confirma de novo.

## Erros

`voice_disabled` 503 · `rate_limited` 429 · `validation_error` 422 · `forbidden` 403 · `voice_provider_unavailable` 503

## Aceite

Ver `docs/engineering/VALIDATION_REPORT.md`.
