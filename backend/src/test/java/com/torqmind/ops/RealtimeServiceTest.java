package com.torqmind.ops;

import com.torqmind.ops.application.realtime.RealtimeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

class RealtimeServiceTest {

    @Test
    void registersConnectionAndPublishIsSafe() {
        RealtimeService svc = new RealtimeService();
        UUID user = UUID.randomUUID();

        svc.publish(user, "notification", Map.of("x", 1));
        Assertions.assertEquals(0, svc.connectionCount());

        SseEmitter emitter = svc.register(user);
        Assertions.assertNotNull(emitter);
        Assertions.assertEquals(1, svc.connectionCount());

        svc.publish(user, "notification", Map.of("title", "oi"));
        svc.heartbeat();
        Assertions.assertEquals(1, svc.connectionCount());

        SseEmitter none = svc.register(null);
        Assertions.assertNotNull(none);
        Assertions.assertEquals(1, svc.connectionCount());
    }
}
