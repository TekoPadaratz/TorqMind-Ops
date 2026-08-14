# TorqMind Ops

Agendador operacional para redes de postos. O dono da rede e sua equipe criam tarefas por empresa/filial, atribuem responsáveis, acompanham prazos e exigem evidências antes da conclusão. Homologação pública na porta host **88**.

## O que o produto cobre

- Tarefas únicas ou recorrentes: diária, semanal, mensal e dias personalizados.
- Destino por usuário, setor, gerentes ou toda a equipe da filial.
- Janela de início/vencimento, lembrete e marcação automática de atraso.
- Conclusão condicionada a comentário e/ou foto, conforme definido na criação.
- Histórico de criação, mudança de status, comentário e anexo.
- Ocorrências com atendimento e validação.
- Notificações internas navegáveis e separadas por usuário.
- Isolamento por empresa e filial em listagens e acessos diretos por ID.
- Comandos por voz (rascunho + confirmação); sem atalho de permissão.

Uma exigência de foto só é satisfeita por uma imagem real (JPEG, PNG, GIF, WebP ou HEIC). PDF continua disponível como anexo, mas não libera a conclusão.
Quando a tarefa tem um responsável nominal, somente ele pode iniciar/concluir a execução; foto e comentário de terceiros continuam no histórico, mas não satisfazem a comprovação obrigatória. MASTER pode executar na homologação.

## Papéis e escopo

- `MASTER` (Administrador): gestão global.
- `OWNER` (Dono da empresa): todas as filiais da própria empresa.
- `MANAGER` (Gerente): somente a filial vinculada.
- `OPERATOR` (Funcionário): somente a filial vinculada.

Todas as autorizações são verificadas novamente no backend. Esconder um botão na interface não é tratado como segurança. Catálogo de alvos não lista MASTER.

## Stack

- Backend: Java 21 + Spring Boot 3.5.16 + Flyway + PostgreSQL
- Frontend: React + Vite + TypeScript
- Arquivos: armazenamento local ou Google Drive por `StorageProvider`
- Infraestrutura: Docker Compose e Nginx do projeto `torqmind-ops-saas` (porta `88`)

## Mapas

- `docs/architecture/SYSTEM_MAP.md`
- `docs/architecture/ENVIRONMENT_MAP.md`
- `docs/product/BUSINESS_RULES.md`
- `docs/contracts/VOICE_COMMANDS.md`
- `docs/engineering/HOMOLOGATION.md`
- `docs/engineering/VOICE_PROVIDER.md`

## Primeiro início seguro

1. Copie `.env.example` para `.env` (não commite).
2. Substitua `JWT_SECRET` por um valor aleatório com ao menos 32 caracteres.
3. Em banco novo, preencha temporariamente `BOOTSTRAP_ADMIN_USERNAME` e `BOOTSTRAP_ADMIN_PASSWORD`.
4. Suba somente este projeto, neste diretório:

```bash
cd /home/tm/torqmind-ops-saas && docker compose up --build -d
```

5. Abra [http://localhost:88](http://localhost:88) ou `https://task.torqmind.com.br/`.
6. Após o primeiro acesso, apague as variáveis `BOOTSTRAP_ADMIN_*` do `.env` e recrie apenas o backend.

O backend recusa segredos JWT curtos ou os valores de exemplo. A senha inicial precisa ter ao menos oito caracteres, com letras e números.

Nunca use `docker compose down` neste servidor compartilhado sem `-p torqmind-ops-saas` e certeza do diretório. Não toque nos stacks `torqmind` / `torqmind-homolog`.

## Voz

Botão 🎤 acima da navegação. Sem `VOICE_OPENAI_API_KEY`, grave **ou digite** o comando (provider `deterministic`). Com a chave, STT Whisper + interpretação JSON. Nenhuma ação mutável sem confirmar. A transcrição jamais decide permissões.

## Desenvolvimento local

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend (o Vite encaminha `/api` para `localhost:8080`):

```bash
cd frontend
npm ci
npm run dev
```

## Validação

```bash
cd backend && mvn test && mvn -q -DskipTests package
cd frontend && npm ci && npm test && npm run build && npm audit
```

## Rollback

1. Reverter o commit/arquivos do Ops.
2. `docker compose up -d --build backend frontend` neste diretório (+ recreate do nginx deste compose se o backend mudar de IP).
3. Flyway V12 (`voice_drafts`) não deve ser revertida. V13 só aplica constraints `NOT VALID`.
4. Restaurar dump: `cat backup.sql | docker exec -i torqmind-ops-saas-postgres-1 psql -U postgres -d torqmind_ops`
