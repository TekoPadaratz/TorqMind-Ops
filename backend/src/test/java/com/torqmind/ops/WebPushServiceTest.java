package com.torqmind.ops;

import com.torqmind.ops.application.notification.WebPushService;
import com.torqmind.ops.domain.push.PushSubscription;
import com.torqmind.ops.domain.push.PushVapid;
import com.torqmind.ops.infrastructure.persistence.PushSubscriptionRepository;
import com.torqmind.ops.infrastructure.persistence.PushVapidRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

class WebPushServiceTest {

    @Test
    void generatesVapidPublicKeyInBase64Url() {
        PushVapidRepository vapidRepo = Mockito.mock(PushVapidRepository.class);
        PushSubscriptionRepository subRepo = Mockito.mock(PushSubscriptionRepository.class);
        Mockito.when(vapidRepo.findById(1)).thenReturn(Optional.empty());
        Mockito.when(vapidRepo.save(Mockito.any(PushVapid.class))).thenAnswer(inv -> inv.getArgument(0));
        WebPushService svc = new WebPushService(vapidRepo, subRepo, "mailto:test@torqmind.local");

        String pk = svc.publicKey();

        Assertions.assertNotNull(pk);
        Assertions.assertTrue(pk.length() > 80, "chave publica VAPID deve ter ~88 chars");
        Assertions.assertFalse(pk.contains("="));
        Assertions.assertFalse(pk.contains("+"));
        Assertions.assertFalse(pk.contains("/"));
        Mockito.verify(vapidRepo).save(Mockito.any(PushVapid.class));
    }

    @Test
    void subscribeUpsertsByEndpoint() {
        PushVapidRepository vapidRepo = Mockito.mock(PushVapidRepository.class);
        PushSubscriptionRepository subRepo = Mockito.mock(PushSubscriptionRepository.class);
        WebPushService svc = new WebPushService(vapidRepo, subRepo, "mailto:test@torqmind.local");
        UUID user = UUID.randomUUID();
        Mockito.when(subRepo.findByEndpoint("https://push/abc")).thenReturn(Optional.empty());

        svc.subscribe(user, "https://push/abc", "p256dhkey", "authkey");

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        Mockito.verify(subRepo).save(captor.capture());
        Assertions.assertEquals(user, captor.getValue().getUserId());
        Assertions.assertEquals("https://push/abc", captor.getValue().getEndpoint());
        Assertions.assertEquals("p256dhkey", captor.getValue().getP256dh());
        Assertions.assertNotNull(captor.getValue().getCreatedAt());
    }

    @Test
    void subscribeIgnoresIncompleteInput() {
        PushVapidRepository vapidRepo = Mockito.mock(PushVapidRepository.class);
        PushSubscriptionRepository subRepo = Mockito.mock(PushSubscriptionRepository.class);
        WebPushService svc = new WebPushService(vapidRepo, subRepo, "mailto:test@torqmind.local");

        svc.subscribe(UUID.randomUUID(), "", "p", "a");
        svc.subscribe(null, "https://x", "p", "a");
        svc.sendToUser(null, "t", "b", "/");

        Mockito.verifyNoInteractions(subRepo);
    }
}
