package com.torqmind.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.application.voice.DeterministicVoiceIntentProvider;
import com.torqmind.ops.application.voice.VoiceContext;
import com.torqmind.ops.application.voice.VoiceIntent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class VoiceGoldenPhrasesTest {

    private static final DeterministicVoiceIntentProvider provider = new DeterministicVoiceIntentProvider();
    private static final ObjectMapper mapper = new ObjectMapper();

    static Stream<Map<String, Object>> phrases() throws Exception {
        try (InputStream in = VoiceGoldenPhrasesTest.class.getResourceAsStream("/voice/golden_phrases.json")) {
            List<Map<String, Object>> list = mapper.readValue(in, new TypeReference<>() {});
            return list.stream();
        }
    }

    @ParameterizedTest
    @MethodSource("phrases")
    void goldenPhraseMapsToExpectedAction(Map<String, Object> row) {
        String phrase = String.valueOf(row.get("phrase"));
        String expectedAction = String.valueOf(row.get("action"));
        VoiceIntent intent = provider.interpret(phrase, new VoiceContext());
        Assertions.assertEquals(expectedAction, intent.getAction(), "Frase: " + phrase);
        if (row.containsKey("requestedStatus")) {
            Assertions.assertEquals(String.valueOf(row.get("requestedStatus")), intent.getRequestedStatus(), phrase);
        }
        if (row.containsKey("fuel")) {
            Assertions.assertEquals(String.valueOf(row.get("fuel")), intent.getFuel(), phrase);
        }
    }
}
