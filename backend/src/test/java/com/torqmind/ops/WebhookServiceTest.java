package com.torqmind.ops;

import com.torqmind.ops.application.webhook.WebhookService;
import com.torqmind.ops.domain.webhook.Webhook;
import com.torqmind.ops.infrastructure.persistence.WebhookRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

class WebhookServiceTest {

    @Test
    void createValidatesUrlAndReturnsSecret() {
        WebhookRepository repo = Mockito.mock(WebhookRepository.class);
        Mockito.when(repo.save(Mockito.any(Webhook.class))).thenAnswer(inv -> {
            Webhook w = inv.getArgument(0);
            w.setId(1L);
            return w;
        });
        WebhookService svc = new WebhookService(repo);

        WebhookService.CreatedWebhook created = svc.create(3L, "https://1.1.1.1/hook", null, UUID.randomUUID());

        Assertions.assertTrue(created.secret().startsWith("whsec_"));
        Assertions.assertEquals("https://1.1.1.1/hook", created.info().url());
        Assertions.assertEquals(3L, created.info().companyId());
        Assertions.assertTrue(created.info().active());
    }

    @Test
    void createRejectsInternalOrNonHttpsUrl() {
        WebhookRepository repo = Mockito.mock(WebhookRepository.class);
        WebhookService svc = new WebhookService(repo);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> svc.create(3L, "https://10.0.0.1/hook", null, null));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> svc.create(3L, "http://example.com/hook", null, null));
        Mockito.verify(repo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void dispatchWithoutHooksIsNoop() {
        WebhookRepository repo = Mockito.mock(WebhookRepository.class);
        Mockito.when(repo.findByCompanyIdAndActiveTrue(3L)).thenReturn(java.util.List.of());
        WebhookService svc = new WebhookService(repo);

        svc.dispatch(3L, "routine_run.updated", java.util.Map.of("entityId", 9L));
        svc.dispatch(null, "x", java.util.Map.of());
    }
}
