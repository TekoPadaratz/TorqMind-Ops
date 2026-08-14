# Provedor de voz

Chaves **somente no backend**. Frontend nunca recebe `VOICE_OPENAI_API_KEY`.

## Modo gratuito (padrão)

O frontend usa a implementação `SpeechRecognition`/`webkitSpeechRecognition` disponível no navegador do celular e envia somente o texto reconhecido para `/api/voice/drafts`. O backend interpreta com o provider `deterministic`. Esse fluxo não chama a OpenAI e não consome seus créditos.

Se o navegador não suportar reconhecimento, o campo digitado continua disponível. A OpenAI é apenas um provider opcional para ambientes que decidirem pagar por transcrição/interpretação no futuro.

## Variáveis

| Variável | Padrão | Uso |
|----------|--------|-----|
| `VOICE_ENABLED` | true | Desliga só a voz; UI tradicional segue |
| `VOICE_TRANSCRIPTION_PROVIDER` | deterministic | `openai` se houver chave |
| `VOICE_INTENT_PROVIDER` | deterministic | `openai` se houver chave |
| `VOICE_OPENAI_API_KEY` | vazio | Opcional; não é usada no modo gratuito |
| `VOICE_OPENAI_BASE_URL` | https://api.openai.com/v1 | Oficial |
| `VOICE_TRANSCRIBE_MODEL` | whisper-1 | POST `/audio/transcriptions` |
| `VOICE_INTENT_MODEL` | gpt-4o-mini | POST `/chat/completions` JSON |

Documentação oficial consultada: [Create transcription](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create) e [File transcription](https://developers.openai.com/api/docs/guides/speech-to-text).

## Homologação gratuita

Mantenha `VOICE_TRANSCRIPTION_PROVIDER=deterministic` e `VOICE_INTENT_PROVIDER=deterministic`. O usuário fala pelo reconhecimento do navegador ou digita o comando. Em ambos os casos, o backend recebe texto e aplica as mesmas regras, prévia e confirmação.

Para usar OpenAI opcionalmente, configure a chave e altere explicitamente o provider desejado para `openai`; rebuild somente o backend do compose `torqmind-ops-saas`.
