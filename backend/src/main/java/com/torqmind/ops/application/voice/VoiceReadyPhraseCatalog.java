package com.torqmind.ops.application.voice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo curado de frases prontas (não é aprendizado automático).
 */
@Component
public class VoiceReadyPhraseCatalog {

    private static final Logger log = LoggerFactory.getLogger(VoiceReadyPhraseCatalog.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Object>> exact = new LinkedHashMap<>();

    public VoiceReadyPhraseCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        load();
    }

    public void load() {
        try (InputStream in = new ClassPathResource("voice/ready_phrases.json").getInputStream()) {
            List<Map<String, Object>> rows = objectMapper.readValue(in, new TypeReference<>() {});
            for (Map<String, Object> row : rows) {
                Object phrase = row.get("phrase");
                if (phrase == null) {
                    continue;
                }
                String key = VoicePhraseNormalizer.normalize(String.valueOf(phrase));
                if (!key.isBlank()) {
                    exact.put(key, row);
                }
            }
            log.info("voice.ready phrases loaded count={}", exact.size());
        } catch (Exception ex) {
            log.warn("voice.ready failed to load catalog: {}", ex.getMessage());
        }
    }

    public Optional<VoiceIntent> match(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return Optional.empty();
        }
        for (String variant : phraseVariants(transcript)) {
            Map<String, Object> row = exact.get(variant);
            if (row != null) {
                return Optional.of(buildIntent(transcript, row));
            }
        }
        return Optional.empty();
    }

    private static List<String> phraseVariants(String transcript) {
        String normalized = VoicePhraseNormalizer.normalize(transcript);
        java.util.LinkedHashSet<String> variants = new java.util.LinkedHashSet<>();
        variants.add(normalized);
        if (normalized.endsWith("?")) {
            variants.add(normalized.substring(0, normalized.length() - 1).trim());
        }
        if (normalized.endsWith(".")) {
            variants.add(normalized.substring(0, normalized.length() - 1).trim());
        }
        for (String prefix : List.of(
                "por favor", "por gentileza", "favor", "oi", "ola", "olá", "ei",
                "e ai", "e aí", "beleza", "ok", "então", "entao"
        )) {
            if (normalized.equals(prefix)) {
                continue;
            }
            if (normalized.startsWith(prefix)) {
                String rest = normalized.substring(prefix.length()).replaceFirst("^[\\s,;:.-]+", "").trim();
                if (!rest.isBlank()) {
                    variants.add(rest);
                }
            }
        }
        return List.copyOf(variants);
    }

    private VoiceIntent buildIntent(String transcript, Map<String, Object> row) {
        VoiceIntent intent = new VoiceIntent();
        intent.setSchemaVersion(VoiceIntent.SCHEMA_VERSION);
        intent.setTranscript(transcript);
        intent.setRequiresConfirmation(false);
        intent.setConfidence(0.94);
        applyRow(intent, row);
        if ("DELETE_TASK".equals(intent.getAction()) || "REJECT_TASK".equals(intent.getAction())) {
            intent.setRequiresConfirmation(true);
        }
        return intent;
    }

    public int size() {
        return exact.size();
    }

    private static void applyRow(VoiceIntent intent, Map<String, Object> row) {
        copy(row, "action", intent::setAction);
        copy(row, "title", intent::setTitle);
        copy(row, "taskReference", intent::setTaskReference);
        copy(row, "comment", intent::setComment);
        copy(row, "fuel", intent::setFuel);
        copy(row, "targetType", intent::setTargetType);
        copy(row, "recurrence", intent::setRecurrence);
        copy(row, "requestedStatus", intent::setRequestedStatus);
        copy(row, "occurrencePriority", intent::setOccurrencePriority);
        copy(row, "branchReference", intent::setBranchReference);
        copy(row, "targetUserReference", intent::setTargetUserReference);
    }

    private static void copy(Map<String, Object> row, String key, java.util.function.Consumer<String> setter) {
        Object value = row.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            setter.accept(String.valueOf(value));
        }
    }
}
