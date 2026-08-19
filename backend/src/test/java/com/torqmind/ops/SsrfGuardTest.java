package com.torqmind.ops;

import com.torqmind.ops.infrastructure.security.SsrfGuard;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SsrfGuardTest {

    @Test
    void rejectsNonHttpsAndInternalTargets() {
        Assertions.assertFalse(SsrfGuard.isSafe("http://example.com/hook"));
        Assertions.assertFalse(SsrfGuard.isSafe("ftp://example.com/hook"));
        Assertions.assertFalse(SsrfGuard.isSafe("https://127.0.0.1/hook"));
        Assertions.assertFalse(SsrfGuard.isSafe("https://localhost/hook"));
        Assertions.assertFalse(SsrfGuard.isSafe("https://10.1.2.3/hook"));
        Assertions.assertFalse(SsrfGuard.isSafe("https://172.16.5.5/hook"));
        Assertions.assertFalse(SsrfGuard.isSafe("https://192.168.0.1/hook"));
        Assertions.assertFalse(SsrfGuard.isSafe("https://169.254.169.254/latest/meta-data"));
        Assertions.assertFalse(SsrfGuard.isSafe("https://100.64.0.1/hook"));
        Assertions.assertFalse(SsrfGuard.isSafe("not a url"));
        Assertions.assertFalse(SsrfGuard.isSafe(""));
    }

    @Test
    void allowsPublicIpLiteral() {
        Assertions.assertTrue(SsrfGuard.isSafe("https://1.1.1.1/webhooks/torqmind"));
        Assertions.assertTrue(SsrfGuard.isSafe("https://8.8.8.8/hook"));
    }
}
