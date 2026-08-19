package com.torqmind.ops.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.application.apikey.ApiKeyService;
import com.torqmind.ops.domain.apikey.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Autentica requisicoes a /api/public/ via header X-API-Key (rate-limit por chave). */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final int LIMIT_PER_MIN = 120;
    private static final long TOUCH_INTERVAL_SEC = 300;

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, long[]> buckets = new ConcurrentHashMap<>();

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String rawKey = request.getHeader("X-API-Key");
        if (path == null || !path.startsWith("/api/public/") || rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        ApiKey key = apiKeyService.authenticate(rawKey).orElse(null);
        if (key == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isRateLimited(key.getId())) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("error", "rate_limited", "message", "Limite de requisicoes excedido.")));
            return;
        }
        if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(Instant.now().minusSeconds(TOUCH_INTERVAL_SEC))) {
            try {
                apiKeyService.touch(key.getId());
            } catch (Exception ignore) {
                // best-effort
            }
        }
        AppUserPrincipal principal = new AppUserPrincipal(null, "api:" + key.getId(), "API_CLIENT", key.getCompanyId(), null);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(Long keyId) {
        long now = System.currentTimeMillis();
        long[] bucket = buckets.computeIfAbsent(keyId, k -> new long[]{now, 0});
        synchronized (bucket) {
            if (now - bucket[0] > 60_000) {
                bucket[0] = now;
                bucket[1] = 0;
            }
            bucket[1]++;
            return bucket[1] > LIMIT_PER_MIN;
        }
    }
}
