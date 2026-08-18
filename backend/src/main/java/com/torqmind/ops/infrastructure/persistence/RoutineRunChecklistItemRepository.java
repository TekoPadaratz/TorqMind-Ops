package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.routine.RoutineRunChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutineRunChecklistItemRepository extends JpaRepository<RoutineRunChecklistItem, Long> {
    List<RoutineRunChecklistItem> findByRunIdOrderByPositionAsc(Long runId);
    long countByRunIdAndRequiredTrueAndCheckedFalse(Long runId);
    boolean existsByRunId(Long runId);
}
