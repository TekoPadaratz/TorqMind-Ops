package com.torqmind.ops.application.auth;

import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CredentialService credentialService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CredentialService credentialService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.credentialService = credentialService;
    }

    @Transactional(noRollbackFor = AuthException.class)
    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .orElseThrow(() -> new AuthException("Usuário ou senha inválidos."));

        if (!user.isActive()) {
            throw new AuthException("Usuário inativo.");
        }

        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AuthException("Muitas tentativas. Tente novamente em alguns minutos.");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int fails = user.getFailedLoginCount() + 1;
            if (fails >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_MINUTES, ChronoUnit.MINUTES));
                user.setFailedLoginCount(0);
            } else {
                user.setFailedLoginCount(fails);
            }
            userRepository.save(user);
            throw new AuthException("Usuário ou senha inválidos.");
        }

        if (user.getFailedLoginCount() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        String token = jwtService.generate(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getCompanyId(),
                user.getBranchId(),
                user.getPasswordEpoch()
        );
        return new LoginResult(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.getCompanyId(),
                user.getBranchId()
        );
    }

    @Transactional
    public LoginResult changeOwnPassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Usuário ou senha inválidos."));
        if (!user.isActive()) {
            throw new AuthException("Usuário inativo.");
        }
        if (!credentialService.matches(user, currentPassword)) {
            throw new IllegalArgumentException("Senha atual incorreta.");
        }
        if (credentialService.matches(user, newPassword)) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da atual.");
        }
        credentialService.assignPassword(user, userId, newPassword, CredentialService.ACTION_SELF_CHANGE, true);
        String token = jwtService.generate(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getCompanyId(),
                user.getBranchId(),
                user.getPasswordEpoch()
        );
        return new LoginResult(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.getCompanyId(),
                user.getBranchId()
        );
    }

    public record LoginResult(
            String token,
            java.util.UUID userId,
            String username,
            String fullName,
            String role,
            Long companyId,
            Long branchId
    ) {
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
