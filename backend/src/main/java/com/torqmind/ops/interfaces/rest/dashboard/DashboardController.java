package com.torqmind.ops.interfaces.rest.dashboard;

import com.torqmind.ops.application.dashboard.DashboardService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
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
}
