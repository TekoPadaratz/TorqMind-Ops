package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.routine.RoutineTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutineTemplateRepository extends JpaRepository<RoutineTemplate, Long> {
    List<RoutineTemplate> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<RoutineTemplate> findByCompanyIdAndActiveTrueOrderByCreatedAtDesc(Long companyId);
    List<RoutineTemplate> findByActiveTrueAndNotifyTimeIsNotNull();
    List<RoutineTemplate> findByActiveTrueAndStartTimeIsNotNull();
}
