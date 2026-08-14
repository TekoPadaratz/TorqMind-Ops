package com.torqmind.ops.shared.media;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Detecta tipo real pelo conteúdo (não confia em Content-Type / extensão). */
public final class MediaSignatures {
    private MediaSignatures() {}

    public static String detect(byte[] content) {
        if (content == null || content.length < 12) {
            return null;
        }
        if (isJpeg(content)) {
            return "image/jpeg";
        }
        if (isPng(content)) {
            return "image/png";
        }
        if (isGif(content)) {
            return "image/gif";
        }
        if (isWebp(content)) {
            return "image/webp";
        }
        if (isPdf(content)) {
            return "application/pdf";
        }
        if (isWav(content)) {
            return "audio/wav";
        }
        if (isOgg(content)) {
            return "audio/ogg";
        }
        if (isWebm(content)) {
            return "audio/webm";
        }
        if (isMp4Family(content)) {
            return "audio/mp4";
        }
        if (isMpegAudio(content)) {
            return "audio/mpeg";
        }
        return null;
    }

    public static boolean isImage(String mime) {
        return mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    public static boolean isAudio(String mime) {
        if (mime == null) {
            return false;
        }
        String m = mime.toLowerCase(Locale.ROOT);
        return m.startsWith("audio/") || "video/webm".equals(m);
    }

    public static boolean matchesDeclared(String declared, String detected) {
        if (detected == null) {
            return false;
        }
        if (declared == null || declared.isBlank()) {
            return true;
        }
        String d = declared.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (d.equals(detected)) {
            return true;
        }
        if (detected.startsWith("image/") && d.startsWith("image/")) {
            return detected.equals(d);
        }
        if (detected.equals("audio/webm") && (d.equals("audio/webm") || d.equals("video/webm"))) {
            return true;
        }
        if (detected.equals("audio/mp4") && (d.equals("audio/mp4") || d.equals("audio/m4a") || d.equals("video/mp4"))) {
            return true;
        }
        if (detected.equals("audio/mpeg") && (d.equals("audio/mpeg") || d.equals("audio/mp3"))) {
            return true;
        }
        if (detected.equals("audio/ogg") && (d.equals("audio/ogg") || d.equals("audio/opus"))) {
            return true;
        }
        return false;
    }

    private static boolean isJpeg(byte[] b) {
        return b[0] == (byte) 0xFF && b[1] == (byte) 0xD8 && b[2] == (byte) 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b[0] == (byte) 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
    }

    private static boolean isGif(byte[] b) {
        return b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
    }

    private static boolean isWebp(byte[] b) {
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static boolean isPdf(byte[] b) {
        return b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    private static boolean isWav(byte[] b) {
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
    }

    private static boolean isOgg(byte[] b) {
        return b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S';
    }

    private static boolean isWebm(byte[] b) {
        return b[0] == 0x1A && b[1] == 0x45 && b[2] == (byte) 0xDF && b[3] == (byte) 0xA3;
    }

    private static boolean isMp4Family(byte[] b) {
        String box = new String(b, 4, 8, StandardCharsets.ISO_8859_1);
        return box.startsWith("ftyp");
    }

    private static boolean isMpegAudio(byte[] b) {
        return (b[0] == 'I' && b[1] == 'D' && b[2] == '3')
                || (b[0] == (byte) 0xFF && (b[1] & 0xE0) == 0xE0);
    }
}
