package com.torqmind.ops.application.notification;

import com.torqmind.ops.application.ops.NotificationPolicy;
import com.torqmind.ops.domain.notification.Notification;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.NotificationRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final WebPushService webPushService;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
                               EmailService emailService, WebPushService webPushService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.webPushService = webPushService;
    }

    /**
     * Notifica o destinatário (se não for o autor) e, para testes/visibilidade,
     * também replica para todos os Administradores (MASTER) ativos.
     */
    @Transactional
    public void notifyCounterpart(UUID actor, UUID recipient, String entityType, Long entityId, String title, String body) {
        if (NotificationPolicy.shouldNotify(actor, recipient)) {
            save(actor, recipient, entityType, entityId, title, body);
        }
        for (User master : userRepository.findByRoleIgnoreCaseAndActiveTrue("MASTER")) {
            UUID mid = master.getId();
            if (mid == null) {
                continue;
            }
            if (recipient != null && recipient.equals(mid)) {
                continue;
            }
            if (!NotificationPolicy.shouldNotify(actor, mid)) {
                continue;
            }
            save(actor, mid, entityType, entityId, title, body);
        }
    }

    @Transactional
    public int markAllRead(UUID recipientUserId) {
        return notificationRepository.markAllRead(recipientUserId, Instant.now());
    }

    /** E-mail best-effort ao usuario (se tiver e-mail), para eventos importantes (atraso/escalonamento). */
    public void emailUser(UUID userId, String subject, String body) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(u -> emailService.send(u.getEmail(), subject, body));
    }

    private void save(UUID actor, UUID recipient, String entityType, Long entityId, String title, String body) {
        Notification notification = new Notification();
        notification.setActorUserId(actor);
        notification.setRecipientUserId(recipient);
        notification.setEntityType(entityType);
        notification.setEntityId(entityId);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setCreatedAt(Instant.now());
        notificationRepository.save(notification);
        webPushService.sendToUser(recipient, title, body, deepLink(entityType, entityId));
    }

    private static String deepLink(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            return "/";
        }
        return switch (entityType) {
            case "ROUTINE_RUN" -> "/routines/" + entityId;
            case "OCCURRENCE" -> "/occurrences/" + entityId;
            default -> "/";
        };
    }
}
