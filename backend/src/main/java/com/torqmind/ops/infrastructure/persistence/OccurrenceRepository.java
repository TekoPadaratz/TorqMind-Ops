package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OccurrenceRepository extends JpaRepository<Occurrence, Long> {
    List<Occurrence> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<Occurrence> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, OccurrenceStatus status);
    List<Occurrence> findByCompanyIdAndBranchIdOrderByCreatedAtDesc(Long companyId, Long branchId);
    List<Occurrence> findByCompanyIdAndBranchIdAndStatusOrderByCreatedAtDesc(Long companyId, Long branchId, OccurrenceStatus status);
    long countByCompanyIdAndStatus(Long companyId, OccurrenceStatus status);
    long countByCompanyIdAndBranchIdAndStatus(Long companyId, Long branchId, OccurrenceStatus status);
    List<Occurrence> findByCompanyIdAndCreatedAtBetween(Long companyId, java.time.Instant from, java.time.Instant to);
    List<Occurrence> findByCompanyIdAndBranchIdAndCreatedAtBetween(Long companyId, Long branchId, java.time.Instant from, java.time.Instant to);
}
