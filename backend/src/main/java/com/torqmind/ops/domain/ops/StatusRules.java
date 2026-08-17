package com.torqmind.ops.domain.ops;

import com.torqmind.ops.domain.occurrence.OccurrenceKind;

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

    /**
     * Análise de qualidade: rascunho permanece ABERTA (ou no fluxo operacional)
     * e o checkbox de finalizar vai direto para ENCERRADA. Reabertura continua
     * bloqueada — ENCERRADA/REJEITADA são terminais como no fluxo genérico.
     */
    public static boolean canTransitionOccurrence(
            OccurrenceStatus current,
            OccurrenceStatus next,
            OccurrenceKind kind
    ) {
        if (kind == OccurrenceKind.FUEL_QUALITY_RECEIPT
                && next == OccurrenceStatus.ENCERRADA
                && current != OccurrenceStatus.ENCERRADA
                && current != OccurrenceStatus.REJEITADA) {
            return true;
        }
        return canTransitionOccurrence(current, next);
    }

    public static boolean canEditQualityReceipt(OccurrenceStatus status) {
        return status != OccurrenceStatus.ENCERRADA && status != OccurrenceStatus.REJEITADA;
    }
}
