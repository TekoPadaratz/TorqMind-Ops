# Homologação e sincronização

Fonte de verdade: `/home/tm/torqmind-ops-saas` no host `torqmind-app-stream`.

`C:\TorqMind-Ops` não está acessível daqui. Copiar depois para o Windows **sem** `--delete`, sem `.env` / `secrets/` / `node_modules` / `backend/target`.

## Backup antes de migrar

```bash
cd /home/tm/torqmind-ops-saas
docker exec torqmind-ops-saas-postgres-1 pg_dump -U postgres torqmind_ops > /tmp/torqmind-ops-pre-voice.sql
```

## Deploy (só este compose)

```bash
cd /home/tm/torqmind-ops-saas
docker compose up -d --build backend frontend
docker compose up -d --force-recreate nginx
```

Flyway V12 sobe com o backend. Conferir `flyway_schema_history`.

## Isolamento

Antes/depois: IDs e `StartedAt` de `torqmind-web`, `torqmind-api`, `torqmind-nginx`, `*-homolog`, `torqmind-debezium`, `torqmind-redpanda` devem ser iguais.

## Checklist

- Health backend deste stack
- Login
- Criar tarefa manual
- Sino / avisos
- Voz: texto “mostre minhas tarefas atrasadas” → prévia → confirmar
- Foto real (não PDF) na conclusão
- Celular / viewport estreito
