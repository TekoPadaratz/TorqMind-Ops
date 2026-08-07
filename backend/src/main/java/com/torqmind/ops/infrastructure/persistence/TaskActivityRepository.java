package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.task.TaskActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {
    List<TaskActivity> findByTaskTypeAndTaskIdOrderByCreatedAt(String taskType, Long taskId);
}
