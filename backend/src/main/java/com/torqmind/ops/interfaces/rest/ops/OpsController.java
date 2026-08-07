package com.torqmind.ops.interfaces.rest.ops;

import com.torqmind.ops.application.ops.NotificationPolicy;
import com.torqmind.ops.application.ops.OpsService;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.RoutineStatus;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ops")
@Validated
public class OpsController {

    private final OpsService opsService;

    public OpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    @PostMapping("/routines/{id}/status")
    public Map<String, Object> updateRoutineStatus(
            @PathVariable Long id,
            @RequestParam RoutineStatus current,
            @RequestParam RoutineStatus next
    ) {
        opsService.validateRoutineTransition(current, next);
        return Map.of("id", id, "status", next.name());
    }

    @PostMapping("/occurrences/{id}/status")
    public Map<String, Object> updateOccurrenceStatus(
            @PathVariable Long id,
            @RequestParam OccurrenceStatus current,
            @RequestParam OccurrenceStatus next
    ) {
        opsService.validateOccurrenceTransition(current, next);
        return Map.of("id", id, "status", next.name());
    }

    @GetMapping("/notifications/should-notify")
    public Map<String, Object> shouldNotify(
            @RequestParam @NotNull UUID actorUserId,
            @RequestParam @NotNull UUID recipientUserId
    ) {
        return Map.of("notify", NotificationPolicy.shouldNotify(actorUserId, recipientUserId));
    }
}
