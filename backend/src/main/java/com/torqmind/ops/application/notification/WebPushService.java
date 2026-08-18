package com.torqmind.ops.application.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.domain.push.PushSubscription;
import com.torqmind.ops.domain.push.PushVapid;
import com.torqmind.ops.infrastructure.persistence.PushSubscriptionRepository;
import com.torqmind.ops.infrastructure.persistence.PushVapidRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Web Push (VAPID): gera/persiste o par de chaves, guarda inscricoes por dispositivo e envia best-effort. */
@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);
    private static final String CURVE = "secp256r1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final ECNamedCurveParameterSpec EC_SPEC = ECNamedCurveTable.getParameterSpec(CURVE);

    private final PushVapidRepository vapidRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final String subject;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "web-push");
        t.setDaemon(true);
        return t;
    });

    private volatile PushVapid vapid;

    public WebPushService(PushVapidRepository vapidRepository, PushSubscriptionRepository subscriptionRepository,
                          @Value("${app.push.subject:mailto:ops-agent@torqmind.local}") String subject) {
        this.vapidRepository = vapidRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subject = subject;
    }

    public String publicKey() {
        return ensureVapid().getPublicKey();
    }

    /** Prepara/valida o VAPID no boot; best-effort e nunca derruba o contexto. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            log.info("Web Push pronto (VAPID configurado, chave publica com {} chars).", publicKey().length());
        } catch (Exception e) {
            log.warn("Web Push indisponivel neste ambiente: {}", e.toString());
        }
    }

    private synchronized PushVapid ensureVapid() {
        if (vapid != null) {
            return vapid;
        }
        vapid = vapidRepository.findById(1).orElseGet(this::generateVapid);
        return vapid;
    }

    private PushVapid generateVapid() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(EC_SPEC);
            KeyPair kp = kpg.generateKeyPair();
            org.bouncycastle.jce.interfaces.ECPublicKey pub = (org.bouncycastle.jce.interfaces.ECPublicKey) kp.getPublic();
            org.bouncycastle.jce.interfaces.ECPrivateKey priv = (org.bouncycastle.jce.interfaces.ECPrivateKey) kp.getPrivate();
            PushVapid v = new PushVapid();
            v.setId(1);
            v.setPublicKey(base64Url(pub.getQ().getEncoded(false)));
            v.setPrivateKey(base64Url(toFixed(priv.getD(), 32)));
            v.setSubject(subject);
            v.setCreatedAt(Instant.now());
            return vapidRepository.save(v);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar chaves VAPID.", e);
        }
    }

    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth) {
        if (userId == null || endpoint == null || endpoint.isBlank() || p256dh == null || auth == null) {
            return;
        }
        PushSubscription sub = subscriptionRepository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        sub.setUserId(userId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        if (sub.getCreatedAt() == null) {
            sub.setCreatedAt(Instant.now());
        }
        subscriptionRepository.save(sub);
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        subscriptionRepository.findByEndpoint(endpoint).ifPresent(subscriptionRepository::delete);
    }

    /** Enfileira o envio em thread separada; nunca propaga erro para o fluxo de notificacao. */
    public void sendToUser(UUID userId, String title, String body, String url) {
        if (userId == null) {
            return;
        }
        executor.submit(() -> {
            try {
                doSend(userId, title, body, url);
            } catch (Exception e) {
                log.debug("Falha no envio de push para {}: {}", userId, e.toString());
            }
        });
    }

    private void doSend(UUID userId, String title, String body, String url) throws Exception {
        List<PushSubscription> subs = subscriptionRepository.findByUserId(userId);
        if (subs.isEmpty()) {
            return;
        }
        PushVapid v = ensureVapid();
        PushService pushService = new PushService();
        pushService.setPublicKey(publicKeyFrom(v.getPublicKey()));
        pushService.setPrivateKey(privateKeyFrom(v.getPrivateKey()));
        pushService.setSubject(v.getSubject());
        byte[] payload = MAPPER.writeValueAsBytes(Map.of(
                "title", title == null ? "TorqMind Ops" : title,
                "body", body == null ? "" : body,
                "url", url == null ? "/" : url));
        for (PushSubscription sub : subs) {
            try {
                Notification n = new Notification(sub.getEndpoint(), publicKeyFrom(sub.getP256dh()),
                        base64UrlDecode(sub.getAuth()), payload);
                int status = pushService.send(n).getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    subscriptionRepository.delete(sub);
                }
            } catch (Exception e) {
                log.debug("Push falhou para endpoint {}: {}", sub.getEndpoint(), e.toString());
            }
        }
    }

    private static PublicKey publicKeyFrom(String base64UrlUncompressed) throws Exception {
        org.bouncycastle.math.ec.ECPoint point = EC_SPEC.getCurve().decodePoint(base64UrlDecode(base64UrlUncompressed));
        KeyFactory kf = KeyFactory.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        return kf.generatePublic(new ECPublicKeySpec(point, EC_SPEC));
    }

    private static PrivateKey privateKeyFrom(String base64UrlScalar) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        return kf.generatePrivate(new ECPrivateKeySpec(new BigInteger(1, base64UrlDecode(base64UrlScalar)), EC_SPEC));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] base64UrlDecode(String s) {
        String t = s.replace('+', '-').replace('/', '_');
        int rem = t.length() % 4;
        if (rem > 0) {
            t = t + "====".substring(rem);
        }
        return Base64.getUrlDecoder().decode(t);
    }

    private static byte[] toFixed(BigInteger value, int len) {
        byte[] b = value.toByteArray();
        if (b.length == len) {
            return b;
        }
        byte[] out = new byte[len];
        if (b.length > len) {
            System.arraycopy(b, b.length - len, out, 0, len);
        } else {
            System.arraycopy(b, 0, out, len - b.length, b.length);
        }
        return out;
    }
}
