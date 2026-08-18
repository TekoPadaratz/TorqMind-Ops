package com.torqmind.ops;

import com.torqmind.ops.application.company.CompanySettingsService;
import com.torqmind.ops.domain.company.CompanySettings;
import com.torqmind.ops.infrastructure.persistence.CompanySettingsRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

class CompanySettingsServiceTest {

    @Test
    void defaultsWhenNoneSaved() {
        CompanySettingsRepository repo = Mockito.mock(CompanySettingsRepository.class);
        Mockito.when(repo.findById(1L)).thenReturn(Optional.empty());
        CompanySettingsService service = new CompanySettingsService(repo);

        CompanySettings s = service.getOrDefault(1L);

        Assertions.assertTrue(s.isRequirePhotoOnComplete());
        Assertions.assertTrue(s.isRequireCommentOnComplete());
        Assertions.assertEquals(15, s.getDefaultReminderMinutes());
    }

    @Test
    void updateClampsReminderAndPersists() {
        CompanySettingsRepository repo = Mockito.mock(CompanySettingsRepository.class);
        Mockito.when(repo.findById(1L)).thenReturn(Optional.empty());
        Mockito.when(repo.save(Mockito.any(CompanySettings.class))).thenAnswer(inv -> inv.getArgument(0));
        CompanySettingsService service = new CompanySettingsService(repo);

        CompanySettings s = service.update(1L, false, true, 9999, true);

        Assertions.assertFalse(s.isRequirePhotoOnComplete());
        Assertions.assertTrue(s.isRequireCommentOnComplete());
        Assertions.assertEquals(1440, s.getDefaultReminderMinutes());
    }
}
