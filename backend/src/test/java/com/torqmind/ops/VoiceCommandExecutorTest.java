package com.torqmind.ops;

import com.torqmind.ops.application.occurrence.OccurrenceService;
import com.torqmind.ops.application.routine.RoutineService;
import com.torqmind.ops.application.task.TaskDetailService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.application.voice.VoiceCommandExecutor;
import com.torqmind.ops.application.voice.VoiceIntent;
import com.torqmind.ops.application.voice.VoiceResolved;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

class VoiceCommandExecutorTest {

    @Test
    void createTaskGoesThroughRoutineService() {
        RoutineService routineService = Mockito.mock(RoutineService.class);
        TenantResolver tenantResolver = Mockito.mock(TenantResolver.class);
        RoutineTemplate saved = Mockito.mock(RoutineTemplate.class);
        Mockito.when(saved.getId()).thenReturn(42L);
        Mockito.when(tenantResolver.resolveCompanyForCreate(Mockito.any(), Mockito.any())).thenReturn(1L);
        Mockito.when(tenantResolver.resolveBranchForCreate(Mockito.any(), Mockito.any())).thenReturn(2L);
        Mockito.when(routineService.createRecurringTask(
                Mockito.eq(1L), Mockito.eq(2L), Mockito.eq("Limpeza"), Mockito.any(),
                Mockito.eq("ONCE"), Mockito.eq("USER"), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(LocalTime.class), Mockito.any(LocalTime.class), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.any(LocalDate.class), Mockito.any(),
                Mockito.eq(true), Mockito.eq(true), Mockito.any()
        )).thenReturn(saved);

        VoiceCommandExecutor executor = new VoiceCommandExecutor(
                routineService,
                Mockito.mock(OccurrenceService.class),
                Mockito.mock(TaskDetailService.class),
                tenantResolver,
                Mockito.mock(RoutineRunRepository.class),
                Mockito.mock(RoutineTemplateRepository.class)
        );
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "op", "MANAGER", 1L, 2L);
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("CREATE_TASK");
        intent.setTitle("Limpeza");
        intent.setRecurrence("ONCE");
        intent.setTargetType("USER");
        intent.setScheduledDate(LocalDate.now().plusDays(1).toString());
        intent.setStartTime("08:00");
        intent.setDueTime("10:00");
        intent.setRequiresPhoto(true);
        intent.setRequiresComment(true);
        VoiceResolved resolved = new VoiceResolved();
        resolved.setCompanyId(1L);
        resolved.setBranchId(2L);
        resolved.setUserId(UUID.randomUUID());

        Map<String, Object> result = executor.execute(me, intent, resolved);
        Assertions.assertEquals(42L, result.get("entityId"));
        Mockito.verify(routineService).createRecurringTask(
                Mockito.eq(1L), Mockito.eq(2L), Mockito.eq("Limpeza"), Mockito.any(),
                Mockito.eq("ONCE"), Mockito.eq("USER"), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(LocalTime.class), Mockito.any(LocalTime.class), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.any(LocalDate.class), Mockito.any(),
                Mockito.eq(true), Mockito.eq(true), Mockito.any()
        );
    }
}
