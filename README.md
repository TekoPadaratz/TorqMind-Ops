# TorqMind Ops SaaS

Fundação de produto SaaS para gestão operacional de redes de postos de combustível.

## Stack

- Backend: Java 21 + Spring Boot 3 + Flyway + PostgreSQL
- Frontend: React + Vite + TypeScript + PWA
- Infra: Docker Compose + Nginx

## Módulos

- Rotinas: execuções programadas com status PENDENTE, EM_ANDAMENTO, CONCLUIDA, ATRASADA, REJEITADA
- Ocorrências: fluxo reativo com status ABERTA, EM_ATENDIMENTO, AGUARDANDO_VALIDACAO, ENCERRADA, REJEITADA

## Regras críticas

- Multi-tenant por empresa e filial
- Notificação sem auto-retorno para quem executa a ação
- Abstração de arquivos via StorageProvider

## Como rodar

1. Copie .env.example para .env
2. Suba os serviços:

```bash
make up
```

3. Endpoints:

- App: http://localhost:88/
- Backend (via nginx): http://localhost:88/api/ops/notifications/should-notify?actorUserId=00000000-0000-0000-0000-000000000001&recipientUserId=00000000-0000-0000-0000-000000000002

## Desenvolvimento local

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## Testes

```bash
make backend-test
```

## Observações

- O StorageProvider atual usa LocalStorageProvider com raiz configurável por variável de ambiente.
- A integração JWT está preparada na configuração base e deve ser endurecida na fase seguinte com filtros e RBAC por escopo.
