package com.torqmind.ops.application.voice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VoiceProviderConfig {

    @Bean
    public DeterministicVoiceTranscriptionProvider deterministicVoiceTranscriptionProvider() {
        return new DeterministicVoiceTranscriptionProvider();
    }

    @Bean
    public DeterministicVoiceIntentProvider deterministicVoiceIntentProvider(VoiceReadyPhraseCatalog readyPhrases) {
        return new DeterministicVoiceIntentProvider(readyPhrases);
    }

    @Bean
    public OpenAiVoiceTranscriptionProvider openAiVoiceTranscriptionProvider(VoiceProperties properties) {
        return new OpenAiVoiceTranscriptionProvider(properties);
    }

    @Bean
    public OpenAiVoiceIntentProvider openAiVoiceIntentProvider(VoiceProperties properties, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new OpenAiVoiceIntentProvider(properties, mapper);
    }

    @Bean
    @Primary
    public VoiceTranscriptionProvider voiceTranscriptionProvider(
            VoiceProperties properties,
            DeterministicVoiceTranscriptionProvider deterministic,
            OpenAiVoiceTranscriptionProvider openai
    ) {
        String name = properties.getTranscriptionProvider() == null ? "deterministic" : properties.getTranscriptionProvider();
        if ("openai".equalsIgnoreCase(name) && properties.hasOpenaiKey()) {
            return openai;
        }
        return deterministic;
    }

    @Bean
    @Primary
    public VoiceIntentProvider voiceIntentProvider(
            VoiceProperties properties,
            DeterministicVoiceIntentProvider deterministic,
            OpenAiVoiceIntentProvider openai
    ) {
        String name = properties.getIntentProvider() == null ? "deterministic" : properties.getIntentProvider();
        if ("openai".equalsIgnoreCase(name) && properties.hasOpenaiKey()) {
            return openai;
        }
        return deterministic;
    }
}
