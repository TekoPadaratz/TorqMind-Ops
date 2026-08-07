package com.torqmind.ops.application.dashboard;

import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DashboardService {

    private final RoutineRunRepository runRepository;
    private final OccurrenceRepository occurrenceRepository;

    public DashboardService(RoutineRunRepository runRepository, OccurrenceRepository occurrenceRepository) {
        this.runRepository = runRepository;
        this.occurrenceRepository = occurrenceRepository;
    }

    public DashboardSummary summary(Long companyId, Long branchId) {
        long pending;
        long inProgress;
        long late;
        long open;
        long awaiting;
        List<RoutineRun> runs;
        List<Occurrence> openOccurrences;

        if (branchId != null) {
            pending = runRepository.countByCompanyIdAndBranchIdAndStatus(companyId, branchId, RoutineStatus.PENDENTE);
            inProgress = runRepository.countByCompanyIdAndBranchIdAndStatus(companyId, branchId, RoutineStatus.EM_ANDAMENTO);
            late = runRepository.countByCompanyIdAndBranchIdAndStatus(companyId, branchId, RoutineStatus.ATRASADA);
            open = occurrenceRepository.countByCompanyIdAndBranchIdAndStatus(companyId, branchId, OccurrenceStatus.ABERTA);
            awaiting = occurrenceRepository.countByCompanyIdAndBranchIdAndStatus(companyId, branchId, OccurrenceStatus.AGUARDANDO_VALIDACAO);
            runs = runRepository.findByCompanyIdAndBranchIdOrderByDueAtAsc(companyId, branchId);
            openOccurrences = occurrenceRepository
                    .findByCompanyIdAndBranchIdAndStatusOrderByCreatedAtDesc(companyId, branchId, OccurrenceStatus.ABERTA);
        } else {
            pending = runRepository.countByCompanyIdAndStatus(companyId, RoutineStatus.PENDENTE);
            inProgress = runRepository.countByCompanyIdAndStatus(companyId, RoutineStatus.EM_ANDAMENTO);
            late = runRepository.countByCompanyIdAndStatus(companyId, RoutineStatus.ATRASADA);
            open = occurrenceRepository.countByCompanyIdAndStatus(companyId, OccurrenceStatus.ABERTA);
            awaiting = occurrenceRepository.countByCompanyIdAndStatus(companyId, OccurrenceStatus.AGUARDANDO_VALIDACAO);
            runs = runRepository.findByCompanyIdOrderByDueAtAsc(companyId);
            openOccurrences = occurrenceRepository
                    .findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, OccurrenceStatus.ABERTA);
        }

        Instant now = Instant.now();
        List<RoutineRun> lateRuns = runs.stream()
                .filter(r -> r.getStatus() == RoutineStatus.PENDENTE || r.getStatus() == RoutineStatus.EM_ANDAMENTO
                        || r.getStatus() == RoutineStatus.ATRASADA)
                .filter(r -> r.getDueAt() != null && r.getDueAt().isBefore(now))
                .limit(5)
                .toList();

        return new DashboardSummary(
                pending, inProgress, late, open, awaiting,
                lateRuns,
                openOccurrences.stream().limit(5).toList()
        );
    }

    public record DashboardSummary(
            long routinesPending,
            long routinesInProgress,
            long routinesLate,
            long occurrencesOpen,
            long occurrencesAwaitingValidation,
            List<RoutineRun> lateRuns,
            List<Occurrence> openOccurrences
    ) {
    }
}
