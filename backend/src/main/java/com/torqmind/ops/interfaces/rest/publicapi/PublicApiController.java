package com.torqmind.ops.interfaces.rest.publicapi;

import com.torqmind.ops.application.dashboard.DashboardService;
import com.torqmind.ops.application.occurrence.OccurrenceService;
import com.torqmind.ops.application.routine.RoutineService;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** API publica v1 (somente leitura). Autenticada por X-API-Key; sempre escopada a empresa da chave. */
@RestController
@RequestMapping("/api/public/v1")
public class PublicApiController {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final RoutineService routineService;
    private final OccurrenceService occurrenceService;
    private final DashboardService dashboardService;

    public PublicApiController(RoutineService routineService, OccurrenceService occurrenceService,
                              DashboardService dashboardService) {
        this.routineService = routineService;
        this.occurrenceService = occurrenceService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/routines/runs")
    public List<RoutineService.CalendarRun> runs(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam String from,
            @RequestParam String to
    ) {
        Instant fromI = LocalDate.parse(from).atStartOfDay(ZONE).toInstant();
        Instant toI = LocalDate.parse(to).plusDays(1).atStartOfDay(ZONE).toInstant();
        return routineService.calendarRuns(me.companyId(), null, fromI, toI);
    }

    @GetMapping("/occurrences")
    public List<PublicOccurrence> occurrences(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) OccurrenceStatus status
    ) {
        return occurrenceService.list(me.companyId(), null, status).stream()
                .map(o -> new PublicOccurrence(o.getId(), o.getTitle(), o.getStatus().name(), o.getPriority(),
                        o.getKind() == null ? null : o.getKind().name(), o.getCreatedAt(), o.getFinalizedAt()))
                .toList();
    }

    @GetMapping("/dashboard/summary")
    public PublicSummary summary(@AuthenticationPrincipal AppUserPrincipal me) {
        DashboardService.DashboardSummary s = dashboardService.summary(me.companyId(), null);
        return new PublicSummary(
                s.routinesPending(), s.routinesInProgress(), s.routinesLate(),
                s.occurrencesOpen(), s.occurrencesAwaitingValidation(),
                s.lateRuns().stream().map(r -> new PublicLateRun(r.getId(), r.getStatus().name(), r.getDueAt())).toList(),
                s.openOccurrences().stream().map(o -> new PublicOpenOccurrence(o.getId(), o.getTitle(), o.getPriority())).toList());
    }

    public record PublicOccurrence(Long id, String title, String status, String priority, String kind,
                                   Instant createdAt, Instant finalizedAt) {
    }

    public record PublicLateRun(Long id, String status, Instant dueAt) {
    }

    public record PublicOpenOccurrence(Long id, String title, String priority) {
    }

    public record PublicSummary(long routinesPending, long routinesInProgress, long routinesLate,
                                long occurrencesOpen, long occurrencesAwaitingValidation,
                                List<PublicLateRun> lateRuns, List<PublicOpenOccurrence> openOccurrences) {
    }
}
