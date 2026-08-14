package com.torqmind.ops.application.voice;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoiceDateTimeNormalizer {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern TIME = Pattern.compile("(\\d{1,2})(?:[:hH](\\d{2}))?");

    private VoiceDateTimeNormalizer() {}

    public static LocalDate parseDate(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = fold(raw);
        if (v.equals("hoje")) {
            return today;
        }
        if (v.equals("amanha") || v.equals("amanhã")) {
            return today.plusDays(1);
        }
        if (v.contains("depois de amanha") || v.contains("depois de amanhã")) {
            return today.plusDays(2);
        }
        Integer weekday = weekdayIso(v);
        if (weekday != null) {
            return nextOrSame(today, DayOfWeek.of(weekday));
        }
        try {
            return LocalDate.parse(raw.trim(), ISO_DATE);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Data inválida: use AAAA-MM-DD ou hoje/amanhã.");
        }
    }

    public static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = fold(raw);
        Integer named = namedHour(v);
        if (named != null && !v.matches(".*\\d.*")) {
            return LocalTime.of(named, 0);
        }
        v = v.replace(" ", "");
        v = v.replace("horas", "").replace("hora", "").replace("às", "").replace("as", "");
        Matcher m = TIME.matcher(v);
        if (!m.find()) {
            if (named != null) {
                return LocalTime.of(named, 0);
            }
            throw new IllegalArgumentException("Horário inválido.");
        }
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (v.contains("da tarde") && h < 12) {
            h += 12;
        }
        if (v.contains("da noite") && h < 12) {
            h += 12;
        }
        if (h == 24) {
            h = 0;
        }
        if (h < 0 || h > 23 || min < 0 || min > 59) {
            throw new IllegalArgumentException("Horário impossível.");
        }
        return LocalTime.of(h, min);
    }

    public static Integer namedHour(String raw) {
        java.util.List<Integer> all = namedHoursInOrder(raw);
        return all.isEmpty() ? null : all.get(0);
    }

    public static java.util.List<Integer> namedHoursInOrder(String raw) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (raw == null) {
            return out;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?:às|as)\\s+(uma|duas|tres|três|quatro|cinco|seis|sete|oito|nove|dez|onze|doze|meio[- ]dia|meia[- ]noite)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE
        ).matcher(fold(raw));
        while (m.find()) {
            Integer h = wordToHour(m.group(1));
            if (h != null) {
                out.add(h);
            }
        }
        return out;
    }

    private static Integer wordToHour(String w) {
        String v = fold(w);
        return switch (v) {
            case "meia noite", "meia-noite" -> 0;
            case "meio dia", "meio-dia", "doze" -> 12;
            case "uma" -> 1;
            case "duas" -> 2;
            case "tres", "três" -> 3;
            case "quatro" -> 4;
            case "cinco" -> 5;
            case "seis" -> 6;
            case "sete" -> 7;
            case "oito" -> 8;
            case "nove" -> 9;
            case "dez" -> 10;
            case "onze" -> 11;
            default -> null;
        };
    }

    public static Integer weekdayIso(String raw) {
        if (raw == null) {
            return null;
        }
        String v = fold(raw);
        if (v.contains("segunda")) return 1;
        if (v.contains("terca") || v.contains("terça")) return 2;
        if (v.contains("quarta")) return 3;
        if (v.contains("quinta")) return 4;
        if (v.contains("sexta")) return 5;
        if (v.contains("sabado") || v.contains("sábado")) return 6;
        if (v.contains("domingo")) return 7;
        return null;
    }

    public static List<Integer> customDaysFromSpeech(String transcript) {
        String v = fold(transcript);
        java.util.LinkedHashSet<Integer> days = new java.util.LinkedHashSet<>();
        if (v.contains("primeiro") || v.matches(".*\\b1\\b.*") || v.contains("dia 1")) {
            days.add(1);
        }
        Matcher m = Pattern.compile("\\b(\\d{1,2})\\b").matcher(v);
        while (m.find()) {
            int d = Integer.parseInt(m.group(1));
            if (d >= 1 && d <= 31) {
                days.add(d);
            }
        }
        if (v.contains("quinze")) days.add(15);
        if (v.contains("vinte e oito") || v.contains("vinte-e-oito")) days.add(28);
        return List.copyOf(days);
    }

    public static String fold(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    private static LocalDate nextOrSame(LocalDate today, DayOfWeek day) {
        int delta = day.getValue() - today.getDayOfWeek().getValue();
        if (delta < 0) {
            delta += 7;
        }
        return today.plusDays(delta);
    }
}
