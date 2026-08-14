# VALIDATION_REPORT — voz (2026-08-14)

## Resultado

Comandos por voz **no ar** no TorqMind Ops (homologação porta 88). A UI tradicional continua disponível. Sem `VOICE_OPENAI_API_KEY`, STT automático pede texto; interpretação **deterministic** em PT-BR funciona.

## Ambiente

- Fonte de verdade: `/home/tm/torqmind-ops-saas` em `torqmind-app-stream` (user `tm`)
- `C:\TorqMind-Ops`: **não acessível** (sem SSH config / sem rota). Não houve cópia cega.
- Git: `main` @ `36d914e` + alterações locais (notificações + voz). **Não commitado.**
- Backup DB: `/tmp/torqmind-ops-pre-voice.sql` (69 359 bytes) antes do Flyway V12

## Isolamento do outro TorqMind

Containers protegidos **iguais** antes/depois (ID + StartedAt):

- torqmind-web `06a8e094f9a0` started `2026-08-14T14:45:47Z`
- torqmind-api `32cc8707269b` started `2026-08-14T14:42:49Z`
- torqmind-nginx `13dd21fd2ce0` started `2026-08-14T05:53:03Z`
- torqmind-web-homolog `8f635f72c894` started `2026-08-14T14:35:37Z`
- torqmind-api-homolog `ebaa07bf7351` started `2026-08-14T14:32:25Z`
- torqmind-nginx-homolog `568473e3dc63` started `2026-08-14T05:53:03Z`
- torqmind-debezium `d24b1e8af61c` started `2026-08-14T05:53:03Z`
- torqmind-redpanda `354c40ec5264` started `2026-08-14T05:53:03Z`

Só o compose `torqmind-ops-saas` foi rebuilt (backend/frontend/nginx). Postgres Ops **não** reiniciou.

## Migração

Flyway **V12** `voice_drafts` aplicada com sucesso (`installed_on` 2026-08-14 12:17). `validate` ok. Sem `clean`/`drop`.

## Testes automatizados (execução real)

| Suite | Resultado |
|-------|-----------|
| Backend `mvn -Dtest='!OpsBackendApplicationTests' test` | **27** run, 0 fail (Surefire) |
| Frontend `vitest run` | **1** file / **1** test pass |
| Frontend `npm run build` | OK (VoiceSheet chunk separado 9.47 kB) |
| `OpsBackendApplicationTests` | **não** rodado no container Maven (precisa Postgres); sobe no Compose |

## API de voz na homologação (JWT de teste, sem logar senha)

- `GET /api/voice/status` → 200, enabled, provider deterministic
- `POST /api/voice/drafts` LIST_TASKS → READY_FOR_CONFIRMATION
- confirm + retry mesma Idempotency-Key → CONFIRMED, mesma lista (20 tarefas)
- CREATE_TASK confirm → template **id 33**, retry não duplicou
- Prompt injection na mesma frase → ainda CREATE_TASK “Limpeza” (não muda permissão)
- transcript vazio → 422
- unauth `/api/voice/status` → 401
- App `http://127.0.0.1:88/` → 200; actuator health → 200

## Mobile / PWA

- FAB 56px acima da nav + safe-area
- Inputs 48px / 16px
- PWA com cache **não** reativada (histórico iOS)
- Viewport `viewport-fit=cover`
- Validação visual autenticada (FAB + gravação no aparelho) **pendente de login humano**

## Dependências

- Spring Boot **permanece 3.3.3** (Boot 4 seria migração ampla)
- OpenAI via HTTP oficial (`/v1/audio/transcriptions`, `/v1/chat/completions`)
- vitest 2.0.5 (dev)

## Credencial ainda necessária

`VOICE_OPENAI_API_KEY` no `.env` deste projeto para transcrição de áudio real. Sem ela: **digite** o comando.

## Riscos

- Parser deterministic extrai título às vezes frágil (“Para os gerentes”)
- `OpsBackendApplicationTests` depende de DB local
- Foto obrigatória na voz exige upload na confirmação (`NEEDS_INPUT` + câmera)
- Cópia Windows não sincronizada
