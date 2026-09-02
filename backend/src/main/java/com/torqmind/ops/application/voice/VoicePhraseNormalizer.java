package com.torqmind.ops.application.voice;

import java.text.Normalizer;
import java.util.Locale;

public final class VoicePhraseNormalizer {

    private VoicePhraseNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return lower.replaceAll("\\s+", " ");
    }
}
