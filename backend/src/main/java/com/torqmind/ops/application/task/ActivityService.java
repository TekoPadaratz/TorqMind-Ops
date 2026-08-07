package com.torqmind.ops.application.task;

import com.torqmind.ops.domain.task.TaskActivity;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.infrastructure.persistence.TaskActivityRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ActivityService {

    private final TaskActivityRepository activityRepository;

    public ActivityService(TaskActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public void record(TaskType type, Long taskId, UUID actor, String activityType, String fromStatus, String toStatus, String message) {
        TaskActivity activity = new TaskActivity();
        activity.setTaskType(type.name());
        activity.setTaskId(taskId);
        activity.setActorUserId(actor);
        activity.setActivityType(activityType);
        activity.setFromStatus(fromStatus);
        activity.setToStatus(toStatus);
        activity.setMessage(message);
        activity.setCreatedAt(Instant.now());
        activityRepository.save(activity);
    }
}
