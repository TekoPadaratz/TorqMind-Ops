package com.torqmind.ops;

import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.ops.StatusRules;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StatusRulesTest {

    @Test
    void allowsValidRoutineTransition() {
        Assertions.assertTrue(StatusRules.canTransitionRoutine(RoutineStatus.PENDENTE, RoutineStatus.EM_ANDAMENTO));
        Assertions.assertTrue(StatusRules.canTransitionRoutine(RoutineStatus.EM_ANDAMENTO, RoutineStatus.CONCLUIDA));
    }

    @Test
    void blocksInvalidRoutineTransition() {
        Assertions.assertFalse(StatusRules.canTransitionRoutine(RoutineStatus.CONCLUIDA, RoutineStatus.EM_ANDAMENTO));
    }

    @Test
    void allowsValidOccurrenceTransition() {
        Assertions.assertTrue(StatusRules.canTransitionOccurrence(OccurrenceStatus.ABERTA, OccurrenceStatus.EM_ATENDIMENTO));
        Assertions.assertTrue(StatusRules.canTransitionOccurrence(OccurrenceStatus.EM_ATENDIMENTO, OccurrenceStatus.AGUARDANDO_VALIDACAO));
    }

    @Test
    void blocksInvalidOccurrenceTransition() {
        Assertions.assertFalse(StatusRules.canTransitionOccurrence(OccurrenceStatus.ENCERRADA, OccurrenceStatus.EM_ATENDIMENTO));
    }
}
