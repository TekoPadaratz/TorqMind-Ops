package com.torqmind.ops.application.auth;

import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.PasswordChangeEventRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.JwtService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

class AuthTotpLoginTest {

    // Segredo padrao RFC 6238.
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private UserRepository userRepository;
    private AuthService authService;
    private TotpService totpService;
    private BCryptPasswordEncoder encoder;
    private UUID userId;

    @BeforeEach
    void setup() {
        userRepository = Mockito.mock(UserRepository.class);
        PasswordChangeEventRepository eventRepository = Mockito.mock(PasswordChangeEventRepository.class);
        encoder = new BCryptPasswordEncoder();
        CredentialService credentialService = new CredentialService(encoder, userRepository, eventRepository);
        JwtService jwtService = new JwtService("unit-test-secret-with-at-least-32-chars!!", "torqmind-ops", 60);
        totpService = new TotpService();
        authService = new AuthService(userRepository, encoder, jwtService, credentialService, totpService);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        userId = UUID.randomUUID();
    }

    private User enrolledUser() {
        User user = new User();
        user.setId(userId);
        user.setUsername("chefe");
        user.setFullName("Chefe Master");
        user.setRole("MASTER");
        user.setActive(true);
        user.setPasswordHash(encoder.encode("Senha123"));
        user.setTotpSecret(SECRET);
        user.setTotpEnabled(true);
        return user;
    }

    @Test
    void loginWithTotpEnabledReturnsChallengeThenTokenOnValidCode() {
        User user = enrolledUser();
        Mockito.when(userRepository.findByUsernameIgnoreCase("chefe")).thenReturn(Optional.of(user));
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AuthService.LoginOutcome outcome = authService.login("chefe", "Senha123");
        Assertions.assertTrue(outcome.totpRequired());
        Assertions.assertNull(outcome.result());
        Assertions.assertNotNull(outcome.challenge());

        String code = totpService.code(SECRET, Instant.now().getEpochSecond());
        AuthService.LoginResult result = authService.verifyTotpLogin(outcome.challenge(), code);
        Assertions.assertNotNull(result.token());
        Assertions.assertEquals("chefe", result.username());
    }

    @Test
    void verifyTotpLoginRejectsWrongCode() {
        User user = enrolledUser();
        Mockito.when(userRepository.findByUsernameIgnoreCase("chefe")).thenReturn(Optional.of(user));
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AuthService.LoginOutcome outcome = authService.login("chefe", "Senha123");
        String valid = totpService.code(SECRET, Instant.now().getEpochSecond());
        String wrong = "000000".equals(valid) ? "111111" : "000000";
        Assertions.assertThrows(AuthService.AuthException.class,
                () -> authService.verifyTotpLogin(outcome.challenge(), wrong));
    }

    @Test
    void loginWithoutTotpIssuesTokenDirectly() {
        User user = enrolledUser();
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        Mockito.when(userRepository.findByUsernameIgnoreCase("chefe")).thenReturn(Optional.of(user));

        AuthService.LoginOutcome outcome = authService.login("chefe", "Senha123");
        Assertions.assertFalse(outcome.totpRequired());
        Assertions.assertNotNull(outcome.result());
        Assertions.assertNotNull(outcome.result().token());
    }

    @Test
    void setupThenEnableActivatesTotp() {
        User user = enrolledUser();
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AuthService.TotpSetup setup = authService.setupTotp(userId);
        Assertions.assertNotNull(setup.secret());
        Assertions.assertTrue(setup.otpauthUri().startsWith("otpauth://totp/"));
        Assertions.assertFalse(user.isTotpEnabled());

        String code = totpService.code(setup.secret(), Instant.now().getEpochSecond());
        authService.enableTotp(userId, code);
        Assertions.assertTrue(user.isTotpEnabled());
    }
}
