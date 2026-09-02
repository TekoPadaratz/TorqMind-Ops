package com.torqmind.ops;

import com.torqmind.ops.application.occurrence.OccurrenceService;
import com.torqmind.ops.application.routine.RoutineService;
import com.torqmind.ops.application.task.TaskDetailService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.application.voice.VoiceCommandExecutor;
import com.torqmind.ops.application.voice.VoiceIntent;
import com.torqmind.ops.application.voice.VoiceResolved;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.infrastructure.persistence.NotificationRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
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

    private static VoiceCommandExecutor executor(
            RoutineService routineService,
            OccurrenceService occurrenceService,
            TaskDetailService taskDetailService,
            TenantResolver tenantResolver,
            RoutineRunRepository runRepository,
            RoutineTemplateRepository templateRepository
    ) {
        return new VoiceCommandExecutor(
                routineService,
                occurrenceService,
                taskDetailService,
                tenantResolver,
                runRepository,
                templateRepository,
                Mockito.mock(OccurrenceRepository.class),
                Mockito.mock(NotificationRepository.class)
        );
    }

    @Test
    void createTaskGoesThroughRoutineService() {
        RoutineService routineService = Mockito.mock(RoutineService.class);
        TenantResolver tenantResolver = Mockito.mock(TenantResolver.class);
        RoutineTemplate saved = Mockito.mock(RoutineTemplate.class);
        Mockito.when(saved.getId()).thenReturn(42L);
        Mockito.when(saved.getTitle()).thenReturn("Limpeza");
        Mockito.when(tenantResolver.resolveCompanyForCreate(Mockito.any(), Mockito.any())).thenReturn(1L);
        Mockito.when(tenantResolver.resolveBranchForCreate(Mockito.any(), Mockito.any())).thenReturn(2L);
        Mockito.when(routineService.createRecurringTask(
                Mockito.eq(1L), Mockito.eq(2L), Mockito.eq("Limpeza"), Mockito.any(),
                Mockito.eq("ONCE"), Mockito.eq("USER"), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(LocalTime.class), Mockito.any(LocalTime.class), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.any(LocalDate.class), Mockito.any(),
                Mockito.eq(true), Mockito.eq(true), Mockito.any(), Mockito.any()
        )).thenReturn(saved);

        VoiceCommandExecutor executor = executor(
                routineService,
                Mockito.mock(OccurrenceService.class),
                Mockito.mock(TaskDetailService.class),
                tenantResolver,
                Mockito.mock(RoutineRunRepository.class),
                Mockito.mock(RoutineTemplateRepository.class)
        );
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

        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "op", "MANAGER", 1L, 2L);
        Map<String, Object> result = executor.execute(me, intent, resolved);
        Assertions.assertEquals(42L, result.get("entityId"));
        Mockito.verify(routineService).createRecurringTask(
                Mockito.eq(1L), Mockito.eq(2L), Mockito.eq("Limpeza"), Mockito.any(),
                Mockito.eq("ONCE"), Mockito.eq("USER"), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(LocalTime.class), Mockito.any(LocalTime.class), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.any(LocalDate.class), Mockito.any(),
                Mockito.eq(true), Mockito.eq(true), Mockito.any(), Mockito.any()
        );
    }

    @Test
    void openQualityAnalysisNavigatesWithoutSaving() {
        OccurrenceService occurrenceService = Mockito.mock(OccurrenceService.class);
        VoiceCommandExecutor executor = executor(
                Mockito.mock(RoutineService.class),
                occurrenceService,
                Mockito.mock(TaskDetailService.class),
                Mockito.mock(TenantResolver.class),
                Mockito.mock(RoutineRunRepository.class),
                Mockito.mock(RoutineTemplateRepository.class)
        );
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("OPEN_QUALITY_ANALYSIS");
        intent.setFuel("DIESEL_S10");
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "op", "OPERATOR", 1L, 2L);
        Map<String, Object> result = executor.execute(me, intent, new VoiceResolved());
        Assertions.assertEquals("/occurrences/new/fuel-quality?fuel=DIESEL_S10", result.get("navigateTo"));
        Mockito.verifyNoInteractions(occurrenceService);
    }

    @Test
    void queryTaskAnswersConcludedStatus() {
        RoutineRunRepository runRepo = Mockito.mock(RoutineRunRepository.class);
        RoutineRun run = Mockito.mock(RoutineRun.class);
        Mockito.when(run.getId()).thenReturn(7L);
        Mockito.when(run.getStatus()).thenReturn(RoutineStatus.CONCLUIDA);
        Mockito.when(run.getCompletedAt()).thenReturn(null);
        Mockito.when(runRepo.findById(7L)).thenReturn(java.util.Optional.of(run));
        VoiceCommandExecutor executor = executor(
                Mockito.mock(RoutineService.class),
                Mockito.mock(OccurrenceService.class),
                Mockito.mock(TaskDetailService.class),
                Mockito.mock(TenantResolver.class),
                runRepo,
                Mockito.mock(RoutineTemplateRepository.class)
        );
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("QUERY_TASK");
        intent.setTitle("Afericao de bomba");
        VoiceResolved resolved = new VoiceResolved();
        resolved.setRunId(7L);
        resolved.setTaskTitle("Afericao de bomba");
        resolved.setUserName("Alfredo");
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "op", "MANAGER", 1L, 2L);
        Map<String, Object> result = executor.execute(me, intent, resolved);
        String spoken = String.valueOf(result.get("spoken"));
        Assertions.assertTrue(spoken.toLowerCase().contains("alfredo"));
        Assertions.assertTrue(spoken.toLowerCase().contains("concluiu"));
    }

    @Test
    void deleteTaskWithoutPermissionSpeaksDenial() {
        RoutineService routineService = Mockito.mock(RoutineService.class);
        Mockito.when(routineService.deleteTemplateAsActor(Mockito.eq(5L), Mockito.any()))
                .thenThrow(new com.torqmind.ops.shared.api.ForbiddenException("nope"));
        VoiceCommandExecutor executor = executor(
                routineService,
                Mockito.mock(OccurrenceService.class),
                Mockito.mock(TaskDetailService.class),
                Mockito.mock(TenantResolver.class),
                Mockito.mock(RoutineRunRepository.class),
                Mockito.mock(RoutineTemplateRepository.class)
        );
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("DELETE_TASK");
        intent.setTitle("Afericao de bomba");
        VoiceResolved resolved = new VoiceResolved();
        resolved.setTemplateId(5L);
        resolved.setTaskTitle("Afericao de bomba");
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "op", "OPERATOR", 1L, 2L);
        Map<String, Object> result = executor.execute(me, intent, resolved);
        Assertions.assertTrue(String.valueOf(result.get("spoken")).toLowerCase().contains("permiss"));
    }

    @Test
    void listOccurrencesSpeaksCountAndNavigates() {
        OccurrenceService occurrenceService = Mockito.mock(OccurrenceService.class);
        TenantResolver tenantResolver = Mockito.mock(TenantResolver.class);
        Mockito.when(tenantResolver.resolveListCompanyId(Mockito.any(), Mockito.any())).thenReturn(1L);
        Mockito.when(tenantResolver.branchFilterOrNull(Mockito.any())).thenReturn(null);
        Occurrence o1 = Mockito.mock(Occurrence.class);
        Mockito.when(o1.getId()).thenReturn(11L);
        Mockito.when(o1.getTitle()).thenReturn("Vazamento na bomba 3");
        Mockito.when(o1.getStatus()).thenReturn(OccurrenceStatus.ABERTA);
        Mockito.when(occurrenceService.list(Mockito.eq(1L), Mockito.isNull(), Mockito.eq(OccurrenceStatus.ABERTA)))
                .thenReturn(java.util.List.of(o1));
        VoiceCommandExecutor executor = executor(
                Mockito.mock(RoutineService.class),
                occurrenceService,
                Mockito.mock(TaskDetailService.class),
                tenantResolver,
                Mockito.mock(RoutineRunRepository.class),
                Mockito.mock(RoutineTemplateRepository.class)
        );
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("LIST_OCCURRENCES");
        intent.setRequestedStatus("ABERTA");
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "op", "MANAGER", 1L, 2L);
        Map<String, Object> result = executor.execute(me, intent, new VoiceResolved());
        Assertions.assertEquals("/occurrences", result.get("navigateTo"));
        Assertions.assertTrue(String.valueOf(result.get("spoken")).toLowerCase().contains("ocorrência"));
    }

    @Test
    void bulkDeleteIsRefusedEvenForOwner() {
        VoiceCommandExecutor executor = executor(
                Mockito.mock(RoutineService.class),
                Mockito.mock(OccurrenceService.class),
                Mockito.mock(TaskDetailService.class),
                Mockito.mock(TenantResolver.class),
                Mockito.mock(RoutineRunRepository.class),
                Mockito.mock(RoutineTemplateRepository.class)
        );
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("DELETE_TASK");
        intent.setTranscript("excluir todas as rotinas");
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "dono", "OWNER", 1L, null);
        Map<String, Object> result = executor.execute(me, intent, new VoiceResolved());
        Assertions.assertEquals("REFUSED", result.get("entityType"));
        Assertions.assertTrue(String.valueOf(result.get("spoken")).toLowerCase().contains("massa"));
    }

    @Test
    void adminDeniedExplainsHierarchy() {
        VoiceCommandExecutor exec = executor(
                Mockito.mock(RoutineService.class),
                Mockito.mock(OccurrenceService.class),
                Mockito.mock(TaskDetailService.class),
                Mockito.mock(TenantResolver.class),
                Mockito.mock(RoutineRunRepository.class),
                Mockito.mock(RoutineTemplateRepository.class)
        );
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("ADMIN_DENIED");
        AppUserPrincipal op = new AppUserPrincipal(UUID.randomUUID(), "op", "OPERATOR", 1L, 2L);
        String spoken = String.valueOf(exec.execute(op, intent, new VoiceResolved()).get("spoken"));
        Assertions.assertTrue(spoken.toLowerCase().contains("cadastro"));
    }

    @Test
    void startOccurrenceTransitionsToInProgress() {
        OccurrenceService occurrenceService = Mockito.mock(OccurrenceService.class);
        Occurrence occ = Mockito.mock(Occurrence.class);
        Mockito.when(occ.getId()).thenReturn(9L);
        Mockito.when(occ.getTitle()).thenReturn("Bomba parada");
        Mockito.when(occurrenceService.transition(Mockito.eq(9L), Mockito.eq(OccurrenceStatus.EM_ATENDIMENTO), Mockito.any(), Mockito.any()))
                .thenReturn(occ);
        VoiceCommandExecutor exec = executor(
                Mockito.mock(RoutineService.class),
                occurrenceService,
                Mockito.mock(TaskDetailService.class),
                Mockito.mock(TenantResolver.class),
                Mockito.mock(RoutineRunRepository.class),
                Mockito.mock(RoutineTemplateRepository.class)
        );
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("START_OCCURRENCE");
        VoiceResolved resolved = new VoiceResolved();
        resolved.setOccurrenceId(9L);
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "m", "MANAGER", 1L, 2L);
        Map<String, Object> result = exec.execute(me, intent, resolved);
        Assertions.assertTrue(String.valueOf(result.get("spoken")).toLowerCase().contains("atendendo"));
    }
}
