package com.torqmind.ops.application.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class TotpServiceTest {

    // Segredo padrao RFC 6238 ("12345678901234567890" em Base32).
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesRfc6238Vectors() {
        TotpService totp = new TotpService();
        Assertions.assertEquals("287082", totp.code(SECRET, 59L));
        Assertions.assertEquals("081804", totp.code(SECRET, 1111111109L));
        Assertions.assertEquals("005924", totp.code(SECRET, 1234567890L));
    }

    @Test
    void verifyAcceptsCurrentAndAdjacentWindow() {
        TotpService totp = new TotpService();
        long t = 1234567890L;
        Assertions.assertTrue(totp.verify(SECRET, totp.code(SECRET, t), t));
        Assertions.assertTrue(totp.verify(SECRET, totp.code(SECRET, t - 30), t));
        Assertions.assertTrue(totp.verify(SECRET, totp.code(SECRET, t + 30), t));
    }

    @Test
    void verifyRejectsOutOfWindowAndMalformed() {
        TotpService totp = new TotpService();
        long t = 1234567890L;
        Assertions.assertFalse(totp.verify(SECRET, totp.code(SECRET, t + 300), t));
        Assertions.assertFalse(totp.verify(SECRET, "12345", t));
        Assertions.assertFalse(totp.verify(SECRET, "abcdef", t));
        Assertions.assertFalse(totp.verify(SECRET, null, t));
    }

    @Test
    void newSecretIsUsable() {
        TotpService totp = new TotpService();
        String secret = totp.newSecret();
        Assertions.assertTrue(secret.length() >= 32);
        long t = Instant.now().getEpochSecond();
        Assertions.assertTrue(totp.verify(secret, totp.code(secret, t), t));
    }
}
