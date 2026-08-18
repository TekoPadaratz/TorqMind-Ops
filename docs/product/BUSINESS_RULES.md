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

Não desativar a própria conta. Não desativar nem rebaixar o último MASTER ativo. Reset/troca invalida sessões JWT anteriores (`password_epoch`). Histórico registra ator, ação e horário — nunca a senha. Sem “esqueci minha senha” por e-mail nesta fase.

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
- Recorrência: `ONCE|DAILY|WEEKLY|MONTHLY|CUSTOM`; timezone `America/Sao_Paulo`; `business_days_only` adia fim de semana/feriado nacional BR.
- **Atraso e escalonamento**: ao vencer sem conclusão, o run vira `ATRASADA`, avisa o responsável e **escalona** para os gerentes da filial e os donos da empresa (uma vez).
- **Comprovante PDF**: `GET /api/routines/runs/{id}/report` gera um PDF de rastreabilidade da execução (status, responsável, horários, evidências/anexos com **carimbo data/hora** e **local (lat/lng)**, comentários e histórico). Respeita o tenant (mesmo controle de acesso do detalhe).
- **Geo na foto**: no upload de imagem, o app captura a geolocalização (best-effort; se negada/indisponível, envia sem geo) e grava `latitude/longitude` no anexo. Exibido como link de mapa na evidência e na coluna **Local** do comprovante PDF.
- **Offline (foto)**: sem conexão, a foto de evidência é salva localmente (IndexedDB) e reenviada automaticamente ao reconectar; o servidor **deduplica por checksum** (reenvio não duplica). Voz e demais ações continuam exigindo conexão (STT/interpretação no servidor).

## Ocorrências

Abrir no tenant. Transições via `StatusRules`. Comentário/anexo pelos mesmos endpoints de detalhe.

**Transformar em rotina**: `POST /api/occurrences/{id}/to-routine` cria uma rotina recorrente a partir da ocorrência (mesmo título/descrição/empresa/filial; recorrência + horários informados).

Tipo `FUEL_QUALITY_RECEIPT`: rascunho permanece `ABERTA`; o checkbox “Finalizar ocorrência ao salvar” vai a `ENCERRADA` (regra específica em `StatusRules`). Sem reprovação automática. Reabertura segue o fluxo genérico (hoje terminal). Snapshot do posto fica na análise.

## Notificações

`notifyCounterpart(actor, recipient, …)` — nunca o actor. MASTER ativos recebem cópia (fase de testes). Marcar lidas não apaga.

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
