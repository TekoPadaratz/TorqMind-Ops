# SYSTEM_MAP — TorqMind Ops

Fonte canônica compacta. Detalhes de voz: `docs/contracts/VOICE_COMMANDS.md`.

## Módulos

- Rotinas: template → runs (`PENDENTE|EM_ANDAMENTO|CONCLUIDA|ATRASADA|REJEITADA`); ao vencer sem conclusão vira `ATRASADA` e **escalona** (avisa responsável + gerentes da filial + donos); **comprovante PDF** por run (`GET /api/routines/runs/{id}/report`: dados, evidências com carimbo data/hora, comentários e histórico)
- Ocorrências: (`ABERTA|EM_ATENDIMENTO|AGUARDANDO_VALIDACAO|ENCERRADA|REJEITADA`); tipo opcional `GENERIC | FUEL_QUALITY_RECEIPT` (análise de qualidade no recebimento; 1 recebimento = 1 ocorrência; rascunho `ABERTA`, finalização `ENCERRADA` + PDF no `StorageProvider`)
- Catálogo: empresas, filiais, setores, usuários (sem MASTER na lista operacional)
- Notificações: por destinatário; `notifyCounterpart` nunca notifica o actor; MASTER recebe cópia (testes)
- Voz: comando falado → execução direta (criar/consultar/listar) ou confirmação (excluir/rejeitar; recusa exclusão em massa); app responde **falando** (TTS pt-BR); mesmos serviços da UI
- Storage: `StorageProvider` (local | Google Drive OAuth)
- PWA offline: fila de uploads (fotos) em IndexedDB (`offline.ts`), reenvio ao reconectar (evento `online`), indicador de pendencias no header; **sem Service Worker de cache** (evita cache preso no iOS). Servidor **deduplica por checksum** (reenvio nao duplica). Voz permanece online (STT + interpretacao no servidor).

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
- `TaskDetailService` — comentários, anexos (assinatura real, **geo opcional lat/lng**, **dedup por checksum**), detalhe, comprovante PDF (`renderRoutineRunReport` + `RoutineRunPdfRenderer`)
- `TenantResolver` + `TaskAuthorization` — empresa/filial e responsável nominal
- `NotificationService.notifyCounterpart`
- `CredentialService` — hash, época JWT e auditoria de senha (`CREATED|SELF_CHANGE|ADMIN_RESET`)
- `VoiceDraftService` / `VoiceCommandExecutor` / `AuthorizedEntityResolver` — voz: consulta/exclusão por nome, defaults por empresa
- `CompanySettingsService` — config por empresa (foto/comentário/lembrete), só MASTER

## Tabelas

`companies`, `branches`, `sectors`, `auth_users`, `password_change_events` (V15), `routine_templates`, `routine_runs`, `occurrences`, `fuel_quality_analyses` (V14), `notifications`, `task_comments`, `task_attachments`, `task_activities`, `voice_drafts` (V12), `company_settings` (V16)

## Auth

JWT HS256 (`uid`, `role`, `cid`, `bid`, `pwe` = época da senha). Papéis: MASTER, OWNER, MANAGER, OPERATOR.
Filtros: `JwtAuthFilter` recarrega o usuário do banco e rejeita token se a conta estiver inativa ou se `pwe` não bater com `auth_users.password_epoch`. Rate limit de login, rate limit de voz.

Troca de senha: `POST /api/auth/password` (própria senha; senha atual errada = 422). MASTER redefine via `PUT /api/admin/users/{id}/password`. Ambas sobem `password_epoch` e gravam `password_change_events` (`CREATED|SELF_CHANGE|ADMIN_RESET`) sem guardar a senha. Sem recuperação por e-mail.

2FA (TOTP, opt-in por usuário): `TotpService` (HMAC-SHA1/Base32, RFC 6238, sem dependência). Segredo **cifrado em repouso** (AES-GCM, `TotpSecretConverter`, chave `TOTP_ENC_KEY` ou derivada do `JWT_SECRET`). Ativação self-service em `/api/auth/2fa` (`GET` status, `POST /setup` gera segredo+otpauth, `POST /enable` confirma código, `POST /disable` exige código). Login em 2 passos: se `totp_enabled`, `POST /api/auth/login` devolve `{totpRequired,challenge}` (JWT `stage=2fa`, 5 min, **rejeitado** como bearer) e `POST /api/auth/login/2fa` troca desafio+código pelo token. Lockout de conta + **rate-limit por IP** (login e login/2fa). Recuperação: MASTER remove via `POST /api/admin/users/{id}/2fa/disable`.

Headers de segurança (SecurityConfig): `frameOptions sameOrigin`, HSTS, Referrer-Policy. Dashboard gerencial: `GET /api/dashboard/metrics` (conclusão no prazo %, aging de atrasos, ranking por filial). Export: `GET /api/routines/runs/export.csv`. Frontend é **PWA instalável** (manifest + ícone; sem SW de cache).

## Fluxo criar → concluir

UI ou voz confirmada → `createRecurringTask` / `open` → run gerado → `transition(EM_ANDAMENTO)` só pelo responsável se nominal → comentário/foto via `TaskDetailService` → `transition(CONCLUIDA)` com `StatusRules` + evidências.

## Docker

Compose project `torqmind-ops-saas` em `/home/tm/torqmind-ops-saas`. Porta host **88**. Banco `torqmind_ops`. Timezone `America/Sao_Paulo`.
