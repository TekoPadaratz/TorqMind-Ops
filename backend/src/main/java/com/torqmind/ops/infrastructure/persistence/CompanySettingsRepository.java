package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.company.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {
}
