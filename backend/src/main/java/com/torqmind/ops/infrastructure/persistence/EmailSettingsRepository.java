package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.email.EmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailSettingsRepository extends JpaRepository<EmailSettings, Integer> {
}
