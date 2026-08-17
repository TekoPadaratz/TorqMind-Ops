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

## Ocorrências

Abrir no tenant. Transições via `StatusRules`. Comentário/anexo pelos mesmos endpoints de detalhe.

Tipo `FUEL_QUALITY_RECEIPT`: rascunho permanece `ABERTA`; o checkbox “Finalizar ocorrência ao salvar” vai a `ENCERRADA` (regra específica em `StatusRules`). Sem reprovação automática. Reabertura segue o fluxo genérico (hoje terminal). Snapshot do posto fica na análise.

## Notificações

`notifyCounterpart(actor, recipient, …)` — nunca o actor. MASTER ativos recebem cópia (fase de testes). Marcar lidas não apaga.

## Auditoria

`task_activities` registra ator, de/para, mensagem. Voz grava origem `VOICE` no rascunho e mensagem de atividade. Sem áudio/segredos em log.

## A voz jamais pode

Acessar outro tenant; atribuir fora do catálogo autorizado; executar por outro responsável; aceitar PDF/MIME falso como foto; pular transição; concluir sem evidência; inventar entidade; resolver ambiguidade sozinha; duplicar por retry; gravar no banco fora dos serviços.
