package com.torqmind.ops;

import com.torqmind.ops.application.ops.NotificationPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class NotificationPolicyTest {

    @Test
    void shouldNotNotifySameActor() {
        UUID userId = UUID.randomUUID();
        Assertions.assertFalse(NotificationPolicy.shouldNotify(userId, userId));
    }

    @Test
    void shouldNotifyOtherRecipient() {
        Assertions.assertTrue(NotificationPolicy.shouldNotify(UUID.randomUUID(), UUID.randomUUID()));
    }
}
