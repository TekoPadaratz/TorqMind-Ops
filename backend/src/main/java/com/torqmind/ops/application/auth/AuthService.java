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
    private final TotpService totpService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CredentialService credentialService,
            TotpService totpService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.credentialService = credentialService;
        this.totpService = totpService;
    }

    @Transactional(noRollbackFor = AuthException.class)
    public LoginOutcome login(String username, String password) {
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

        if (user.isTotpEnabled() && user.getTotpSecret() != null) {
            String challenge = jwtService.generate2faChallenge(user.getId(), user.getUsername());
            return LoginOutcome.totpRequired(challenge);
        }

        return LoginOutcome.success(issue(user));
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

    @Transactional(noRollbackFor = AuthException.class)
    public LoginResult verifyTotpLogin(String challenge, String code) {
        UUID userId;
        try {
            userId = jwtService.parse2faChallenge(challenge);
        } catch (RuntimeException ex) {
            throw new AuthException("Desafio 2FA invalido ou expirado.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Usuario ou senha invalidos."));
        if (!user.isActive()) {
            throw new AuthException("Usuario inativo.");
        }
        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AuthException("Muitas tentativas. Tente novamente em alguns minutos.");
        }
        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            throw new AuthException("2FA nao esta ativo para este usuario.");
        }
        if (!totpService.verify(user.getTotpSecret(), code)) {
            int fails = user.getFailedLoginCount() + 1;
            if (fails >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_MINUTES, ChronoUnit.MINUTES));
                user.setFailedLoginCount(0);
            } else {
                user.setFailedLoginCount(fails);
            }
            userRepository.save(user);
            throw new AuthException("Codigo de verificacao invalido.");
        }
        if (user.getFailedLoginCount() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
        return issue(user);
    }

    /** Gera o segredo (pendente) e o otpauth URI; nao ativa ainda. */
    @Transactional
    public TotpSetup setupTotp(UUID userId) {
        User user = requireUser(userId);
        if (user.isTotpEnabled()) {
            throw new IllegalArgumentException("A verificacao em duas etapas ja esta ativa. Desative antes de gerar um novo codigo.");
        }
        String secret = totpService.newSecret();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false);
        userRepository.save(user);
        return new TotpSetup(secret, totpService.otpauthUri("TorqMind Ops", user.getUsername(), secret));
    }

    /** Confirma o codigo do app e ativa o 2FA. */
    @Transactional
    public void enableTotp(UUID userId, String code) {
        User user = requireUser(userId);
        if (user.isTotpEnabled()) {
            return;
        }
        if (user.getTotpSecret() == null) {
            throw new IllegalArgumentException("Gere o codigo de configuracao antes de ativar.");
        }
        if (!totpService.verify(user.getTotpSecret(), code)) {
            throw new IllegalArgumentException("Codigo de verificacao invalido.");
        }
        user.setTotpEnabled(true);
        userRepository.save(user);
    }

    /** Desativa o 2FA exigindo um codigo valido (posse do dispositivo). */
    @Transactional
    public void disableTotp(UUID userId, String code) {
        User user = requireUser(userId);
        if (!user.isTotpEnabled()) {
            user.setTotpSecret(null);
            userRepository.save(user);
            return;
        }
        if (!totpService.verify(user.getTotpSecret(), code)) {
            throw new IllegalArgumentException("Codigo de verificacao invalido.");
        }
        user.setTotpSecret(null);
        user.setTotpEnabled(false);
        userRepository.save(user);
    }

    /** Recuperacao: MASTER remove o 2FA de um usuario travado. */
    @Transactional
    public User adminDisableTotp(UUID targetUserId) {
        User user = requireUser(targetUserId);
        user.setTotpSecret(null);
        user.setTotpEnabled(false);
        return userRepository.save(user);
    }

    public boolean totpEnabled(UUID userId) {
        return requireUser(userId).isTotpEnabled();
    }

    private LoginResult issue(User user) {
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

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Usuario nao encontrado."));
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

    public record LoginOutcome(boolean totpRequired, String challenge, LoginResult result) {
        public static LoginOutcome totpRequired(String challenge) {
            return new LoginOutcome(true, challenge, null);
        }

        public static LoginOutcome success(LoginResult result) {
            return new LoginOutcome(false, null, result);
        }
    }

    public record TotpSetup(String secret, String otpauthUri) {
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
