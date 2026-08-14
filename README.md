# TorqMind Ops

Agendador operacional para redes de postos. O dono da rede e sua equipe criam tarefas por empresa/filial, atribuem responsáveis, acompanham prazos e exigem evidências antes da conclusão.

## O que o produto cobre

- Tarefas únicas ou recorrentes: diária, semanal, mensal e dias personalizados.
- Destino por usuário, setor, gerentes ou toda a equipe da filial.
- Janela de início/vencimento, lembrete e marcação automática de atraso.
- Conclusão condicionada a comentário e/ou foto, conforme definido na criação.
- Histórico de criação, mudança de status, comentário e anexo.
- Ocorrências com atendimento e validação.
- Notificações internas navegáveis e separadas por usuário.
- Isolamento por empresa e filial em listagens e acessos diretos por ID.

Uma exigência de foto só é satisfeita por uma imagem real (JPEG, PNG, GIF, WebP ou HEIC). PDF continua disponível como anexo, mas não libera a conclusão.
Quando a tarefa tem um responsável nominal, somente ele pode iniciar/concluir a execução; foto e comentário de terceiros continuam no histórico, mas não satisfazem a comprovação obrigatória.

## Papéis e escopo

- `MASTER` (Administrador): gestão global.
- `OWNER` (Dono da empresa): todas as filiais da própria empresa.
- `MANAGER` (Gerente): somente a filial vinculada.
- `OPERATOR` (Funcionário): somente a filial vinculada.

Todas as autorizações são verificadas novamente no backend. Esconder um botão na interface não é tratado como segurança.

## Stack

- Backend: Java 21, Spring Boot, Spring Security, JPA, Flyway e PostgreSQL.
- Frontend: React, TypeScript e Vite.
- Arquivos: armazenamento local ou Google Drive por `StorageProvider`.
- Infraestrutura: Docker Compose e Nginx, publicados na porta `88`.

## Primeiro início seguro

1. Copie `.env.example` para `.env`.
2. Substitua `JWT_SECRET` por um valor aleatório com ao menos 32 caracteres.
3. Em banco novo, preencha temporariamente `BOOTSTRAP_ADMIN_USERNAME` e `BOOTSTRAP_ADMIN_PASSWORD`.
4. Suba somente este projeto:

```bash
docker compose up --build -d
```

5. Abra [http://localhost:88](http://localhost:88).
6. Após o primeiro acesso, apague as variáveis `BOOTSTRAP_ADMIN_*` do `.env` e recrie apenas o backend.

O backend recusa segredos JWT curtos ou os valores de exemplo. A senha inicial precisa ter ao menos oito caracteres, com letras e números.

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
cd backend && mvn test
cd frontend && npm ci && npm run build && npm audit
MASTER_USER=admin MASTER_PASSWORD='sua-senha' BASE=http://localhost:88 ./scripts/e2e_smoke.sh
MASTER_USER=admin MASTER_PASSWORD='sua-senha' BASE=http://localhost:88 ./scripts/uat_scenario.sh
```

Os roteiros ponta a ponta não guardam credenciais reais no repositório e retornam erro quando algum cenário falha.

## Próxima fase: comandos de voz

A voz deve ser uma nova entrada para as mesmas regras já existentes, nunca um segundo caminho privilegiado. A implementação recomendada é:

1. Capturar ou enviar o áudio pelo celular.
2. Transcrever e transformar a fala em uma intenção estruturada (ação, título, filial, responsável, data, horário e exigências).
3. Resolver nomes ambíguos contra o catálogo autorizado daquele usuário.
4. Mostrar uma prévia curta para confirmação: “Criar tarefa X para gerente Y, amanhã às 8h, exigindo foto e comentário?”.
5. Após a confirmação, chamar o mesmo serviço de criação usado pela tela.
6. Registrar no histórico quem falou, a intenção confirmada e o resultado, sem armazenar áudio indefinidamente por padrão.

Comandos ambíguos, responsáveis duplicados ou datas incompletas devem pedir confirmação. A transcrição jamais decide permissões.
