package com.torqmind.ops.application.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP (RFC 6238) artesanal: HMAC-SHA1, passo de 30s, 6 digitos, Base32.
 * Sem dependencia externa; verificacao com tolerancia de +/- 1 passo (clock skew).
 */
@Service
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int WINDOW = 1;

    private final SecureRandom random = new SecureRandom();

    /** Novo segredo Base32 (160 bits). */
    public String newSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public boolean verify(String base32Secret, String code) {
        return verify(base32Secret, code, Instant.now().getEpochSecond());
    }

    boolean verify(String base32Secret, String code, long epochSeconds) {
        if (base32Secret == null || code == null) {
            return false;
        }
        String normalized = code.trim();
        if (normalized.length() != DIGITS || !normalized.chars().allMatch(Character::isDigit)) {
            return false;
        }
        byte[] key = base32Decode(base32Secret);
        long counter = Math.floorDiv(epochSeconds, (long) STEP_SECONDS);
        for (int w = -WINDOW; w <= WINDOW; w++) {
            if (constantTimeEquals(normalized, hotp(key, counter + w))) {
                return true;
            }
        }
        return false;
    }

    /** Codigo para um instante (segundos epoch). Visivel para teste. */
    String code(String base32Secret, long epochSeconds) {
        return hotp(base32Decode(base32Secret), Math.floorDiv(epochSeconds, (long) STEP_SECONDS));
    }

    public String otpauthUri(String issuer, String account, String base32Secret) {
        String enc = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String label = enc + ":" + URLEncoder.encode(account, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + enc
                + "&algorithm=SHA1&digits=" + DIGITS
                + "&period=" + STEP_SECONDS;
    }

    private static String hotp(byte[] key, long counter) {
        try {
            byte[] msg = new byte[8];
            long c = counter;
            for (int i = 7; i >= 0; i--) {
                msg[i] = (byte) (c & 0xff);
                c >>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao gerar codigo TOTP.", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                sb.append(BASE32.charAt((buffer >> bits) & 0x1f));
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String s) {
        String clean = s.trim().replace("=", "").toUpperCase();
        int buffer = 0;
        int bits = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (char c : clean.toCharArray()) {
            int val = BASE32.indexOf(c);
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }
}
