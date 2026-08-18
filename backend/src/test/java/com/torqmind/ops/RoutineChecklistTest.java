package com.torqmind.ops;

import com.torqmind.ops.application.company.CompanySettingsService;
import com.torqmind.ops.application.notification.NotificationService;
import com.torqmind.ops.application.routine.RoutineService;
import com.torqmind.ops.application.task.ActivityService;
import com.torqmind.ops.application.tenant.TenantAccessService;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.infrastructure.persistence.RoutineChecklistItemRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunChecklistItemRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.persistence.TaskAttachmentRepository;
import com.torqmind.ops.infrastructure.persistence.TaskCommentRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

class RoutineChecklistTest {

    private RoutineRunRepository runRepo;
    private RoutineTemplateRepository templateRepo;
    private RoutineRunChecklistItemRepository runChecklistRepo;
    private TenantAccessService tenant;
    private RoutineService service;
    private AppUserPrincipal me;

    @BeforeEach
    void setup() {
        runRepo = Mockito.mock(RoutineRunRepository.class);
        templateRepo = Mockito.mock(RoutineTemplateRepository.class);
        runChecklistRepo = Mockito.mock(RoutineRunChecklistItemRepository.class);
        tenant = Mockito.mock(TenantAccessService.class);
        service = new RoutineService(
                templateRepo, runRepo,
                Mockito.mock(NotificationService.class),
                Mockito.mock(ActivityService.class),
                Mockito.mock(TaskAttachmentRepository.class),
                Mockito.mock(TaskCommentRepository.class),
                Mockito.mock(UserRepository.class),
                tenant,
                Mockito.mock(RoutineChecklistItemRepository.class),
                runChecklistRepo,
                Mockito.mock(CompanySettingsService.class));
        me = new AppUserPrincipal(UUID.randomUUID(), "op", "OPERATOR", 1L, 2L);
        Mockito.when(runRepo.save(Mockito.any(RoutineRun.class))).thenAnswer(i -> i.getArgument(0));
    }

    private RoutineRun run() {
        RoutineRun r = new RoutineRun();
        r.setTemplateId(10L);
        r.setStatus(RoutineStatus.EM_ANDAMENTO);
        r.setAssignedUserId(me.userId());
        return r;
    }

    private RoutineTemplate template() {
        RoutineTemplate t = new RoutineTemplate();
        t.setRequiresPhoto(false);
        t.setRequiresComment(false);
        return t;
    }

    @Test
    void completeBlockedWhenRequiredChecklistItemsPending() {
        Mockito.when(tenant.requireRoutineRunAccess(me, 5L)).thenReturn(run());
        Mockito.when(templateRepo.findById(10L)).thenReturn(Optional.of(template()));
        Mockito.when(runChecklistRepo.countByRunIdAndRequiredTrueAndCheckedFalse(Mockito.any())).thenReturn(1L);

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.transition(5L, RoutineStatus.CONCLUIDA, "ok", me));
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("checklist"));
    }

    @Test
    void completeAllowedWhenChecklistDone() {
        Mockito.when(tenant.requireRoutineRunAccess(me, 5L)).thenReturn(run());
        Mockito.when(templateRepo.findById(10L)).thenReturn(Optional.of(template()));
        Mockito.when(runChecklistRepo.countByRunIdAndRequiredTrueAndCheckedFalse(Mockito.any())).thenReturn(0L);

        RoutineRun result = service.transition(5L, RoutineStatus.CONCLUIDA, "ok", me);
        Assertions.assertEquals(RoutineStatus.CONCLUIDA, result.getStatus());
    }
}
