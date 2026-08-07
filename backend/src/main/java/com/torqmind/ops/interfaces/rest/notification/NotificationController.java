package com.torqmind.ops.interfaces.rest.notification;

import com.torqmind.ops.domain.notification.Notification;
import com.torqmind.ops.infrastructure.persistence.NotificationRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<Notification> list(@AuthenticationPrincipal AppUserPrincipal me) {
        return notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDesc(me.userId());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppUserPrincipal me) {
        return Map.of("count", notificationRepository.countByRecipientUserIdAndReadAtIsNull(me.userId()));
    }
}
