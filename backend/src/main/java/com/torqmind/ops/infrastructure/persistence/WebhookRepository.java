package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.webhook.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {
    List<Webhook> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<Webhook> findByCompanyIdAndActiveTrue(Long companyId);
}
