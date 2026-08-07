package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.task.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findByTaskTypeAndTaskIdOrderByCreatedAt(String taskType, Long taskId);
    long countByTaskTypeAndTaskId(String taskType, Long taskId);
}
