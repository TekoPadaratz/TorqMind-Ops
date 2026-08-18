package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.push.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserId(UUID userId);
    Optional<PushSubscription> findByEndpoint(String endpoint);
}
