package com.torqmind.ops;

import com.torqmind.ops.application.dashboard.DashboardService;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

class DashboardMetricsTest {

    private RoutineRun run(RoutineStatus status, Instant due, Instant completed) {
        RoutineRun r = new RoutineRun();
        r.setStatus(status);
        r.setDueAt(due);
        r.setCompletedAt(completed);
        r.setBranchId(1L);
        return r;
    }

    @Test
    void computesOnTimeRateAndAging() {
        RoutineRunRepository runRepo = Mockito.mock(RoutineRunRepository.class);
        OccurrenceRepository occRepo = Mockito.mock(OccurrenceRepository.class);
        BranchRepository branchRepo = Mockito.mock(BranchRepository.class);
        Instant now = Instant.now();
        List<RoutineRun> runs = List.of(
                run(RoutineStatus.CONCLUIDA, now.minus(1, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS)),
                run(RoutineStatus.CONCLUIDA, now.minus(3, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS)),
                run(RoutineStatus.PENDENTE, now.minus(48, ChronoUnit.HOURS), null)
        );
        Mockito.when(runRepo.findByCompanyIdOrderByDueAtAsc(1L)).thenReturn(runs);
        Mockito.when(branchRepo.findById(1L)).thenReturn(Optional.empty());

        DashboardService svc = new DashboardService(runRepo, occRepo, branchRepo);
        DashboardService.DashboardMetrics m = svc.metrics(1L, null);

        Assertions.assertEquals(2, m.completedCount());
        Assertions.assertEquals(1, m.onTimeCount());
        Assertions.assertEquals(50, m.onTimeRate());
        Assertions.assertEquals(0, m.aging().upTo1d());
        Assertions.assertEquals(1, m.aging().upTo3d());
        Assertions.assertEquals(1, m.branchRanking().size());
        Assertions.assertEquals(1, m.branchRanking().get(0).lateCount());
    }
}
