package com.torqmind.ops.application.company;

import com.torqmind.ops.domain.company.CompanySettings;
import com.torqmind.ops.infrastructure.persistence.CompanySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CompanySettingsService {

    private static final int MAX_REMINDER_MINUTES = 1440;

    private final CompanySettingsRepository repository;

    public CompanySettingsService(CompanySettingsRepository repository) {
        this.repository = repository;
    }

    /** Configuração da empresa ou os padrões-lei quando ainda não personalizada. */
    public CompanySettings getOrDefault(Long companyId) {
        return repository.findById(companyId).orElseGet(() -> {
            CompanySettings fresh = new CompanySettings();
            fresh.setCompanyId(companyId);
            return fresh;
        });
    }

    @Transactional
    public CompanySettings update(Long companyId, boolean requirePhoto, boolean requireComment, Integer reminderMinutes) {
        CompanySettings settings = repository.findById(companyId).orElseGet(() -> {
            CompanySettings fresh = new CompanySettings();
            fresh.setCompanyId(companyId);
            return fresh;
        });
        settings.setRequirePhotoOnComplete(requirePhoto);
        settings.setRequireCommentOnComplete(requireComment);
        int minutes = reminderMinutes == null ? 15 : Math.max(0, Math.min(MAX_REMINDER_MINUTES, reminderMinutes));
        settings.setDefaultReminderMinutes(minutes);
        settings.setUpdatedAt(Instant.now());
        return repository.save(settings);
    }
}
