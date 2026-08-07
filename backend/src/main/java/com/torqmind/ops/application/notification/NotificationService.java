package com.torqmind.ops.application.notification;

import com.torqmind.ops.application.ops.NotificationPolicy;
import com.torqmind.ops.domain.notification.Notification;
import com.torqmind.ops.infrastructure.persistence.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Cria uma notificação apenas quando o destinatário não é o próprio autor da ação. */
    public void notifyCounterpart(UUID actor, UUID recipient, String entityType, Long entityId, String title, String body) {
        if (!NotificationPolicy.shouldNotify(actor, recipient)) {
            return;
        }
        Notification notification = new Notification();
        notification.setActorUserId(actor);
        notification.setRecipientUserId(recipient);
        notification.setEntityType(entityType);
        notification.setEntityId(entityId);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setCreatedAt(Instant.now());
        notificationRepository.save(notification);
    }
}
