package com.torqmind.ops;

import com.torqmind.ops.application.auth.CredentialService;
import com.torqmind.ops.application.auth.PasswordRecoveryService;
import com.torqmind.ops.application.notification.EmailService;
import com.torqmind.ops.domain.user.PasswordResetToken;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.PasswordChangeEventRepository;
import com.torqmind.ops.infrastructure.persistence.PasswordResetTokenRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

class PasswordRecoveryTest {

    private UserRepository userRepo;
    private PasswordResetTokenRepository tokenRepo;
    private EmailService emailService;
    private PasswordRecoveryService service;
    private BCryptPasswordEncoder encoder;
    private UUID userId;

    @BeforeEach
    void setup() {
        userRepo = Mockito.mock(UserRepository.class);
        tokenRepo = Mockito.mock(PasswordResetTokenRepository.class);
        encoder = new BCryptPasswordEncoder();
        CredentialService credentialService =
                new CredentialService(encoder, userRepo, Mockito.mock(PasswordChangeEventRepository.class));
        emailService = Mockito.mock(EmailService.class);
        service = new PasswordRecoveryService(userRepo, tokenRepo, credentialService, emailService, "http://localhost");
        Mockito.when(tokenRepo.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));
        userId = UUID.randomUUID();
    }

    private User user() {
        User u = new User();
        u.setId(userId);
        u.setUsername("ana");
        u.setEmail("ana@posto.com");
        u.setActive(true);
        u.setPasswordHash(encoder.encode("Antiga123"));
        return u;
    }

    @Test
    void requestResetSavesTokenAndEmailsExistingUser() {
        Mockito.when(userRepo.findFirstByEmailIgnoreCaseAndActiveTrue("ana@posto.com")).thenReturn(Optional.of(user()));
        service.requestReset("ana@posto.com");
        Mockito.verify(tokenRepo).save(Mockito.any(PasswordResetToken.class));
        Mockito.verify(emailService).send(Mockito.eq("ana@posto.com"), Mockito.anyString(), Mockito.contains("/reset?token="));
    }

    @Test
    void requestResetUnknownEmailDoesNothing() {
        Mockito.when(userRepo.findFirstByEmailIgnoreCaseAndActiveTrue(Mockito.any())).thenReturn(Optional.empty());
        service.requestReset("nao@existe.com");
        Mockito.verify(tokenRepo, Mockito.never()).save(Mockito.any());
        Mockito.verify(emailService, Mockito.never()).send(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void resetWithValidTokenChangesPassword() {
        User u = user();
        Mockito.when(userRepo.findFirstByEmailIgnoreCaseAndActiveTrue("ana@posto.com")).thenReturn(Optional.of(u));
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        service.requestReset("ana@posto.com");
        Mockito.verify(tokenRepo).save(tokenCaptor.capture());
        Mockito.verify(emailService).send(Mockito.any(), Mockito.any(), bodyCaptor.capture());

        String body = bodyCaptor.getValue();
        String raw = body.substring(body.indexOf("token=") + 6).split("\\s+")[0];
        PasswordResetToken saved = tokenCaptor.getValue();

        Mockito.when(tokenRepo.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));
        Mockito.when(userRepo.findById(userId)).thenReturn(Optional.of(u));

        service.reset(raw, "NovaSenha1");

        Assertions.assertTrue(encoder.matches("NovaSenha1", u.getPasswordHash()));
        Assertions.assertTrue(saved.isUsed());
    }

    @Test
    void resetWithInvalidTokenFails() {
        Mockito.when(tokenRepo.findByTokenHash(Mockito.any())).thenReturn(Optional.empty());
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.reset("bogus", "NovaSenha1"));
    }
}
