package com.torqmind.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.application.voice.VoiceReadyPhraseCatalog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

class VoiceReadyPhraseCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VoiceReadyPhraseCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new VoiceReadyPhraseCatalog(objectMapper);
        catalog.load();
    }

    @Test
    void catalogLoadsAtLeastOneHundredTwentyPhrases() {
        Assertions.assertTrue(catalog.size() >= 120, "catálogo deve ter centenas de frases prontas");
    }

    @Test
    void politePrefixStillMatchesCatalog() {
        var intent = catalog.match("por favor, resumo do dia");
        Assertions.assertTrue(intent.isPresent());
        Assertions.assertEquals("SUMMARY_TODAY", intent.get().getAction());
    }

    @Test
    void allReadyPhrasesResolve() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/voice/ready_phrases.json")) {
            List<Map<String, Object>> rows = objectMapper.readValue(in, new TypeReference<>() {});
            for (Map<String, Object> row : rows) {
                String phrase = String.valueOf(row.get("phrase"));
                var intent = catalog.match(phrase);
                Assertions.assertTrue(intent.isPresent(), "sem match: " + phrase);
                Assertions.assertEquals(String.valueOf(row.get("action")), intent.get().getAction(), phrase);
            }
        }
    }
}
