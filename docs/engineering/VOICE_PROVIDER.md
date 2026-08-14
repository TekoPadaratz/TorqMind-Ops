# Provedor de voz

Chaves **somente no backend**. Frontend nunca recebe `VOICE_OPENAI_API_KEY`.

## Variáveis

| Variável | Padrão | Uso |
|----------|--------|-----|
| `VOICE_ENABLED` | true | Desliga só a voz; UI tradicional segue |
| `VOICE_TRANSCRIPTION_PROVIDER` | deterministic | `openai` se houver chave |
| `VOICE_INTENT_PROVIDER` | deterministic | `openai` se houver chave |
| `VOICE_OPENAI_API_KEY` | vazio | Obrigatória para STT/LLM reais |
| `VOICE_OPENAI_BASE_URL` | https://api.openai.com/v1 | Oficial |
| `VOICE_TRANSCRIBE_MODEL` | whisper-1 | POST `/audio/transcriptions` |
| `VOICE_INTENT_MODEL` | gpt-4o-mini | POST `/chat/completions` JSON |

Documentação oficial consultada: [Create transcription](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create) e [File transcription](https://developers.openai.com/api/docs/guides/speech-to-text).

## Homologação sem chave

O ambiente atual usa `deterministic`: o usuário **digita** o comando (ou envia texto). Áudio sem chave devolve mensagem clara pedindo texto ou a variável.

Para STT real, preencha `VOICE_OPENAI_API_KEY` no `.env` deste projeto, rebuild **somente** `backend` do compose `torqmind-ops-saas`.
