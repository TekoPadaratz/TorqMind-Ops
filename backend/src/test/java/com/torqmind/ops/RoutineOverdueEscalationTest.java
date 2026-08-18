package com.torqmind.ops;

import com.torqmind.ops.application.notification.NotificationService;
import com.torqmind.ops.application.routine.RoutineService;
import com.torqmind.ops.application.task.ActivityService;
import com.torqmind.ops.application.tenant.TenantAccessService;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.persistence.TaskAttachmentRepository;
import com.torqmind.ops.infrastructure.persistence.TaskCommentRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class RoutineOverdueEscalationTest {

    @Test
    void overdueEscalatesToBranchManager() {
        RoutineTemplateRepository templateRepo = Mockito.mock(RoutineTemplateRepository.class);
        RoutineRunRepository runRepo = Mockito.mock(RoutineRunRepository.class);
        NotificationService notifications = Mockito.mock(NotificationService.class);
        ActivityService activity = Mockito.mock(ActivityService.class);
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        RoutineService service = new RoutineService(
                templateRepo, runRepo, notifications, activity,
                Mockito.mock(TaskAttachmentRepository.class), Mockito.mock(TaskCommentRepository.class),
                userRepo, Mockito.mock(TenantAccessService.class));

        UUID assignee = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        RoutineRun run = Mockito.mock(RoutineRun.class);
        Mockito.when(run.getId()).thenReturn(1L);
        Mockito.when(run.getTemplateId()).thenReturn(1L);
        Mockito.when(run.getStatus()).thenReturn(RoutineStatus.PENDENTE);
        Mockito.when(run.getDueAt()).thenReturn(Instant.now().minusSeconds(120));
        Mockito.when(run.getAssignedUserId()).thenReturn(assignee);
        Mockito.when(run.getCompanyId()).thenReturn(1L);
        Mockito.when(run.getBranchId()).thenReturn(2L);

        RoutineTemplate tpl = new RoutineTemplate();
        tpl.setTitle("Afericao de bomba");
        Mockito.when(templateRepo.findById(1L)).thenReturn(Optional.of(tpl));
        Mockito.when(runRepo.findByStatusIn(Mockito.anyList())).thenReturn(List.of(run));

        User mgr = new User();
        mgr.setId(manager);
        Mockito.when(userRepo.findByCompanyIdAndBranchIdAndRoleIgnoreCaseAndActiveTrue(1L, 2L, "MANAGER"))
                .thenReturn(List.of(mgr));
        Mockito.when(userRepo.findByCompanyIdAndRoleIgnoreCaseAndActiveTrue(1L, "OWNER"))
                .thenReturn(List.of());

        service.processDueReminders();

        ArgumentCaptor<UUID> recipients = ArgumentCaptor.forClass(UUID.class);
        Mockito.verify(notifications, Mockito.atLeastOnce()).notifyCounterpart(
                Mockito.any(), recipients.capture(), Mockito.eq("ROUTINE_RUN"), Mockito.eq(1L),
                Mockito.anyString(), Mockito.anyString());
        Assertions.assertTrue(recipients.getAllValues().contains(manager),
                "gerente da filial deve receber o escalonamento de atraso");
    }
}
