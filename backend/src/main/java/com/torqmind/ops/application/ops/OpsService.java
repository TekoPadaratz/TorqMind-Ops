package com.torqmind.ops.application.ops;

import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.ops.StatusRules;
import org.springframework.stereotype.Service;

@Service
public class OpsService {

    public void validateRoutineTransition(RoutineStatus current, RoutineStatus next) {
        if (!StatusRules.canTransitionRoutine(current, next)) {
            throw new IllegalArgumentException("Transicao de status de rotina invalida.");
        }
    }

    public void validateOccurrenceTransition(OccurrenceStatus current, OccurrenceStatus next) {
        if (!StatusRules.canTransitionOccurrence(current, next)) {
            throw new IllegalArgumentException("Transicao de status de ocorrencia invalida.");
        }
    }
}
