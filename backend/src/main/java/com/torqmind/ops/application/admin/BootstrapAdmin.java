package com.torqmind.ops.application.admin;

import com.torqmind.ops.domain.user.Role;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Cria o primeiro administrador apenas quando credenciais explícitas forem informadas no ambiente. */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,40}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String fullName;

    public BootstrapAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.bootstrap-admin.full-name:Administrador}") String fullName
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.trim().toLowerCase();
        this.password = password == null ? "" : password;
        this.fullName = fullName == null ? "Administrador" : fullName.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            if (userRepository.count() == 0) {
                log.warn("Nenhum usuário cadastrado. Configure BOOTSTRAP_ADMIN_USERNAME e BOOTSTRAP_ADMIN_PASSWORD para o primeiro acesso.");
            }
            return;
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME inválido.");
        }
        PasswordPolicy.validate(password);
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            log.info("Administrador inicial já cadastrado: {}", username);
            return;
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setFullName(fullName.isBlank() ? "Administrador" : fullName);
        user.setRole(Role.MASTER.name());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
        log.info("Administrador inicial criado: {}. Remova as variáveis BOOTSTRAP_ADMIN_* após o primeiro acesso.", username);
    }
}
