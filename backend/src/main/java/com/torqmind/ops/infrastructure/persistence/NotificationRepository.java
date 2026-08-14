package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);
    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :readAt where n.recipientUserId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
