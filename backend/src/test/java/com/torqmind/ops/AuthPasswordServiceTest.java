package com.torqmind.ops;

import com.torqmind.ops.application.auth.AuthService;
import com.torqmind.ops.application.auth.CredentialService;
import com.torqmind.ops.domain.user.PasswordChangeEvent;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.PasswordChangeEventRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.JwtService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

class AuthPasswordServiceTest {

    private UserRepository userRepository;
    private PasswordChangeEventRepository eventRepository;
    private AuthService authService;
    private JwtService jwtService;
    private BCryptPasswordEncoder encoder;
    private UUID userId;

    @BeforeEach
    void setup() {
        userRepository = Mockito.mock(UserRepository.class);
        eventRepository = Mockito.mock(PasswordChangeEventRepository.class);
        encoder = new BCryptPasswordEncoder();
        CredentialService credentialService = new CredentialService(encoder, userRepository, eventRepository);
        jwtService = new JwtService("unit-test-secret-with-at-least-32-chars!!", "torqmind-ops", 60);
        authService = new AuthService(userRepository, encoder, jwtService, credentialService);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(eventRepository.save(Mockito.any(PasswordChangeEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        userId = UUID.randomUUID();
    }

    @Test
    void wrongCurrentPasswordDoesNotUseUnauthorized() {
        User user = stored("Atual123");
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> authService.changeOwnPassword(userId, "errada", "NovaSenha1"));
        Assertions.assertEquals("Senha atual incorreta.", ex.getMessage());
        Mockito.verify(eventRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void rejectsReusingTheCurrentPassword() {
        User user = stored("Atual123");
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> authService.changeOwnPassword(userId, "Atual123", "Atual123"));
        Assertions.assertEquals("A nova senha deve ser diferente da atual.", ex.getMessage());
    }

    @Test
    void selfChangeBumpsEpochAndReturnsFreshToken() {
        User user = stored("Atual123");
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AuthService.LoginResult result = authService.changeOwnPassword(userId, "Atual123", "NovaSenha1");

        Assertions.assertEquals(1, user.getPasswordEpoch());
        Assertions.assertTrue(encoder.matches("NovaSenha1", user.getPasswordHash()));
        Assertions.assertEquals(1, jwtService.parseToken(result.token()).passwordEpoch());
        ArgumentCaptor<PasswordChangeEvent> captor = ArgumentCaptor.forClass(PasswordChangeEvent.class);
        Mockito.verify(eventRepository).save(captor.capture());
        Assertions.assertEquals(CredentialService.ACTION_SELF_CHANGE, captor.getValue().getAction());
    }

    @Test
    void loginTokenCarriesPasswordEpoch() {
        User user = stored("Atual123");
        user.setPasswordEpoch(4);
        Mockito.when(userRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(user));

        AuthService.LoginResult result = authService.login("ana", "Atual123");
        Assertions.assertEquals(4, jwtService.parseToken(result.token()).passwordEpoch());
    }

    private User stored(String rawPassword) {
        User user = new User();
        user.setId(userId);
        user.setUsername("ana");
        user.setFullName("Ana Operadora");
        user.setRole("OPERATOR");
        user.setActive(true);
        user.setCompanyId(1L);
        user.setBranchId(2L);
        user.setPasswordHash(encoder.encode(rawPassword));
        return user;
    }
}
