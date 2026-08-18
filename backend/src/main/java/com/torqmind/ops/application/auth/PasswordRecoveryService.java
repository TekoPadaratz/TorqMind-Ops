package com.torqmind.ops.application.auth;

import com.torqmind.ops.application.notification.EmailService;
import com.torqmind.ops.domain.user.PasswordResetToken;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.PasswordResetTokenRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/** Recuperacao de senha por e-mail: token de uso unico (hash guardado), valido por 1 hora. */
@Service
public class PasswordRecoveryService {

    private static final long TOKEN_TTL_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final CredentialService credentialService;
    private final EmailService emailService;
    private final String publicBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordResetTokenRepository resetTokenRepository,
            CredentialService credentialService,
            EmailService emailService,
            @Value("${app.public-base-url:https://task.torqmind.com.br}") String publicBaseUrl
    ) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.credentialService = credentialService;
        this.emailService = emailService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** Nunca revela se o e-mail existe (anti-enumeracao). */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findFirstByEmailIgnoreCaseAndActiveTrue(email.trim().toLowerCase()).ifPresent(user -> {
            String raw = randomToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            token.setTokenHash(sha256Hex(raw));
            token.setExpiresAt(Instant.now().plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS));
            token.setUsed(false);
            token.setCreatedAt(Instant.now());
            resetTokenRepository.save(token);

            String link = publicBaseUrl + "/reset?token=" + raw;
            emailService.send(user.getEmail(), "Recuperacao de senha - TorqMind Ops",
                    "Recebemos um pedido para redefinir sua senha.\n\n"
                            + "Abra o link a seguir (valido por 1 hora):\n" + link + "\n\n"
                            + "Se voce nao fez este pedido, ignore este e-mail.");
        });
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token invalido ou expirado.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("A nova senha deve ter ao menos 8 caracteres.");
        }
        PasswordResetToken token = resetTokenRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Token invalido ou expirado."));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token invalido ou expirado.");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Token invalido ou expirado."));
        credentialService.assignPassword(user, user.getId(), newPassword, CredentialService.ACTION_ADMIN_RESET, true);
        token.setUsed(true);
        resetTokenRepository.save(token);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }
}
