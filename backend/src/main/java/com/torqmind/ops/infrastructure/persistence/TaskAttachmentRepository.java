package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.task.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTaskTypeAndTaskIdOrderByCreatedAt(String taskType, Long taskId);
    long countByTaskTypeAndTaskId(String taskType, Long taskId);
}
