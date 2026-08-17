# SYSTEM_MAP — TorqMind Ops

Fonte canônica compacta. Detalhes de voz: `docs/contracts/VOICE_COMMANDS.md`.

## Módulos

- Rotinas: template → runs (`PENDENTE|EM_ANDAMENTO|CONCLUIDA|ATRASADA|REJEITADA`)
- Ocorrências: (`ABERTA|EM_ATENDIMENTO|AGUARDANDO_VALIDACAO|ENCERRADA|REJEITADA`); tipo opcional `GENERIC | FUEL_QUALITY_RECEIPT` (análise de qualidade no recebimento; 1 recebimento = 1 ocorrência; rascunho `ABERTA`, finalização `ENCERRADA` + PDF no `StorageProvider`)
- Catálogo: empresas, filiais, setores, usuários (sem MASTER na lista operacional)
- Notificações: por destinatário; `notifyCounterpart` nunca notifica o actor; MASTER recebe cópia (testes)
- Voz: rascunho → confirmação → mesmos serviços da UI
- Storage: `StorageProvider` (local | Google Drive OAuth)

## HTTP (`/api`)

| Área | Arquivo |
|------|---------|
| Auth | `interfaces/rest/auth/AuthController` |
| Admin MASTER | `interfaces/rest/admin/AdminController` |
| Catálogo | `interfaces/rest/catalog/CatalogController` |
| Rotinas | `interfaces/rest/routine/RoutineController` |
| Ocorrências | `interfaces/rest/occurrence/OccurrenceController` |
| Anexos | `interfaces/rest/attachment/AttachmentController` |
| Dashboard | `interfaces/rest/dashboard/DashboardController` |
| Avisos | `interfaces/rest/notification/NotificationController` |
| Voz | `interfaces/rest/voice/VoiceController` |

Upload multipart: campo `file`. Voz: `POST /api/voice/drafts` (áudio e/ou `transcript`).

## Serviços de aplicação

- `RoutineService` — criar template/tarefa, gerar runs, transições + evidências
- `OccurrenceService` — abrir e transicionar
- `TaskDetailService` — comentários, anexos (assinatura real), detalhe
- `TenantResolver` + `TaskAuthorization` — empresa/filial e responsável nominal
- `NotificationService.notifyCounterpart`
- `VoiceDraftService` / `VoiceCommandExecutor` / `AuthorizedEntityResolver`

## Tabelas

`companies`, `branches`, `sectors`, `auth_users`, `routine_templates`, `routine_runs`, `occurrences`, `fuel_quality_analyses` (V14), `notifications`, `task_comments`, `task_attachments`, `task_activities`, `voice_drafts` (V12)

## Auth

JWT HS256 (`uid`, `role`, `cid`, `bid`). Papéis: MASTER, OWNER, MANAGER, OPERATOR.  
Filtros: `JwtAuthFilter`, rate limit de login, rate limit de voz.

## Fluxo criar → concluir

UI ou voz confirmada → `createRecurringTask` / `open` → run gerado → `transition(EM_ANDAMENTO)` só pelo responsável se nominal → comentário/foto via `TaskDetailService` → `transition(CONCLUIDA)` com `StatusRules` + evidências.

## Docker

Compose project `torqmind-ops-saas` em `/home/tm/torqmind-ops-saas`. Porta host **88**. Banco `torqmind_ops`. Timezone `America/Sao_Paulo`.
