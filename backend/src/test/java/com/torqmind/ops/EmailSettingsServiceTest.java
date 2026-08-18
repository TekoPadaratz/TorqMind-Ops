package com.torqmind.ops;

import com.torqmind.ops.application.notification.EmailSettingsService;
import com.torqmind.ops.domain.email.EmailSettings;
import com.torqmind.ops.infrastructure.persistence.EmailSettingsRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

class EmailSettingsServiceTest {

    private EmailSettingsService svc(EmailSettings stored, String envHost) {
        EmailSettingsRepository repo = Mockito.mock(EmailSettingsRepository.class);
        Mockito.when(repo.findById(1)).thenReturn(stored == null ? Optional.empty() : Optional.of(stored));
        Mockito.when(repo.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));
        return new EmailSettingsService(repo, envHost, 587, "envuser", "envpass", true, false,
                "env@torqmind.com", "TorqMind Ops");
    }

    @Test
    void dbProfileWinsWhenEnabledWithHost() {
        EmailSettings db = new EmailSettings();
        db.setEnabled(true);
        db.setHost("smtp.provedor.com");
        db.setUsername("user");
        db.setPassword("secret");
        db.setFromEmail("naoresponder@cliente.com");
        EmailSettingsService.SmtpRuntime rt = svc(db, "envhost.com").resolveRuntime();
        Assertions.assertTrue(rt.enabled());
        Assertions.assertEquals("smtp.provedor.com", rt.host());
        Assertions.assertEquals("naoresponder@cliente.com", rt.fromEmail());
    }

    @Test
    void fallsBackToEnvWhenDbDisabled() {
        EmailSettingsService.SmtpRuntime rt = svc(new EmailSettings(), "envhost.com").resolveRuntime();
        Assertions.assertTrue(rt.enabled());
        Assertions.assertEquals("envhost.com", rt.host());
    }

    @Test
    void disabledWhenNoDbAndNoEnv() {
        Assertions.assertFalse(svc(null, "").resolveRuntime().enabled());
    }

    @Test
    void updateKeepsPasswordWhenNull() {
        EmailSettings db = new EmailSettings();
        db.setPassword("old-secret");
        EmailSettings updated = svc(db, "").update(true, "smtp.x.com", 465, "u", null, false, true, "from@x.com", "X");
        Assertions.assertEquals("old-secret", updated.getPassword());
        Assertions.assertTrue(updated.isEnabled());
        Assertions.assertEquals(465, updated.getPort());
    }

    @Test
    void updateRejectsEnableWithoutHostOrValidFrom() {
        EmailSettingsService s = svc(new EmailSettings(), "");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> s.update(true, null, 587, "u", "p", true, false, "from@x.com", "X"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> s.update(true, "smtp.x.com", 587, "u", "p", true, false, "bad-email", "X"));
    }
}
