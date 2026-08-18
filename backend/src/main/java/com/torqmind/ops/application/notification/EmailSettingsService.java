package com.torqmind.ops.application.notification;

import com.torqmind.ops.domain.email.EmailSettings;
import com.torqmind.ops.infrastructure.persistence.EmailSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Perfil de e-mail (SMTP/remetente) guardado no banco, editavel pelo MASTER. Env como fallback. */
@Service
public class EmailSettingsService {

    private final EmailSettingsRepository repository;
    private final String envHost;
    private final int envPort;
    private final String envUsername;
    private final String envPassword;
    private final boolean envUseTls;
    private final boolean envUseSsl;
    private final String envFrom;
    private final String envFromName;

    public EmailSettingsService(
            EmailSettingsRepository repository,
            @Value("${app.mail.host:}") String envHost,
            @Value("${app.mail.port:587}") int envPort,
            @Value("${app.mail.username:}") String envUsername,
            @Value("${app.mail.password:}") String envPassword,
            @Value("${app.mail.use-tls:true}") boolean envUseTls,
            @Value("${app.mail.use-ssl:false}") boolean envUseSsl,
            @Value("${app.mail.from:nao-responder@torqmind.com.br}") String envFrom,
            @Value("${app.mail.from-name:TorqMind Ops}") String envFromName
    ) {
        this.repository = repository;
        this.envHost = envHost;
        this.envPort = envPort;
        this.envUsername = envUsername;
        this.envPassword = envPassword;
        this.envUseTls = envUseTls;
        this.envUseSsl = envUseSsl;
        this.envFrom = envFrom;
        this.envFromName = envFromName;
    }

    public EmailSettings get() {
        return repository.findById(1).orElseGet(() -> {
            EmailSettings fresh = new EmailSettings();
            fresh.setId(1);
            return fresh;
        });
    }

    /** Atualiza o perfil. A senha só é alterada quando informada (null = mantém a atual). */
    @Transactional
    public EmailSettings update(boolean enabled, String host, Integer port, String username, String password,
                               boolean useTls, boolean useSsl, String fromEmail, String fromName) {
        EmailSettings s = get();
        String cleanHost = trimOrNull(host);
        String cleanFrom = trimOrNull(fromEmail);
        if (enabled) {
            if (cleanHost == null) {
                throw new IllegalArgumentException("Informe o servidor SMTP (host).");
            }
            if (cleanFrom == null || !cleanFrom.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new IllegalArgumentException("Informe um e-mail remetente valido.");
            }
        }
        s.setEnabled(enabled);
        s.setHost(cleanHost);
        s.setPort(port == null ? 587 : Math.max(1, Math.min(65535, port)));
        s.setUsername(trimOrNull(username));
        if (password != null) {
            s.setPassword(password.isBlank() ? null : password);
        }
        s.setUseTls(useTls);
        s.setUseSsl(useSsl);
        s.setFromEmail(cleanFrom);
        s.setFromName(trimOrDefault(fromName, "TorqMind Ops"));
        s.setUpdatedAt(Instant.now());
        return repository.save(s);
    }

    /** Config efetiva: perfil do banco (se habilitado e com host) senao o ambiente. */
    public SmtpRuntime resolveRuntime() {
        EmailSettings db = get();
        if (db.isEnabled() && db.getHost() != null && !db.getHost().isBlank()) {
            String from = trimOrNull(db.getFromEmail()) != null ? db.getFromEmail().trim() : envFrom;
            return new SmtpRuntime(true, db.getHost().trim(), db.getPort(),
                    nvl(db.getUsername()), nvl(db.getPassword()), db.isUseTls(), db.isUseSsl(),
                    from, trimOrDefault(db.getFromName(), "TorqMind Ops"));
        }
        if (envHost != null && !envHost.isBlank()) {
            return new SmtpRuntime(true, envHost.trim(), envPort,
                    nvl(envUsername), nvl(envPassword), envUseTls, envUseSsl, envFrom, envFromName);
        }
        return new SmtpRuntime(false, "", envPort, "", "", envUseTls, envUseSsl, envFrom, envFromName);
    }

    private static String nvl(String v) {
        return v == null ? "" : v;
    }

    private static String trimOrNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimOrDefault(String v, String def) {
        String t = trimOrNull(v);
        return t == null ? def : t;
    }

    public record SmtpRuntime(
            boolean enabled, String host, int port, String username, String password,
            boolean useTls, boolean useSsl, String fromEmail, String fromName) {
    }
}
