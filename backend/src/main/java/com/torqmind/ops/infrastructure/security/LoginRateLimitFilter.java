package com.torqmind.ops.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Limite por IP no /api/auth/login e /api/auth/login/2fa. Complementa o bloqueio por conta no AuthService.
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final int MAX_ATTEMPTS = 40;
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, Deque<Long>> attemptsByIp = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && isRateLimited(request.getRequestURI())) {
            String ip = clientIp(request);
            long now = System.currentTimeMillis();
            Deque<Long> window = attemptsByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
            synchronized (window) {
                while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                    window.pollFirst();
                }
                if (window.size() >= MAX_ATTEMPTS) {
                    response.setStatus(429);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(
                            Map.of("error", "rate_limited",
                                    "message", "Muitas tentativas de login. Aguarde 1 minuto.")));
                    return;
                }
                window.addLast(now);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isRateLimited(String uri) {
        return LOGIN_PATH.equals(uri) || (LOGIN_PATH + "/2fa").equals(uri);
    }
}
