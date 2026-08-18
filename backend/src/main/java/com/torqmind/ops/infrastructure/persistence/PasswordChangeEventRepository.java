package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.user.PasswordChangeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PasswordChangeEventRepository extends JpaRepository<PasswordChangeEvent, Long> {
    List<PasswordChangeEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
