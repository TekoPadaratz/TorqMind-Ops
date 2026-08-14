package com.torqmind.ops;

import com.torqmind.ops.application.tenant.TenantAccessService;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.persistence.SectorRepository;
import com.torqmind.ops.infrastructure.persistence.TaskAttachmentRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAccessServiceTest {

    @Mock RoutineTemplateRepository templateRepository;
    @Mock RoutineRunRepository runRepository;
    @Mock OccurrenceRepository occurrenceRepository;
    @Mock TaskAttachmentRepository attachmentRepository;
    @Mock BranchRepository branchRepository;
    @Mock SectorRepository sectorRepository;
    @Mock UserRepository userRepository;
    @InjectMocks TenantAccessService accessService;

    @Test
    void ownerCannotAccessAnotherCompanyByGuessingTheId() {
        RoutineRun run = run(20L, 3L);
        when(runRepository.findById(7L)).thenReturn(Optional.of(run));
        AppUserPrincipal owner = principal("OWNER", 10L, null);

        Assertions.assertThrows(ForbiddenException.class,
                () -> accessService.requireRoutineRunAccess(owner, 7L));
    }

    @Test
    void managerCannotAccessAnotherBranch() {
        RoutineRun run = run(10L, 4L);
        when(runRepository.findById(8L)).thenReturn(Optional.of(run));
        AppUserPrincipal manager = principal("MANAGER", 10L, 3L);

        Assertions.assertThrows(ForbiddenException.class,
                () -> accessService.requireRoutineRunAccess(manager, 8L));
    }

    @Test
    void ownerCanAccessItsCompanyAndMasterCanAccessAnyCompany() {
        RoutineRun run = run(10L, 4L);
        when(runRepository.findById(9L)).thenReturn(Optional.of(run));
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));

        Assertions.assertSame(run,
                accessService.requireRoutineRunAccess(principal("OWNER", 10L, null), 9L));
        Assertions.assertSame(run,
                accessService.requireRoutineRunAccess(principal("MASTER", null, null), 10L));
    }

    @Test
    void onlyAssignedUserCanExecuteANamedTask() {
        UUID assigned = UUID.randomUUID();
        RoutineRun run = run(10L, 4L);
        run.setAssignedUserId(assigned);

        Assertions.assertDoesNotThrow(() -> accessService.requireRoutineExecutor(
                new AppUserPrincipal(assigned, "assigned", "MANAGER", 10L, 4L), run));
        Assertions.assertThrows(ForbiddenException.class, () -> accessService.requireRoutineExecutor(
                principal("OWNER", 10L, null), run));
        Assertions.assertDoesNotThrow(() -> accessService.requireRoutineExecutor(
                principal("MASTER", null, null), run));
    }

    private static RoutineRun run(Long companyId, Long branchId) {
        RoutineRun run = new RoutineRun();
        run.setCompanyId(companyId);
        run.setBranchId(branchId);
        return run;
    }

    private static AppUserPrincipal principal(String role, Long companyId, Long branchId) {
        return new AppUserPrincipal(UUID.randomUUID(), "test", role, companyId, branchId);
    }
}
