package com.torqmind.ops.domain.ops;

import java.util.Set;

public final class StatusRules {
    private StatusRules() {}

    public static boolean canTransitionRoutine(RoutineStatus current, RoutineStatus next) {
        return switch (current) {
            case PENDENTE -> Set.of(RoutineStatus.EM_ANDAMENTO, RoutineStatus.ATRASADA, RoutineStatus.REJEITADA).contains(next);
            case EM_ANDAMENTO -> Set.of(RoutineStatus.CONCLUIDA, RoutineStatus.REJEITADA, RoutineStatus.ATRASADA).contains(next);
            case ATRASADA -> Set.of(RoutineStatus.EM_ANDAMENTO, RoutineStatus.CONCLUIDA, RoutineStatus.REJEITADA).contains(next);
            case CONCLUIDA, REJEITADA -> false;
        };
    }

    public static boolean canTransitionOccurrence(OccurrenceStatus current, OccurrenceStatus next) {
        return switch (current) {
            case ABERTA -> Set.of(OccurrenceStatus.EM_ATENDIMENTO, OccurrenceStatus.REJEITADA).contains(next);
            case EM_ATENDIMENTO -> Set.of(OccurrenceStatus.AGUARDANDO_VALIDACAO, OccurrenceStatus.REJEITADA).contains(next);
            case AGUARDANDO_VALIDACAO -> Set.of(OccurrenceStatus.ENCERRADA, OccurrenceStatus.REJEITADA, OccurrenceStatus.EM_ATENDIMENTO).contains(next);
            case ENCERRADA, REJEITADA -> false;
        };
    }
}
