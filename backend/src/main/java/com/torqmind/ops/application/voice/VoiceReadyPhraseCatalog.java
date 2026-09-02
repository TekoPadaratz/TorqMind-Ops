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
        Map<String, Object> row = exact.get(VoicePhraseNormalizer.normalize(transcript));
        if (row == null) {
            return Optional.empty();
        }
        VoiceIntent intent = new VoiceIntent();
        intent.setSchemaVersion(VoiceIntent.SCHEMA_VERSION);
        intent.setTranscript(transcript);
        intent.setRequiresConfirmation(false);
        intent.setConfidence(0.94);
        applyRow(intent, row);
        if ("DELETE_TASK".equals(intent.getAction()) || "REJECT_TASK".equals(intent.getAction())) {
            intent.setRequiresConfirmation(true);
        }
        return Optional.of(intent);
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
