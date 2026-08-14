package com.torqmind.ops.interfaces.rest.notification;

import com.torqmind.ops.application.notification.NotificationService;
import com.torqmind.ops.domain.notification.Notification;
import com.torqmind.ops.infrastructure.persistence.NotificationRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public NotificationController(
            NotificationRepository notificationRepository,
            NotificationService notificationService
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Notification> list(@AuthenticationPrincipal AppUserPrincipal me) {
        return notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDesc(me.userId());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppUserPrincipal me) {
        return Map.of("count", notificationRepository.countByRecipientUserIdAndReadAtIsNull(me.userId()));
    }

    /** Marca todas como lidas (não apaga). */
    @PostMapping("/mark-read")
    public Map<String, Object> markRead(@AuthenticationPrincipal AppUserPrincipal me) {
        int updated = notificationService.markAllRead(me.userId());
        return Map.of("marked", updated, "count", 0L);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AppUserPrincipal me) {
        return Map.of("updated", notificationService.markAllRead(me.userId()));
    }
}
