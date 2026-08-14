# TorqMind Ops SaaS

Gestão operacional de redes de postos. App principal no celular. Homologação: porta host **88**.

## Stack

- Backend: Java 21 + Spring Boot 3.3.3 + Flyway + PostgreSQL
- Frontend: React + Vite + TypeScript
- Infra: Docker Compose + Nginx (`torqmind-ops-saas` apenas)

## Mapas

- `docs/architecture/SYSTEM_MAP.md`
- `docs/architecture/ENVIRONMENT_MAP.md`
- `docs/product/BUSINESS_RULES.md`
- `docs/contracts/VOICE_COMMANDS.md`
- `docs/engineering/HOMOLOGATION.md`
- `docs/engineering/VOICE_PROVIDER.md`

## Módulos

Rotinas, ocorrências, notificações, catálogo, **comandos por voz** (rascunho + confirmação).

## Como rodar

1. Copie `.env.example` para `.env` (não commite).
2. `cd /home/tm/torqmind-ops-saas && docker compose up --build -d`
3. App: `http://localhost:88/` (ou `http://task.torqmind.com.br`)

Nunca use `docker compose down` neste servidor compartilhado sem `-p torqmind-ops-saas` e certeza do diretório. Não toque nos stacks `torqmind` / `torqmind-homolog`.

## Voz

Botão 🎤 acima da navegação. Sem `VOICE_OPENAI_API_KEY`, grave **ou digite** o comando (provider `deterministic`). Com a chave, STT Whisper + interpretação JSON. Nenhuma ação mutável sem confirmar.

## Testes

```bash
make backend-test
cd frontend && npm test && npm run build
```

## Rollback

1. Reverter o commit/arquivos do Ops.
2. `docker compose up -d --build backend frontend` neste diretório.
3. Flyway V12 não deve ser revertida; se necessário, a tabela `voice_drafts` pode ficar vazia. Não rode `flyway clean` / `drop`.
4. Restaurar dump: `cat backup.sql | docker exec -i torqmind-ops-saas-postgres-1 psql -U postgres -d torqmind_ops`
