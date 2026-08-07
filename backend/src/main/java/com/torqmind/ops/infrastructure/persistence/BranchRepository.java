package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.company.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByCompanyIdOrderById(Long companyId);
}
