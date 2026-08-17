package com.torqmind.ops.shared.documents;

import java.util.Locale;

/**
 * Máscaras leves. Não rejeita RG, documento estrangeiro nem CNPJ/CPF parciais.
 */
public final class DocumentFormats {

    private DocumentFormats() {}

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String digits(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    public static String cnpj(String value) {
        String raw = blankToNull(value);
        if (raw == null) {
            return null;
        }
        String only = raw.replaceAll("\\D", "");
        if (only.length() == 14) {
            return only.substring(0, 2) + "." + only.substring(2, 5) + "." + only.substring(5, 8)
                    + "/" + only.substring(8, 12) + "-" + only.substring(12);
        }
        return raw;
    }

    public static String personDocument(String value) {
        String raw = blankToNull(value);
        if (raw == null) {
            return null;
        }
        String only = raw.replaceAll("\\D", "");
        boolean letters = raw.chars().anyMatch(Character::isLetter);
        if (!letters && only.length() == 11) {
            return only.substring(0, 3) + "." + only.substring(3, 6) + "." + only.substring(6, 9) + "-" + only.substring(9);
        }
        return raw;
    }

    public static String plate(String value) {
        String raw = blankToNull(value);
        if (raw == null) {
            return null;
        }
        return raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9-]", "");
    }

    public static String postalCode(String value) {
        String raw = blankToNull(value);
        if (raw == null) {
            return null;
        }
        String only = raw.replaceAll("\\D", "");
        if (only.length() == 8) {
            return only.substring(0, 5) + "-" + only.substring(5);
        }
        return raw;
    }

    public static String uf(String value) {
        String raw = blankToNull(value);
        return raw == null ? null : raw.toUpperCase(Locale.ROOT);
    }
}
