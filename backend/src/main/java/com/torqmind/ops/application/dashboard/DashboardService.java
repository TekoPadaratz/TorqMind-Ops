package com.torqmind.ops.application.dashboard;

import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final RoutineRunRepository runRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final BranchRepository branchRepository;

    public DashboardService(RoutineRunRepository runRepository, OccurrenceRepository occurrenceRepository,
                           BranchRepository branchRepository) {
        this.runRepository = runRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.branchRepository = branchRepository;
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

    /** Indicadores gerenciais: aderencia ao prazo, envelhecimento dos atrasos e ranking por filial. */
    public DashboardMetrics metrics(Long companyId, Long branchId) {
        List<RoutineRun> runs = branchId != null
                ? runRepository.findByCompanyIdAndBranchIdOrderByDueAtAsc(companyId, branchId)
                : runRepository.findByCompanyIdOrderByDueAtAsc(companyId);
        Instant now = Instant.now();
        long completed = 0;
        long onTime = 0;
        long b1 = 0;
        long b2 = 0;
        long b3 = 0;
        long b4 = 0;
        Map<Long, long[]> perBranch = new HashMap<>();
        for (RoutineRun r : runs) {
            RoutineStatus st = r.getStatus();
            if (st == RoutineStatus.CONCLUIDA && r.getCompletedAt() != null && r.getDueAt() != null) {
                completed++;
                if (!r.getCompletedAt().isAfter(r.getDueAt())) {
                    onTime++;
                }
            }
            boolean openish = st == RoutineStatus.PENDENTE || st == RoutineStatus.EM_ANDAMENTO || st == RoutineStatus.ATRASADA;
            if (openish) {
                long[] pb = perBranch.computeIfAbsent(r.getBranchId(), k -> new long[2]);
                pb[0]++;
                if (r.getDueAt() != null && r.getDueAt().isBefore(now)) {
                    pb[1]++;
                    long hours = Duration.between(r.getDueAt(), now).toHours();
                    if (hours < 24) {
                        b1++;
                    } else if (hours < 72) {
                        b2++;
                    } else if (hours < 168) {
                        b3++;
                    } else {
                        b4++;
                    }
                }
            }
        }
        int onTimeRate = completed == 0 ? 100 : (int) Math.round(100.0 * onTime / completed);
        List<BranchLoad> ranking = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : perBranch.entrySet()) {
            String name = e.getKey() == null ? "Sem filial"
                    : branchRepository.findById(e.getKey()).map(b -> b.getName()).orElse("Filial " + e.getKey());
            ranking.add(new BranchLoad(e.getKey(), name, e.getValue()[0], e.getValue()[1]));
        }
        ranking.sort(Comparator.comparingLong(BranchLoad::lateCount).reversed()
                .thenComparing(Comparator.comparingLong(BranchLoad::openCount).reversed()));
        return new DashboardMetrics(completed, onTime, onTimeRate,
                new AgingBuckets(b1, b2, b3, b4),
                ranking.stream().limit(10).toList());
    }

    public record AgingBuckets(long upTo1d, long upTo3d, long upTo7d, long over7d) {
    }

    public record BranchLoad(Long branchId, String branchName, long openCount, long lateCount) {
    }

    public record DashboardMetrics(
            long completedCount,
            long onTimeCount,
            int onTimeRate,
            AgingBuckets aging,
            List<BranchLoad> branchRanking
    ) {
    }
}
