package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.routine.RoutineChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutineChecklistItemRepository extends JpaRepository<RoutineChecklistItem, Long> {
    List<RoutineChecklistItem> findByTemplateIdOrderByPositionAsc(Long templateId);
}
