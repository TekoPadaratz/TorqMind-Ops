package com.torqmind.ops.application.apikey;

import com.torqmind.ops.domain.apikey.ApiKey;
import com.torqmind.ops.infrastructure.persistence.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cria/valida/lista/revoga chaves de API. Guarda apenas o hash SHA-256 da chave. */
@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "tqm_";
    private static final int PREFIX_LEN = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository repository;

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreatedKey create(Long companyId, String name, UUID createdBy) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String rawKey = KEY_PREFIX + secret;
        ApiKey entity = new ApiKey();
        entity.setCompanyId(companyId);
        entity.setName(name == null || name.isBlank() ? "Chave" : name.trim());
        entity.setPrefix(secret.substring(0, PREFIX_LEN));
        entity.setKeyHash(sha256Hex(rawKey));
        entity.setActive(true);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(Instant.now());
        return new CreatedKey(rawKey, view(repository.save(entity)));
    }

    /** Retorna a chave (ativa) se o valor bruto casar com o hash guardado. */
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String secret = rawKey.substring(KEY_PREFIX.length());
        if (secret.length() < PREFIX_LEN) {
            return Optional.empty();
        }
        ApiKey key = repository.findByPrefixAndActiveTrue(secret.substring(0, PREFIX_LEN)).orElse(null);
        if (key == null || !constantTimeEquals(sha256Hex(rawKey), key.getKeyHash())) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    @Transactional
    public void touch(Long id) {
        repository.updateLastUsed(id, Instant.now());
    }

    public List<ApiKeyView> list(Long companyId) {
        return repository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(ApiKeyService::view).toList();
    }

    @Transactional
    public boolean revoke(Long id, Long companyId) {
        ApiKey key = repository.findById(id).orElse(null);
        if (key == null || (companyId != null && !companyId.equals(key.getCompanyId()))) {
            return false;
        }
        key.setActive(false);
        repository.save(key);
        return true;
    }

    private static ApiKeyView view(ApiKey k) {
        return new ApiKeyView(k.getId(), k.getName(), KEY_PREFIX + k.getPrefix() + "...",
                k.isActive(), k.getCompanyId(), k.getCreatedAt(), k.getLastUsedAt());
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }

    public record CreatedKey(String key, ApiKeyView info) {
    }

    public record ApiKeyView(Long id, String name, String maskedKey, boolean active, Long companyId,
                             Instant createdAt, Instant lastUsedAt) {
    }
}
