package com.torqmind.ops.interfaces.rest.dashboard;

import com.torqmind.ops.application.dashboard.DashboardService;
import com.torqmind.ops.application.dashboard.ManagementReportPdfRenderer;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final TenantResolver tenantResolver;

    public DashboardController(DashboardService dashboardService, TenantResolver tenantResolver) {
        this.dashboardService = dashboardService;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/summary")
    public DashboardService.DashboardSummary summary(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        Long branchId = tenantResolver.branchFilterOrNull(me);
        return dashboardService.summary(cid, branchId);
    }

    @GetMapping("/metrics")
    public DashboardService.DashboardMetrics metrics(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        Long branchId = tenantResolver.branchFilterOrNull(me);
        return dashboardService.metrics(cid, branchId);
    }

    @GetMapping("/report.pdf")
    public ResponseEntity<byte[]> report(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId,
            @RequestParam String from,
            @RequestParam String to
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        Long branchId = tenantResolver.branchFilterOrNull(me);
        java.time.ZoneId zone = java.time.ZoneId.of("America/Sao_Paulo");
        java.time.Instant fromI = java.time.LocalDate.parse(from).atStartOfDay(zone).toInstant();
        java.time.Instant toI = java.time.LocalDate.parse(to).plusDays(1).atStartOfDay(zone).toInstant();
        byte[] pdf = ManagementReportPdfRenderer.render(dashboardService.report(cid, branchId, fromI, toI));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=relatorio-torqmind.pdf")
                .body(pdf);
    }
}
