package com.torqmind.ops;

import com.torqmind.ops.application.voice.AuthorizedEntityResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AuthorizedEntityResolverTest {

    @Test
    void matchesFoldedPortugueseNames() {
        Assertions.assertTrue(AuthorizedEntityResolver.matches("Posto Centro", "centro"));
        Assertions.assertTrue(AuthorizedEntityResolver.matches("João Silva", "joao"));
        Assertions.assertFalse(AuthorizedEntityResolver.matches("Posto Norte", "sul"));
    }
}
