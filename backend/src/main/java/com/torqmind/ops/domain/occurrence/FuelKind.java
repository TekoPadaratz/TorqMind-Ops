package com.torqmind.ops.domain.occurrence;

import java.util.Locale;

public enum FuelKind {
    DIESEL_S10("Diesel S-10"),
    DIESEL_S500("Diesel S-500"),
    ETANOL("Etanol"),
    GASOLINA_ADITIVADA("Gasolina Aditivada"),
    GASOLINA_COMUM("Gasolina Comum");

    private final String label;

    FuelKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean showsGasolineAlcohol() {
        return this == GASOLINA_COMUM || this == GASOLINA_ADITIVADA;
    }

    public boolean showsAehcAlcohol() {
        return this == ETANOL;
    }

    public static FuelKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Informe o combustível.");
        }
        try {
            return FuelKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Combustível inválido.");
        }
    }

    public static FuelKind fromSpeech(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return null;
        }
        String low = transcript.toLowerCase(Locale.ROOT);
        if (low.contains("s-500") || low.contains("s500") || low.contains("s 500")) {
            return DIESEL_S500;
        }
        if (low.contains("s-10") || low.contains("s10") || low.contains("s 10")) {
            return DIESEL_S10;
        }
        if (low.contains("aditivad")) {
            return GASOLINA_ADITIVADA;
        }
        if (low.contains("etanol") || low.contains("álcool") || low.contains("alcool") || low.contains("aehc")) {
            return ETANOL;
        }
        if (low.contains("gasolina comum") || (low.contains("gasolina") && !low.contains("aditiv"))) {
            return GASOLINA_COMUM;
        }
        if (low.contains("diesel")) {
            return DIESEL_S10;
        }
        return null;
    }
}
