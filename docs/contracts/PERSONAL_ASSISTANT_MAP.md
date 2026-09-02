# PERSONAL_ASSISTANT_MAP — TorqMind Ops

Mapa canônico da **assistente pessoal operacional** (voz + texto). Complementa `VOICE_COMMANDS.md` (contrato HTTP) e `SYSTEM_MAP.md` (arquitetura).

Timezone: `America/Sao_Paulo`. Idioma: **pt-BR** (pipeline de voz inteiro).

## Princípios

1. **Sempre responde** — consulta, ação, esclarecimento ou orientação honesta (nunca silêncio).
2. **Faz acontecer** — mutações passam pelos mesmos serviços da UI (`RoutineService`, `OccurrenceService`, `TaskDetailService`).
3. **Pergunta o que falta** — ambiguidade ou slot obrigatório vira pergunta falada; o usuário responde por voz (sem tocar em botão).
4. **Confirma o destrutivo** — `DELETE_TASK` e `REJECT_TASK` exigem confirmação falada ("sim" / "confirmo").
5. **Nunca inventa ID** — referências viram entidades só via `AuthorizedEntityResolver` (catálogo autorizado do tenant).
6. **Sem recuperação genérica** — fora do escopo: chat aberto estilo BI/vendas (produto `torqmind` Intelligence).

## Fluxo conversacional

```
Usuário fala/digita
  → POST /api/voice/drafts (ou PATCH com transcript se rascunho aberto)
  → intent (deterministic | openai) + resolve + missing/ambiguities
  → NEEDS_INPUT: pergunta falada + microfone reabre sozinho
  → READY (não destrutivo): executa na hora + fala resultado + escuta próximo comando
  → READY (destrutivo): pergunta confirmação → "sim" executa
```

Estados: ver `VOICE_COMMANDS.md`.

## Ações (`VoiceAction`)

| Ação | O que faz | Confirmação | Exemplos de fala |
|------|-----------|-------------|------------------|
| `HELP` | Resume o que a assistente faz | Não | "O que você faz?", "Ajuda", "O que posso pedir?" |
| `CREATE_TASK` | Cria rotina/tarefa recorrente | Não | "Crie uma tarefa de aferição para o gerente João amanhã às 8" |
| `CREATE_OCCURRENCE` | Abre ocorrência | Não | "Abra uma ocorrência crítica no posto norte informando bomba parada" |
| `OPEN_QUALITY_ANALYSIS` | Abre formulário análise combustível (não salva) | Não | "Abrir análise de qualidade do diesel S10" |
| `START_TASK` | Inicia run (`EM_ANDAMENTO`) | Não | "Inicie esta tarefa" (na tela da rotina) |
| `COMPLETE_TASK` | Conclui run (evidências se exigidas) | Não | "Conclua a aferição" |
| `REJECT_TASK` | Rejeita rotina ou ocorrência | **Sim** | "Rejeite a tarefa porque não deu tempo" |
| `ADD_COMMENT` | Comentário na thread | Não | "Adicione o comentário: bombas conferidas" |
| `OPEN_TASK` | Navega para rotina/ocorrência | Não | "Abra a tarefa de extintores" |
| `LIST_TASKS` | Lista runs (falado) | Não | "Quais tarefas estão atrasadas?", "O que está pendente hoje?" |
| `LIST_OCCURRENCES` | Lista ocorrências abertas (falado) | Não | "Verifique se tem ocorrências pendentes" |
| `QUERY_TASK` | Status por nome/responsável/data | Não | "O João executou a rotina de aferição hoje?" |
| `DELETE_TASK` | Soft-delete de template de rotina | **Sim** | "Exclua a rotina de extintores" |
| `START_OCCURRENCE` | Assume ocorrência (`EM_ATENDIMENTO`) | Não | "Assuma o chamado da bomba parada" |
| `CLOSE_OCCURRENCE` | Encerra ocorrência (fluxo de status) | Não | "Encerre a ocorrência do vazamento" |
| `LIST_MY_TASKS` | Tarefas atribuídas ao usuário | Não | "Minhas tarefas de hoje", "O que tenho pra fazer?" |
| `OPEN_NOTIFICATIONS` | Conta e resume avisos | Não | "Tem avisos novos?", "Minhas notificações" |
| `SUMMARY_TODAY` | Resumo operacional do dia | Não | "Como está a operação hoje?", "Resumo do dia" |
| `ADMIN_DENIED` | Recusa pedido administrativo | Não | "Cadastrar usuário", "Criar filial" → orienta usar Gestão |

### Aprendizado de frases (V26) — **desligado por padrão**

Flag `app.voice.phrase-learning-enabled` / `VOICE_PHRASE_LEARNING_ENABLED` (default `false`).

Quando ligado, grava em `voice_phrase_learnings` após confirmação. **Recomendado manter off** até validação em produção.

### Catálogo de conversas prontas

Arquivo curado `backend/src/main/resources/voice/ready_phrases.json` (~**134 frases**) — **não é aprendizado**; match exato (com strip de prefixos educados: "por favor", "oi", etc.) antes do parser regex. Testes: `VoiceReadyPhraseCatalogTest`.

### Slots comuns

| Slot | Quando | Pergunta típica |
|------|--------|-----------------|
| `title` | CREATE_TASK / CREATE_OCCURRENCE | "Qual o título da tarefa?" |
| `targetUserReference` | alvo USER | "Para qual pessoa?" |
| `targetSectorReference` | alvo SECTOR | "Qual setor?" |
| `branchReference` | multi-filial | "Qual filial?" |
| `startTime` / `dueTime` | CREATE_TASK | "Que horas começa / vence?" |
| `scheduledDate` | CREATE_TASK ONCE | "Para qual data?" |
| `comment` | ADD_COMMENT / REJECT | "Qual comentário?" |
| `photo` | COMPLETE com `requiresPhoto` | "Preciso de uma foto" (câmera na UI) |

Desambiguação: opções `{key, label}` — usuário pode responder com o **nome** ("Posto Centro"), **ordinal** ("a primeira", "opção 2") ou **número**.

Confirmação destrutiva: "sim", "confirmo", "pode", "ok", "isso" / negação: "não", "cancela".

## Papéis e escopo

| Papel | Consultar | Criar/editar via voz | Excluir rotina |
|-------|-----------|----------------------|----------------|
| MASTER | Tudo | Tudo no tenant | Sim |
| OWNER | Empresa | Empresa | Sim (templates da empresa) |
| MANAGER | Filial | Filial | Conforme `TenantResolver` |
| OPERATOR | Filial | Filial (tarefas próprias/nominais) | Não |

## O que a assistente **não** faz (resposta honesta)

- Vendas, financeiro, metas, lucro → use **TorqMind BI** (produto separado; HELP menciona deep link conceitual).
- Gestão admin (`/api/admin/**`), API keys, webhooks, **cadastro de usuários/filiais** → `ADMIN_DENIED` com mensagem por papel.
- Exclusão em massa ("apague todas") — recusada com mensagem falada.
- Wake word contínua em segundo plano (precisa abrir a assistente uma vez por sessão).

## Cenários de aceite (amostra)

1. **Ajuda** — "O que você faz?" → fala resumo de capacidades.
2. **Listar** — "Tem ocorrência aberta?" → fala contagem + primeiros títulos (não cria ocorrência).
3. **Criar com lacuna** — "Crie tarefa para o João" → pergunta título/horário → usuário responde falando → cria.
4. **Ambiguidade** — dois "João" → pergunta → "o da filial centro" → resolve.
5. **Consulta** — "A Maria concluiu a aferição hoje?" → resposta sim/não falada.
6. **Destrutivo** — "Exclua rotina extintores" → pede confirmação → "sim" → exclui.
8. **Assumir ocorrência** — "Assuma o chamado X" → `EM_ATENDIMENTO`.
9. **Encerrar** — "Encerre a ocorrência X" → fluxo até `ENCERRADA`.
10. **Resumo** — "Como está a operação hoje?" → contagens faladas.
11. **Admin negado** — "Cadastrar usuário" → recusa educada + orientação.
12. **Aprendizado** — desambigua "posto centro" → sucesso → próxima vez reconhece apelido.

## Implementação

- Backend: `VoiceDraftService`, `VoiceConversationResolver`, `DeterministicVoiceIntentProvider`, `VoiceCommandExecutor`
- Frontend: `VoiceSheet`, `voice/conversation.ts`, TTS `speak` + reabertura do microfone
- Testes: `DeterministicVoiceIntentProviderTest`, `VoiceGoldenPhrasesTest`, `VoiceConversationResolverTest`, `VoicePhraseLearningServiceTest`, `voice/conversation.test.ts`
