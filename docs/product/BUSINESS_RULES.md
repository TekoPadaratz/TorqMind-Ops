# BUSINESS_RULES — TorqMind Ops

A voz **não** cria atalho. Mesmos serviços da UI.

## Papéis

| Papel | Escopo |
|-------|--------|
| MASTER | Admin global; menu Gestão; `/api/admin/**`; pode executar tarefa nominal (homologação) |
| OWNER | Toda a empresa |
| MANAGER | Só a filial do JWT |
| OPERATOR | Só a filial do JWT |

Gerente/funcionário: create força `branchId` do usuário. Catálogo de alvos **não lista MASTER**.

## Cadastro e senha

Só MASTER cria/edita usuário, redefine senha, desbloqueia e vê o histórico. Usuário comum troca a própria senha em `/account` (senha atual + nova + confirmação). Política: mínimo 8 caracteres, letra e número. Username não muda depois do cadastro.

Não desativar a própria conta. Não desativar nem rebaixar o último MASTER ativo. Reset/troca invalida sessões JWT anteriores (`password_epoch`). Histórico registra ator, ação e horário — nunca a senha. **Recuperação por e-mail**: disponível quando o usuário tem e-mail cadastrado e o SMTP está configurado (link com token de uso único, válido 1h; nunca revela se o e-mail existe).

**Verificação em duas etapas (2FA/TOTP)**: opcional e por usuário (qualquer papel pode ativar em `/account`; indicada para MASTER/OWNER). Ativação exige confirmar um código do app autenticador; desativação exige um código válido (posse do dispositivo). Login com 2FA ativo pede o código após a senha (desafio de 5 min). O desafio não autentica requisições. Erros de código contam para o bloqueio por tentativas. Recuperação de usuário travado: só MASTER remove o 2FA do usuário.

## Isolamento

Listagens e resolução de nomes: empresa do JWT; MANAGER/OPERATOR filtram filial.
Acesso por ID de outra empresa/filial → 403. MASTER vê tudo.

## Tarefas (rotinas)

- Criar: OWNER/MANAGER/MASTER (e OPERATOR se a UI permitir o form — o backend não bloqueia por papel além do tenant).
- Alvos: `USER | SECTOR | MANAGERS | ALL` dentro do tenant.
- Nominal (`assignedUserId` preenchido): **somente o responsável** inicia (`EM_ANDAMENTO`) ou conclui (`CONCLUIDA`), exceto MASTER.
- Rejeitar: responsável, gerente/dono da mesma filial/empresa, ou MASTER.
- Transições: só `StatusRules`.
- `requiresComment`: comentário na thread ou no payload da transição.
- `requiresPhoto`: ao menos um anexo cuja **assinatura de bytes** seja imagem (JPEG/PNG/WEBP/GIF). PDF não conta como foto.
- **Checklist (parametrizável por empresa)**: se `company_settings.checklists_enabled`, a tarefa pode ter itens de checklist (subtarefas). Cada item é obrigatório ou opcional; a conclusão (`CONCLUIDA`) exige **todos os itens obrigatórios marcados**, além de foto/comentário quando exigidos. Os itens são copiados (snapshot) para cada execução na geração; só o responsável (ou MASTER) marca. Endpoints: `GET/POST /api/routines/runs/{id}/checklist[/{itemId}]`. Aparecem no comprovante PDF.
- Recorrência: `ONCE|DAILY|WEEKLY|MONTHLY|CUSTOM`; timezone `America/Sao_Paulo`; `business_days_only` adia fim de semana/feriado nacional BR.
- **Atraso e escalonamento**: ao vencer sem conclusão, o run vira `ATRASADA`, avisa o responsável e **escalona** para os gerentes da filial e os donos da empresa (uma vez).
- **Comprovante PDF**: `GET /api/routines/runs/{id}/report` gera um PDF de rastreabilidade da execução (status, responsável, horários, evidências/anexos com **carimbo data/hora** e **local (lat/lng)**, comentários e histórico). Respeita o tenant (mesmo controle de acesso do detalhe).
- **Geo na foto**: no upload de imagem, o app captura a geolocalização (best-effort; se negada/indisponível, envia sem geo) e grava `latitude/longitude` no anexo. Exibido como link de mapa na evidência e na coluna **Local** do comprovante PDF.
- **Offline (foto)**: sem conexão, a foto de evidência é salva localmente (IndexedDB) e reenviada automaticamente ao reconectar; o servidor **deduplica por checksum** (reenvio não duplica). Voz e demais ações continuam exigindo conexão (STT/interpretação no servidor).
- **Calendário**: `GET /api/routines/runs/calendar?from=&to=` devolve as execuções do período (título, status, vencimento, responsável), respeitando tenant/filial. A UI mostra uma agenda mensal navegável agrupada por dia; cada item abre o detalhe da tarefa.
- **Operações em lote (UI, não por voz)**: ambas exigem entrar num **modo explícito** (não poluem a revisão diária). Excluir várias rotinas programadas — modo "Excluir várias" no card *Rotinas programadas* → `POST /api/routines/templates/bulk-delete` (MASTER/OWNER; soft-delete item a item, ignora as sem permissão, retorna `deleted`/`failed`). Reatribuir várias tarefas — modo "Reatribuir" no card *Tarefas*: **só tarefas em aberto** (`PENDENTE|EM_ANDAMENTO|ATRASADA`) podem ser marcadas; concluídas e rejeitadas aparecem esmaecidas e **não são selecionáveis** (a UI espelha a regra do servidor). `POST /api/routines/runs/bulk-reassign` (MASTER/OWNER/MANAGER; valida o usuário destino, **pula** runs de outra empresa/filial, com filial incompatível, já concluídas/rejeitadas ou já atribuídas ao próprio destino; retorna `{reassigned, skipped}`). Cada item é sua própria transação (uma falha não desfaz as demais). Cada reatribuição grava no **histórico** a atividade `REASSIGNED` com "de {anterior} para {novo}" e notifica o novo responsável. A **voz** continua recusando exclusão em massa.

## Ocorrências

Abrir no tenant. Transições via `StatusRules`. Comentário/anexo pelos mesmos endpoints de detalhe.

**Transformar em rotina**: `POST /api/occurrences/{id}/to-routine` cria uma rotina recorrente a partir da ocorrência (mesmo título/descrição/empresa/filial; recorrência + horários informados).

Tipo `FUEL_QUALITY_RECEIPT`: rascunho permanece `ABERTA`; o checkbox “Finalizar ocorrência ao salvar” vai a `ENCERRADA` (regra específica em `StatusRules`). Sem reprovação automática. Reabertura segue o fluxo genérico (hoje terminal). Snapshot do posto fica na análise.

## Relatórios e exportações

- **Relatório operacional (PDF)**: `GET /api/dashboard/report.pdf?from=&to=` gera um PDF do período com rotinas agendadas no intervalo e ocorrências abertas no intervalo: KPIs (total, concluídas, % no prazo, pendentes/andamento/atrasadas/rejeitadas), envelhecimento dos atrasos (situação atual), ranking por filial (só na visão da empresa) e a lista de tarefas atrasadas. Respeita o tenant: MANAGER/OPERATOR só a filial do JWT; OWNER/MASTER a empresa inteira.
- **CSV**: rotinas em `GET /api/routines/runs/export.csv` e ocorrências em `GET /api/occurrences/export.csv` (delimitador `;`, BOM UTF-8 para Excel pt-BR), respeitando os mesmos filtros de tenant e o filtro de status opcional.

## Notificações

`notifyCounterpart(actor, recipient, …)` — nunca o actor. MASTER ativos recebem cópia (fase de testes). Marcar lidas não apaga.

**Notificações push (Web Push)**: opt-in por dispositivo em Conta (qualquer papel). O app pede a permissão do navegador e registra um Service Worker **só-push** (não cacheia nada). O servidor guarda a inscrição por endpoint (uma por dispositivo) e, a cada notificação in-app criada, dispara também um push **best-effort** (nunca bloqueia nem quebra o fluxo; inscrições mortas 404/410 são removidas). Chaves VAPID são geradas e guardadas no servidor (privada cifrada), sem serviço de terceiros e sem custo. No iPhone, só funciona com o app instalado na tela de início (iOS 16.4+).

**Tempo real (SSE)**: com o app **aberto**, o sino e a lista de avisos atualizam ao vivo por `GET /api/events/stream` (conexão autenticada por Bearer via fetch-streaming; sem token na URL). O servidor mantém as conexões em memória por usuário (máx. 5/dispositivos, heartbeat de 25s) e publica um evento a cada notificação criada. Se o stream cair, o app reconecta com backoff e o polling de 30s cobre o intervalo — nunca depende só do SSE.

## Auditoria

`task_activities` registra ator, de/para, mensagem. Voz grava origem `VOICE` no rascunho e mensagem de atividade. Sem áudio/segredos em log.

## A voz jamais pode

Acessar outro tenant; atribuir fora do catálogo autorizado; executar por outro responsável; aceitar PDF/MIME falso como foto; pular transição; concluir sem evidência; inventar entidade; resolver ambiguidade sozinha; duplicar por retry; gravar no banco fora dos serviços; **exclusão em massa** (recusada, mesmo MASTER/OWNER).

## Voz conversacional e destrutivos (v2)

- Criar/consultar/listar: **execução direta** (sem confirmação) e o app **fala** a resposta (TTS pt-BR).
- Excluir/rejeitar: **destrutivo → pede confirmação** (fala a pergunta). Excluir só rotina (soft-delete) com permissão: MASTER/OWNER na empresa; criador só a que criou; executor/operador não.
- Exclusão ambígua (mesmo nome em filiais/usuários) → pergunta "de qual filial/usuário?".
- Consulta por nome: "o Alfredo executou a rotina X hoje?" → responde o status falando.
- Config por empresa (só MASTER, `company_settings`): foto/comentário obrigatórios + lembrete padrão; a voz usa esses defaults quando não ditos.
