package com.torqmind.ops.application.task;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Valida o conteúdo real do anexo; o Content-Type enviado pelo navegador não é confiável. */
final class TaskFilePolicy {

    private static final Set<String> HEIF_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    private TaskFilePolicy() {
    }

    static InspectedFile inspect(byte[] content) {
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return new InspectedFile("image/jpeg", ".jpg", true);
        }
        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new InspectedFile("image/png", ".png", true);
        }
        if (asciiAt(content, 0, "GIF87a") || asciiAt(content, 0, "GIF89a")) {
            return new InspectedFile("image/gif", ".gif", true);
        }
        if (asciiAt(content, 0, "RIFF") && asciiAt(content, 8, "WEBP")) {
            return new InspectedFile("image/webp", ".webp", true);
        }
        if (content != null && content.length >= 12 && asciiAt(content, 4, "ftyp")) {
            String brand = new String(content, 8, 4, StandardCharsets.US_ASCII).toLowerCase();
            if (HEIF_BRANDS.contains(brand)) {
                return new InspectedFile("image/heic", ".heic", true);
            }
        }
        if (asciiAt(content, 0, "%PDF-")) {
            return new InspectedFile("application/pdf", ".pdf", false);
        }
        throw new IllegalArgumentException("Tipo de arquivo não suportado. Envie uma imagem válida ou PDF.");
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content == null || content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiAt(byte[] content, int offset, String signature) {
        if (content == null || content.length < offset + signature.length()) {
            return false;
        }
        for (int i = 0; i < signature.length(); i++) {
            if (content[offset + i] != (byte) signature.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    record InspectedFile(String mimeType, String extension, boolean photo) {
    }
}
