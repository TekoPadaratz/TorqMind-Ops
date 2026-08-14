package com.torqmind.ops.application.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

public class OpenAiVoiceTranscriptionProvider implements VoiceTranscriptionProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiVoiceTranscriptionProvider.class);

    private final VoiceProperties properties;

    public OpenAiVoiceTranscriptionProvider(VoiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String transcribe(byte[] audio, String filename, String declaredMime) {
        if (!properties.hasOpenaiKey()) {
            throw new VoiceUnavailableException("Comando por voz indisponível: configure VOICE_OPENAI_API_KEY.");
        }
        String name = (filename == null || filename.isBlank()) ? "audio.webm" : filename;
        ByteArrayResource file = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return name;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", properties.getTranscribeModel());
        body.add("language", "pt");
        body.add("response_format", "json");
        body.add("file", file);

        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Map<?, ?> json = client().post()
                        .uri("/audio/transcriptions")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                if (json == null || json.get("text") == null) {
                    throw new VoiceUnavailableException("A transcrição não retornou texto.");
                }
                return String.valueOf(json.get("text")).trim();
            } catch (RestClientException ex) {
                last = ex;
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                boolean retry = attempt == 0 && (msg.contains("429") || msg.contains("503") || msg.contains("502"));
                log.warn("voice.stt failed attempt={}", attempt);
                if (!retry) {
                    break;
                }
            }
        }
        if (last != null) {
            log.warn("voice.stt exhausted");
        }
        throw new VoiceUnavailableException("Não foi possível transcrever o áudio agora. Tente de novo ou digite o comando.");
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(properties.getTranscribeTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getOpenaiBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + properties.getOpenaiApiKey())
                .build();
    }
}
