package com.torqmind.ops.application.dashboard;

import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private final RoutineRunRepository runRepository;
    private final OccurrenceRepository occurrenceRepository;
    private final BranchRepository branchRepository;
    private final RoutineTemplateRepository templateRepository;
    private final UserRepository userRepository;

    public DashboardService(RoutineRunRepository runRepository, OccurrenceRepository occurrenceRepository,
                           BranchRepository branchRepository, RoutineTemplateRepository templateRepository,
                           UserRepository userRepository) {
        this.runRepository = runRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.branchRepository = branchRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
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

    /** Relatorio gerencial por periodo: rotinas agendadas no intervalo + ocorrencias abertas no intervalo. */
    public ReportData report(Long companyId, Long branchId, Instant from, Instant to) {
        List<RoutineRun> runs = branchId != null
                ? runRepository.findByCompanyIdAndBranchIdAndScheduledForBetweenOrderByScheduledForAsc(companyId, branchId, from, to)
                : runRepository.findByCompanyIdAndScheduledForBetweenOrderByScheduledForAsc(companyId, from, to);
        Instant now = Instant.now();
        long completed = 0, onTime = 0, pending = 0, inProgress = 0, late = 0, rejected = 0;
        long b1 = 0, b2 = 0, b3 = 0, b4 = 0;
        Map<Long, long[]> perBranch = new HashMap<>();
        Map<Long, String> titles = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        List<OverdueLine> overdue = new ArrayList<>();
        for (RoutineRun r : runs) {
            switch (r.getStatus()) {
                case CONCLUIDA -> {
                    completed++;
                    if (r.getCompletedAt() != null && r.getDueAt() != null && !r.getCompletedAt().isAfter(r.getDueAt())) {
                        onTime++;
                    }
                }
                case PENDENTE -> pending++;
                case EM_ANDAMENTO -> inProgress++;
                case ATRASADA -> late++;
                case REJEITADA -> rejected++;
            }
            boolean openish = r.getStatus() == RoutineStatus.PENDENTE || r.getStatus() == RoutineStatus.EM_ANDAMENTO
                    || r.getStatus() == RoutineStatus.ATRASADA;
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
                    if (overdue.size() < 40) {
                        String title = titles.computeIfAbsent(r.getTemplateId(),
                                id -> templateRepository.findById(id).map(RoutineTemplate::getTitle).orElse("Rotina"));
                        String who = r.getAssignedUserId() == null ? "-"
                                : names.computeIfAbsent(r.getAssignedUserId(),
                                        id -> userRepository.findById(id).map(User::getFullName).orElse("-"));
                        overdue.add(new OverdueLine(r.getId(), title, branchName(r.getBranchId()), who, r.getDueAt()));
                    }
                }
            }
        }
        int onTimeRate = completed == 0 ? 100 : (int) Math.round(100.0 * onTime / completed);
        List<BranchLoad> ranking = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : perBranch.entrySet()) {
            ranking.add(new BranchLoad(e.getKey(), branchName(e.getKey()), e.getValue()[0], e.getValue()[1]));
        }
        ranking.sort(Comparator.comparingLong(BranchLoad::lateCount).reversed()
                .thenComparing(Comparator.comparingLong(BranchLoad::openCount).reversed()));

        List<Occurrence> occ = branchId != null
                ? occurrenceRepository.findByCompanyIdAndBranchIdAndCreatedAtBetween(companyId, branchId, from, to)
                : occurrenceRepository.findByCompanyIdAndCreatedAtBetween(companyId, from, to);
        long occOpen = 0, occAtt = 0, occAwait = 0, occClosed = 0, occRejected = 0;
        for (Occurrence o : occ) {
            switch (o.getStatus()) {
                case ABERTA -> occOpen++;
                case EM_ATENDIMENTO -> occAtt++;
                case AGUARDANDO_VALIDACAO -> occAwait++;
                case ENCERRADA -> occClosed++;
                case REJEITADA -> occRejected++;
            }
        }

        return new ReportData(
                branchId == null ? null : branchName(branchId), from, to,
                runs.size(), completed, onTime, onTimeRate, pending, inProgress, late, rejected,
                new AgingBuckets(b1, b2, b3, b4),
                ranking.stream().limit(10).toList(),
                overdue,
                occ.size(), occOpen, occAtt, occAwait, occClosed, occRejected);
    }

    private String branchName(Long branchId) {
        if (branchId == null) {
            return "Sem filial";
        }
        return branchRepository.findById(branchId).map(b -> b.getName()).orElse("Filial " + branchId);
    }

    public record OverdueLine(Long id, String title, String branchName, String assignee, Instant dueAt) {
    }

    public record ReportData(
            String branchName, Instant from, Instant to,
            long total, long completed, long onTime, int onTimeRate,
            long pending, long inProgress, long late, long rejected,
            AgingBuckets aging, List<BranchLoad> branchRanking, List<OverdueLine> overdue,
            long occTotal, long occOpen, long occInAttendance, long occAwaiting, long occClosed, long occRejected
    ) {
    }
}
