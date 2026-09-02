package com.torqmind.ops.application.voice;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Continuação conversacional: aplica resposta falada a ambiguidades, campos faltantes e confirmações.
 */
public final class VoiceConversationResolver {

    private static final Pattern ORDINAL = Pattern.compile(
            "\\b(?:op[cç][aã]o\\s*)?(\\d{1,2})\\b|"
                    + "\\b(primeir[ao]|segund[ao]|terceir[ao]|quart[ao]|quint[ao])\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private VoiceConversationResolver() {
    }

    public enum SpeechIntent {
        NONE,
        CONFIRM,
        DENY
    }

    public static SpeechIntent confirmationIntent(String transcript) {
        String n = normalize(transcript);
        if (n.isBlank()) {
            return SpeechIntent.NONE;
        }
        if (containsAny(n, "nao", "não", "cancela", "cancelar", "negativo", "deixa", "para", "pare")) {
            if (!containsAny(n, "sim", "confirmo", "pode", "ok", "isso", "exato", "correto", "positivo")) {
                return SpeechIntent.DENY;
            }
        }
        if (containsAny(n, "sim", "confirmo", "confirmar", "pode", "pode fazer", "pode sim", "ok", "isso", "exato", "correto", "positivo", "manda ver", "vai em frente")) {
            return SpeechIntent.CONFIRM;
        }
        return SpeechIntent.NONE;
    }

    /**
     * @return true se aplicou alguma seleção/campo a partir da fala
     */
    public static boolean applySpokenAnswer(VoiceIntent intent, String transcript) {
        if (intent == null || transcript == null || transcript.isBlank()) {
            return false;
        }
        boolean applied = false;
        if (!intent.getAmbiguities().isEmpty()) {
            applied |= applyDisambiguation(intent, transcript);
        }
        if (!intent.getMissingFields().isEmpty()) {
            applied |= applyMissingFields(intent, transcript);
        }
        return applied;
    }

    private static boolean applyDisambiguation(VoiceIntent intent, String transcript) {
        String spoken = normalize(transcript);
        for (VoiceAmbiguity ambiguity : List.copyOf(intent.getAmbiguities())) {
            if (ambiguity.getOptions() == null || ambiguity.getOptions().isEmpty()) {
                continue;
            }
            Integer ordinal = parseOrdinal(spoken);
            if (ordinal != null && ordinal >= 1 && ordinal <= ambiguity.getOptions().size()) {
                VoiceOption chosen = ambiguity.getOptions().get(ordinal - 1);
                intent.getAmbiguities().removeIf(a -> ambiguity.getField().equals(a.getField()));
                applySelectionKey(intent, ambiguity.getField(), chosen.getKey());
                return true;
            }
            VoiceOption best = null;
            int bestScore = 0;
            for (VoiceOption option : ambiguity.getOptions()) {
                int score = matchScore(spoken, option.getLabel());
                if (score > bestScore) {
                    bestScore = score;
                    best = option;
                }
            }
            if (best != null && bestScore >= 2) {
                intent.getAmbiguities().removeIf(a -> ambiguity.getField().equals(a.getField()));
                applySelectionKey(intent, ambiguity.getField(), best.getKey());
                return true;
            }
        }
        return false;
    }

    private static void applySelectionKey(VoiceIntent intent, String field, String key) {
        if (field == null || key == null) {
            return;
        }
        switch (field) {
            case "companyReference" -> intent.setCompanyReference(key);
            case "branchReference", "cityReference" -> intent.setBranchReference(key);
            case "targetUserReference" -> intent.setTargetUserReference(key);
            case "targetSectorReference" -> intent.setTargetSectorReference(key);
            case "taskReference" -> intent.setTaskReference(key);
            default -> { }
        }
    }

    private static boolean applyMissingFields(VoiceIntent intent, String transcript) {
        String raw = transcript.trim();
        String spoken = normalize(transcript);
        boolean applied = false;
        List<String> missing = intent.getMissingFields();
        if (missing.contains("title") && !raw.isBlank()) {
            intent.setTitle(capitalize(raw));
            applied = true;
        }
        if (missing.contains("comment") && !raw.isBlank()) {
            intent.setComment(raw);
            applied = true;
        }
        if (missing.contains("description") && !raw.isBlank()) {
            intent.setDescription(raw);
            applied = true;
        }
        if (missing.contains("startTime")) {
            String time = extractFirstTime(spoken);
            if (time != null) {
                intent.setStartTime(time);
                applied = true;
            }
        }
        if (missing.contains("dueTime")) {
            String time = extractFirstTime(spoken);
            if (time != null) {
                intent.setDueTime(time);
                applied = true;
            }
        }
        if (missing.contains("scheduledDate")) {
            if (containsAny(spoken, "hoje")) {
                intent.setScheduledDate(java.time.LocalDate.now(VoiceDateTimeNormalizer.ZONE).toString());
                applied = true;
            } else if (containsAny(spoken, "amanha", "amanhã")) {
                intent.setScheduledDate(java.time.LocalDate.now(VoiceDateTimeNormalizer.ZONE).plusDays(1).toString());
                applied = true;
            }
        }
        if (missing.contains("targetUserReference") && !raw.isBlank()) {
            intent.setTargetUserReference(raw);
            applied = true;
        }
        if (missing.contains("targetSectorReference") && !raw.isBlank()) {
            intent.setTargetSectorReference(raw);
            applied = true;
        }
        if (missing.contains("branchReference") && !raw.isBlank()) {
            intent.setBranchReference(raw);
            applied = true;
        }
        return applied;
    }

    private static Integer parseOrdinal(String spoken) {
        Matcher m = ORDINAL.matcher(spoken);
        if (!m.find()) {
            return null;
        }
        if (m.group(1) != null) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        String word = m.group(2).toLowerCase(Locale.ROOT);
        if (word.startsWith("primeir")) return 1;
        if (word.startsWith("segund")) return 2;
        if (word.startsWith("terceir")) return 3;
        if (word.startsWith("quart")) return 4;
        if (word.startsWith("quint")) return 5;
        return null;
    }

    private static int matchScore(String spoken, String label) {
        if (label == null || label.isBlank()) {
            return 0;
        }
        String nLabel = normalize(label);
        if (spoken.equals(nLabel)) {
            return 100;
        }
        if (spoken.contains(nLabel) || nLabel.contains(spoken)) {
            return 10;
        }
        int score = 0;
        for (String token : nLabel.split("\\s+")) {
            if (token.length() >= 3 && spoken.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private static String extractFirstTime(String spoken) {
        Matcher m = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\b").matcher(spoken);
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h > 23) {
                continue;
            }
            String mm = m.group(2) == null ? "00" : m.group(2);
            return String.format("%02d:%02d", h, Integer.parseInt(mm));
        }
        List<Integer> named = VoiceDateTimeNormalizer.namedHoursInOrder(spoken);
        if (!named.isEmpty()) {
            return String.format("%02d:00", named.get(0));
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return lower.replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
