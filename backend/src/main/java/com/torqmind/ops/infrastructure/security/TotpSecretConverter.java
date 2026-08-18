package com.torqmind.ops.infrastructure.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifra o segredo TOTP em repouso (AES-GCM). Compatível com valores legados em texto puro:
 * na leitura, valores sem o prefixo "gcm:" são retornados como estão; na escrita, sempre cifra.
 * Chave: TOTP_ENC_KEY (base64 de 32 bytes) ou derivada de JWT_SECRET via SHA-256.
 */
@Converter
public class TotpSecretConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "gcm:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key = resolveKey();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao cifrar segredo TOTP.", ex);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || !dbData.startsWith(PREFIX)) {
            return dbData;
        }
        try {
            byte[] all = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(all, 0, IV_LEN);
            byte[] ciphertext = Arrays.copyOfRange(all, IV_LEN, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao decifrar segredo TOTP.", ex);
        }
    }

    private static SecretKey resolveKey() {
        try {
            String b64 = System.getenv("TOTP_ENC_KEY");
            if (b64 != null && !b64.isBlank()) {
                byte[] raw = Base64.getDecoder().decode(b64.trim());
                if (raw.length == 32) {
                    return new SecretKeySpec(raw, "AES");
                }
            }
            String seed = System.getenv("JWT_SECRET");
            if (seed == null || seed.isBlank()) {
                seed = "torqmind-ops-totp-fallback-seed";
            }
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao derivar chave de cifra TOTP.", ex);
        }
    }
}
