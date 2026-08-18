package com.torqmind.ops.interfaces.rest.company;

import com.torqmind.ops.application.company.CompanySettingsService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.company.CompanySettings;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leitura das configuracoes efetivas da empresa do usuario (para a UI). */
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    private final CompanySettingsService settingsService;
    private final TenantResolver tenantResolver;

    public CompanyController(CompanySettingsService settingsService, TenantResolver tenantResolver) {
        this.settingsService = settingsService;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/settings")
    public SettingsView settings(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId
    ) {
        Long cid;
        try {
            cid = tenantResolver.resolveListCompanyId(me, companyId);
        } catch (RuntimeException ex) {
            cid = null;
        }
        if (cid == null) {
            return new SettingsView(null, true, true, 15, true);
        }
        CompanySettings s = settingsService.getOrDefault(cid);
        return new SettingsView(
                s.getCompanyId(),
                s.isRequirePhotoOnComplete(),
                s.isRequireCommentOnComplete(),
                s.getDefaultReminderMinutes(),
                s.isChecklistsEnabled());
    }

    public record SettingsView(
            Long companyId,
            boolean requirePhotoOnComplete,
            boolean requireCommentOnComplete,
            int defaultReminderMinutes,
            boolean checklistsEnabled
    ) {
    }
}
