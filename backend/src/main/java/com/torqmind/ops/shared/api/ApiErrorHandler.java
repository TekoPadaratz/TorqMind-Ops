package com.torqmind.ops.shared.api;

import com.torqmind.ops.application.auth.AuthService;
import com.torqmind.ops.application.voice.VoiceRateLimitException;
import com.torqmind.ops.application.voice.VoiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(AuthService.AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthService.AuthException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "unauthorized", "message", ex.getMessage()));
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleForbidden(RuntimeException ex) {
        String message = ex instanceof ForbiddenException ? ex.getMessage() : "Acesso não permitido.";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "forbidden", "message", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "validation_error", "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "validation_error", "message", "Payload invalido"));
    }

    @ExceptionHandler(VoiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleVoiceUnavailable(VoiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "voice_unavailable", "message", ex.getMessage()));
    }

    @ExceptionHandler(VoiceRateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleVoiceRate(VoiceRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "rate_limited", "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("unhandled", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal_error", "message", "Falha interna do servidor"));
    }
}
