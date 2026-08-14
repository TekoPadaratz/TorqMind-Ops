# EXECUTION_PLAN

Uma etapa principal por vez. Fonte de verdade: servidor `/home/tm/torqmind-ops-saas`.

## Decisões

- Spring Boot permanece **3.3.3** (Boot 4.x seria migração ampla e bloquearia a voz).
- PWA com cache **não** reativada (histórico de SW preso no iOS). Manifesto mínimo + `viewport-fit=cover` apenas.
- Sem credencial OpenAI no ambiente: arquitetura + provider real + **deterministic** para testes/homologação textual. Variável: `VOICE_OPENAI_API_KEY`.
- Cópia Windows `C:\TorqMind-Ops` fora de alcance; não sobrescrever nada lá.

## Etapas

1. [x] Mapear Git/Docker/isolamento
2. [x] Contratos e mapas
3. [x] Backend voz + autorização compartilhada + Flyway V12
4. [x] Frontend mobile (FAB, gravação, prévia, foto)
5. [x] Testes (27 backend + 1 frontend)
6. [x] Build/testes reais
7. [x] Deploy só compose `torqmind-ops-saas` + backup Flyway
8. [x] Comparar containers protegidos (intocados)

## Decisões extras na execução

- Listas imutáveis do interpretador: rascunho sempre recopia para `ArrayList` antes de mutar.
- Sanitização de injection remove trechos, não a frase inteira.
- Teste de contexto Spring excluído do `Dockerfile` (precisa do Postgres do Compose).

## Riscos

- Ambiguidade de nomes comuns (“João”) exige escolha na UI.
- Sem API key, STT real não funciona; alternativa manual (texto) sim.
- `navigate(-1)` no sino já existente pode sair do app se não houver histórico.
