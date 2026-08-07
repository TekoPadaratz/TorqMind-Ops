package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.company.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
