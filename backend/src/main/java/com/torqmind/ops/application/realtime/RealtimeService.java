package com.torqmind.ops.application.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Barramento de eventos ao vivo (SSE) por usuario: conexoes em memoria + heartbeat. */
@Service
public class RealtimeService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int MAX_PER_USER = 5;

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        if (userId == null) {
            emitter.complete();
            return emitter;
        }
        Set<SseEmitter> set = emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        if (set.size() >= MAX_PER_USER) {
            Iterator<SseEmitter> it = set.iterator();
            if (it.hasNext()) {
                SseEmitter old = it.next();
                set.remove(old);
                try {
                    old.complete();
                } catch (Exception ignore) {
                    // ja encerrado
                }
            }
        }
        set.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    public void publish(UUID userId, String event, Object data) {
        if (userId == null) {
            return;
        }
        Set<SseEmitter> set = emitters.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception e) {
                remove(userId, emitter);
                try {
                    emitter.complete();
                } catch (Exception ignore) {
                    // ja encerrado
                }
            }
        }
    }

    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        for (Map.Entry<UUID, Set<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(entry.getKey(), emitter);
                    try {
                        emitter.complete();
                    } catch (Exception ignore) {
                        // ja encerrado
                    }
                }
            }
        }
    }

    public int connectionCount() {
        return emitters.values().stream().mapToInt(Set::size).sum();
    }

    private void remove(UUID userId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
