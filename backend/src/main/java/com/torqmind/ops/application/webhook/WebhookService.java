package com.torqmind.ops.application.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.domain.webhook.Webhook;
import com.torqmind.ops.infrastructure.persistence.WebhookRepository;
import com.torqmind.ops.infrastructure.security.SsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Webhooks de saida: criar/listar/excluir/testar e disparar eventos (best-effort, assinado, anti-SSRF). */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEDUP_WINDOW_MS = 10_000;

    private final WebhookRepository repository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "webhook");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Long> recentDispatch = new ConcurrentHashMap<>();

    public WebhookService(WebhookRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreatedWebhook create(Long companyId, String url, String events, UUID createdBy) {
        SsrfGuard.validate(url);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String secret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Webhook entity = new Webhook();
        entity.setCompanyId(companyId);
        entity.setUrl(url.trim());
        entity.setSecret(secret);
        entity.setEvents(events == null || events.isBlank() ? null : events.trim());
        entity.setActive(true);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(Instant.now());
        return new CreatedWebhook(secret, view(repository.save(entity)));
    }

    public List<WebhookView> list(Long companyId) {
        return repository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(WebhookService::view).toList();
    }

    @Transactional
    public boolean delete(Long id, Long companyId) {
        Webhook w = repository.findById(id).orElse(null);
        if (w == null || (companyId != null && !companyId.equals(w.getCompanyId()))) {
            return false;
        }
        repository.delete(w);
        return true;
    }

    /** Envia um evento de teste de forma sincrona e devolve o resultado para a UI. */
    public Map<String, Object> test(Long id, Long companyId) {
        Webhook w = repository.findById(id).orElse(null);
        if (w == null || (companyId != null && !companyId.equals(w.getCompanyId()))) {
            return Map.of("ok", false, "message", "Webhook nao encontrado.");
        }
        Map<String, Object> payload = envelope(w.getCompanyId(), "webhook.test", Map.of("message", "teste do TorqMind Ops"));
        try {
            int status = deliver(w, payload);
            return Map.of("ok", status >= 200 && status < 300, "status", status);
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** Dispara um evento para os webhooks ativos da empresa (best-effort, em thread separada). */
    public void dispatch(Long companyId, String event, Map<String, Object> data) {
        if (companyId == null || event == null) {
            return;
        }
        String dedupKey = companyId + ":" + event + ":" + (data == null ? "" : data.get("entityId"));
        long now = System.currentTimeMillis();
        Long previous = recentDispatch.put(dedupKey, now);
        if (previous != null && now - previous < DEDUP_WINDOW_MS) {
            return;
        }
        executor.submit(() -> {
            try {
                doDispatch(companyId, event, data);
            } catch (Exception e) {
                log.debug("Falha ao disparar webhook {}: {}", event, e.toString());
            }
        });
    }

    private void doDispatch(Long companyId, String event, Map<String, Object> data) {
        List<Webhook> hooks = repository.findByCompanyIdAndActiveTrue(companyId);
        if (hooks.isEmpty()) {
            return;
        }
        Map<String, Object> payload = envelope(companyId, event, data);
        for (Webhook w : hooks) {
            if (!matchesEvent(w.getEvents(), event)) {
                continue;
            }
            try {
                deliver(w, payload);
            } catch (Exception e) {
                log.debug("Webhook {} falhou: {}", w.getId(), e.toString());
            }
        }
    }

    private int deliver(Webhook w, Map<String, Object> payload) throws Exception {
        SsrfGuard.validate(w.getUrl()); // re-checa no envio (anti DNS rebinding para IP interno)
        byte[] body;
        try {
            body = MAPPER.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o payload.", e);
        }
        String signature = "sha256=" + hmacHex(w.getSecret(), body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(w.getUrl().trim()))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("User-Agent", "TorqMind-Ops-Webhook")
                .header("X-TorqMind-Event", String.valueOf(payload.get("event")))
                .header("X-TorqMind-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        int status;
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            status = response.statusCode();
            recordResult(w, status >= 200 && status < 300, String.valueOf(status));
        } catch (Exception e) {
            recordResult(w, false, "erro: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            throw e;
        }
        return status;
    }

    private void recordResult(Webhook w, boolean ok, String status) {
        Webhook fresh = repository.findById(w.getId()).orElse(null);
        if (fresh == null) {
            return;
        }
        fresh.setLastStatus(status);
        fresh.setLastAttemptAt(Instant.now());
        fresh.setFailureCount(ok ? 0 : fresh.getFailureCount() + 1);
        repository.save(fresh);
    }

    private static boolean matchesEvent(String filter, String event) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        for (String part : filter.split(",")) {
            String p = part.trim();
            if (!p.isEmpty() && (event.equals(p) || event.startsWith(p))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> envelope(Long companyId, String event, Map<String, Object> data) {
        Map<String, Object> map = new HashMap<>();
        map.put("event", event);
        map.put("companyId", companyId);
        map.put("timestamp", Instant.now().toString());
        map.put("data", data == null ? Map.of() : data);
        return map;
    }

    private static String hmacHex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(body);
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC indisponivel", e);
        }
    }

    private static WebhookView view(Webhook w) {
        return new WebhookView(w.getId(), w.getUrl(), w.getEvents(), w.isActive(), w.getCompanyId(),
                w.getCreatedAt(), w.getLastStatus(), w.getLastAttemptAt(), w.getFailureCount());
    }

    public record CreatedWebhook(String secret, WebhookView info) {
    }

    public record WebhookView(Long id, String url, String events, boolean active, Long companyId,
                              Instant createdAt, String lastStatus, Instant lastAttemptAt, int failureCount) {
    }
}
