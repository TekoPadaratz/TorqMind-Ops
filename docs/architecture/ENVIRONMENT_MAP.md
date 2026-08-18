# ENVIRONMENT_MAP — TorqMind Ops

Sem segredos. Atualizar após cada deploy.

## Cópia local vs servidor

| Item | Valor |
|------|--------|
| Workspace deste agente | `/home/tm/torqmind-ops-saas` no host `torqmind-app-stream` (user `tm`) |
| `C:\TorqMind-Ops` | **Não acessível** deste ambiente. Não há `~/.ssh/config` nem rota até a cópia Windows. |
| Fonte de verdade nesta execução | **Servidor:** `/home/tm/torqmind-ops-saas` |
| Git remote | `https://github.com/TekoPadaratz/TorqMind-Ops.git` |
| Branch / commit base | `main` @ `36d914e` (tracking `origin/main`) |
| Trabalho local não commitado (preservar) | notificações mark-read, filtro MASTER no catálogo, layout mobile de horários |

Não há segundo clone Git do Ops neste host. O “remoto SSH” **é este diretório**. Sincronizar com Windows só depois, arquivo a arquivo, sem `--delete`.

## Compose exclusivo do Ops

- Project: `torqmind-ops-saas`
- Working dir: `/home/tm/torqmind-ops-saas`
- Arquivo: `docker-compose.yml`
- Containers: `torqmind-ops-saas-{postgres,backend,frontend,nginx}-1`
- Porta pública: **88→80**
- DB: `torqmind_ops` (volume `torqmind-ops-saas_pgdata`)
- Flyway aplicado até **V21** (e-mail do usuário + tokens de recuperação de senha); checklists = V20; segredo TOTP cifrado = V19; 2FA opt-in = V18; geolocalização dos anexos = V17; config por empresa = V16; senha = V15; voz = V12; qualidade combustível = V14
- E-mail (opcional, best-effort): SMTP via env `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` + `MAIL_FROM` + `PUBLIC_BASE_URL`. Sem SMTP configurado, os envios são apenas registrados (nada quebra).
- URL homologação: `http://task.torqmind.com.br` (porta 88 / host deste stack)

## Serviços PROTEGIDOS (outro TorqMind) — não tocar

Snapshot **antes** da execução (2026-08-14):

| Container | Compose | Porta | ID curto |
|-----------|---------|-------|----------|
| torqmind-web | torqmind | 3000 | 06a8e094f9a0 |
| torqmind-api | torqmind | 8000 | 32cc8707269b |
| torqmind-nginx | torqmind | **80** | 13dd21fd2ce0 |
| torqmind-web-homolog | torqmind-homolog | 3000 | 8f635f72c894 |
| torqmind-api-homolog | torqmind-homolog | 8000 | ebaa07bf7351 |
| torqmind-nginx-homolog | torqmind-homolog | 127.0.0.1:81 | 568473e3dc63 |
| torqmind-debezium | torqmind | 18083 | d24b1e8af61c |
| torqmind-redpanda | torqmind | — | 354c40ec5264 |

Working dirs protegidos: `/home/tm/torqmind`, `/home/tm/worktrees/torqmind-hardening-2026-08`.

Evidência pós-deploy: mesmos IDs e `StartedAt` dos 8 containers acima.

## Implantação segura

1. Só `cd /home/tm/torqmind-ops-saas && docker compose up -d --build backend frontend` (+ recreate **nginx deste projeto** se IPs Docker mudarem).
2. Nunca `docker compose down`, `down -v`, `system prune`, stop por nome “torqmind”.
3. Backup DB antes de Flyway: `docker exec torqmind-ops-saas-postgres-1 pg_dump -U postgres torqmind_ops`
4. Não copiar `.env` / `secrets/` entre máquinas em logs ou git.
