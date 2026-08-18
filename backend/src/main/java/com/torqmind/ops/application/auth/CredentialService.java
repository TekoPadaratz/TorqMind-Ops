package com.torqmind.ops.application.auth;

import com.torqmind.ops.application.admin.PasswordPolicy;
import com.torqmind.ops.domain.user.PasswordChangeEvent;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.PasswordChangeEventRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CredentialService {

    public static final String ACTION_CREATED = "CREATED";
    public static final String ACTION_SELF_CHANGE = "SELF_CHANGE";
    public static final String ACTION_ADMIN_RESET = "ADMIN_RESET";

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final PasswordChangeEventRepository eventRepository;

    public CredentialService(
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            PasswordChangeEventRepository eventRepository
    ) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void assignPassword(User user, UUID actorId, String rawPassword, String action, boolean bumpEpoch) {
        PasswordPolicy.validate(rawPassword);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        Instant now = Instant.now();
        user.setPasswordChangedAt(now);
        user.setUpdatedAt(now);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        if (bumpEpoch) {
            user.setPasswordEpoch(user.getPasswordEpoch() + 1);
        }
        userRepository.save(user);
        record(user.getId(), actorId, action, now);
    }

    public boolean matches(User user, String rawPassword) {
        return user != null && rawPassword != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    private void record(UUID userId, UUID actorId, String action, Instant now) {
        PasswordChangeEvent event = new PasswordChangeEvent();
        event.setUserId(userId);
        event.setActorUserId(actorId);
        event.setAction(action);
        event.setCreatedAt(now);
        eventRepository.save(event);
    }
}
