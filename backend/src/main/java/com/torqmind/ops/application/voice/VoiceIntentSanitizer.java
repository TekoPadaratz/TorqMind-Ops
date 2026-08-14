package com.torqmind.ops.application.voice;

public final class VoiceIntentSanitizer {
    private VoiceIntentSanitizer() {}

    public static String sanitize(String transcript) {
        if (transcript == null) {
            return "";
        }
        String t = transcript.length() > 4000 ? transcript.substring(0, 4000) : transcript;
        String[] lines = t.split("\\R");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String cleaned = line.replaceAll("(?i)ignore (as instru[cç][oõ]es anteriores|previous instructions)", " ")
                    .replaceAll("(?i)altere a permiss[aã]o", " ")
                    .replaceAll("(?i)system prompt", " ")
                    .replaceAll("(?i)voc[eê] agora [eé]", " ")
                    .trim();
            if (cleaned.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(cleaned);
        }
        return sb.toString().trim();
    }
}
