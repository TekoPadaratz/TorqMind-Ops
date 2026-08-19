package com.torqmind.ops;

import com.torqmind.ops.application.apikey.ApiKeyService;
import com.torqmind.ops.domain.apikey.ApiKey;
import com.torqmind.ops.infrastructure.persistence.ApiKeyRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

class ApiKeyServiceTest {

    @Test
    void createReturnsRawKeyAndMasksIt() {
        ApiKeyRepository repo = Mockito.mock(ApiKeyRepository.class);
        Mockito.when(repo.save(Mockito.any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            k.setId(1L);
            return k;
        });
        ApiKeyService svc = new ApiKeyService(repo);

        ApiKeyService.CreatedKey created = svc.create(7L, "BI", UUID.randomUUID());

        Assertions.assertTrue(created.key().startsWith("tqm_"));
        Assertions.assertEquals(7L, created.info().companyId());
        Assertions.assertTrue(created.info().maskedKey().startsWith("tqm_"));
        Assertions.assertTrue(created.info().maskedKey().endsWith("..."));
        Assertions.assertNotEquals(created.key(), created.info().maskedKey());
    }

    @Test
    void authenticateMatchesValidAndRejectsWrong() {
        ApiKeyRepository repo = Mockito.mock(ApiKeyRepository.class);
        final ApiKey[] stored = new ApiKey[1];
        Mockito.when(repo.save(Mockito.any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            k.setId(1L);
            stored[0] = k;
            return k;
        });
        ApiKeyService svc = new ApiKeyService(repo);
        ApiKeyService.CreatedKey created = svc.create(7L, "BI", null);
        String secret = created.key().substring("tqm_".length());
        Mockito.when(repo.findByPrefixAndActiveTrue(secret.substring(0, 8))).thenReturn(Optional.of(stored[0]));

        Assertions.assertTrue(svc.authenticate(created.key()).isPresent());
        Assertions.assertTrue(svc.authenticate("tqm_" + secret + "x").isEmpty());
        Assertions.assertTrue(svc.authenticate("nope").isEmpty());
        Assertions.assertTrue(svc.authenticate(null).isEmpty());
    }
}
