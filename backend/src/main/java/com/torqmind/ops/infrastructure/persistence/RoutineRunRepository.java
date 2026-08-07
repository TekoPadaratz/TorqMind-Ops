package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutineRunRepository extends JpaRepository<RoutineRun, Long> {
    List<RoutineRun> findByCompanyIdOrderByDueAtAsc(Long companyId);
    List<RoutineRun> findByCompanyIdAndStatusOrderByDueAtAsc(Long companyId, RoutineStatus status);
    List<RoutineRun> findByCompanyIdAndBranchIdOrderByDueAtAsc(Long companyId, Long branchId);
    List<RoutineRun> findByCompanyIdAndBranchIdAndStatusOrderByDueAtAsc(Long companyId, Long branchId, RoutineStatus status);
    long countByCompanyIdAndStatus(Long companyId, RoutineStatus status);
    long countByCompanyIdAndBranchIdAndStatus(Long companyId, Long branchId, RoutineStatus status);
    List<RoutineRun> findByStatusIn(java.util.List<RoutineStatus> statuses);
}
