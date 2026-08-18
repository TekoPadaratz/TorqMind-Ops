package com.torqmind.ops;

import com.torqmind.ops.application.admin.AdminService;
import com.torqmind.ops.application.auth.CredentialService;
import com.torqmind.ops.application.storage.DriveFolderService;
import com.torqmind.ops.application.tenant.TenantAccessService;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.user.PasswordChangeEvent;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.CompanyRepository;
import com.torqmind.ops.infrastructure.persistence.PasswordChangeEventRepository;
import com.torqmind.ops.infrastructure.persistence.SectorRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class UserCredentialControlTest {

    private UserRepository userRepository;
    private CompanyRepository companyRepository;
    private PasswordChangeEventRepository eventRepository;
    private TenantAccessService tenantAccessService;
    private CredentialService credentialService;
    private AdminService adminService;
    private UUID masterId;
    private UUID operatorId;

    @BeforeEach
    void setup() {
        userRepository = Mockito.mock(UserRepository.class);
        companyRepository = Mockito.mock(CompanyRepository.class);
        BranchRepository branchRepository = Mockito.mock(BranchRepository.class);
        SectorRepository sectorRepository = Mockito.mock(SectorRepository.class);
        eventRepository = Mockito.mock(PasswordChangeEventRepository.class);
        tenantAccessService = Mockito.mock(TenantAccessService.class);
        DriveFolderService driveFolderService = Mockito.mock(DriveFolderService.class);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(eventRepository.save(Mockito.any(PasswordChangeEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        credentialService = new CredentialService(new BCryptPasswordEncoder(), userRepository, eventRepository);
        adminService = new AdminService(
                userRepository,
                sectorRepository,
                companyRepository,
                branchRepository,
                credentialService,
                eventRepository,
                driveFolderService,
                tenantAccessService
        );
        masterId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
    }

    @Test
    void createUserRecordsCreatedEventWithoutKeepingTheSecret() {
        Mockito.when(companyRepository.findById(1L)).thenReturn(Optional.of(new Company()));
        Mockito.when(userRepository.findByUsernameIgnoreCase("ana")).thenReturn(Optional.empty());

        User created = adminService.createUser(
                "MASTER", masterId, "ana", "Ana Operadora", "OPERATOR", "Senha1234", 1L, 2L, null
        );

        Assertions.assertNotEquals("Senha1234", created.getPasswordHash());
        Assertions.assertTrue(created.getPasswordHash().startsWith("$2a$"));
        Assertions.assertEquals(0, created.getPasswordEpoch());
        ArgumentCaptor<PasswordChangeEvent> captor = ArgumentCaptor.forClass(PasswordChangeEvent.class);
        Mockito.verify(eventRepository).save(captor.capture());
        Assertions.assertEquals(CredentialService.ACTION_CREATED, captor.getValue().getAction());
        Assertions.assertEquals(masterId, captor.getValue().getActorUserId());
        Assertions.assertFalse(asJson(captor.getValue()).contains("Senha1234"));
    }

    @Test
    void operatorCannotResetPassword() {
        Assertions.assertThrows(ForbiddenException.class,
                () -> adminService.resetPassword("OPERATOR", operatorId, operatorId, "NovaSenha1"));
        Mockito.verify(eventRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void lastMasterCannotBeDeactivated() {
        User lastMaster = user(masterId, "teko", "MASTER", true);
        Mockito.when(userRepository.findById(masterId)).thenReturn(Optional.of(lastMaster));
        Mockito.when(userRepository.countByRoleIgnoreCaseAndActiveTrue("MASTER")).thenReturn(1L);

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> adminService.updateUser("MASTER", masterId, masterId, "Teko", "MASTER", null, null, null, false));
        Assertions.assertTrue(ex.getMessage().contains("própria conta"));
    }

    @Test
    void lastRemainingMasterCannotBeDemoted() {
        UUID otherMaster = UUID.randomUUID();
        User last = user(otherMaster, "lucas", "MASTER", true);
        Mockito.when(userRepository.findById(otherMaster)).thenReturn(Optional.of(last));
        Mockito.when(userRepository.countByRoleIgnoreCaseAndActiveTrue("MASTER")).thenReturn(1L);

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> adminService.updateUser("MASTER", masterId, otherMaster, "Lucas", "OWNER", 1L, null, null, true));
        Assertions.assertTrue(ex.getMessage().contains("último administrador"));
    }

    @Test
    void adminResetBumpsEpochClearsLockAndAudits() {
        User target = user(operatorId, "guilherme", "OWNER", true);
        target.setFailedLoginCount(4);
        target.setLockedUntil(java.time.Instant.now().plusSeconds(600));
        Mockito.when(userRepository.findById(operatorId)).thenReturn(Optional.of(target));

        User updated = adminService.resetPassword("MASTER", masterId, operatorId, "NovaSenha9");

        Assertions.assertEquals(1, updated.getPasswordEpoch());
        Assertions.assertEquals(0, updated.getFailedLoginCount());
        Assertions.assertNull(updated.getLockedUntil());
        ArgumentCaptor<PasswordChangeEvent> captor = ArgumentCaptor.forClass(PasswordChangeEvent.class);
        Mockito.verify(eventRepository).save(captor.capture());
        Assertions.assertEquals(CredentialService.ACTION_ADMIN_RESET, captor.getValue().getAction());
        Assertions.assertFalse(updated.getPasswordHash().contains("NovaSenha9"));
    }

    @Test
    void passwordHistoryNeverIncludesHashOrSecret() {
        PasswordChangeEvent event = new PasswordChangeEvent();
        event.setUserId(operatorId);
        event.setActorUserId(masterId);
        event.setAction(CredentialService.ACTION_ADMIN_RESET);
        event.setCreatedAt(java.time.Instant.parse("2026-08-17T12:00:00Z"));
        User actor = user(masterId, "teko", "MASTER", true);
        Mockito.when(userRepository.findById(operatorId)).thenReturn(Optional.of(user(operatorId, "ana", "OPERATOR", true)));
        Mockito.when(eventRepository.findByUserIdOrderByCreatedAtDesc(operatorId)).thenReturn(List.of(event));
        Mockito.when(userRepository.findAllById(Mockito.any())).thenReturn(List.of(actor));

        List<AdminService.PasswordEventView> events = adminService.listPasswordEvents("MASTER", operatorId);
        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals("Redefinição pelo administrador", events.get(0).actionLabel());
        Assertions.assertEquals("Teko", events.get(0).actorName());
        Assertions.assertEquals(CredentialService.ACTION_ADMIN_RESET, events.get(0).action());
        Assertions.assertEquals(masterId, events.get(0).actorUserId());
    }

    private static User user(UUID id, String username, String role, boolean active) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(username.substring(0, 1).toUpperCase() + username.substring(1));
        user.setRole(role);
        user.setActive(active);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        return user;
    }

    private static String asJson(PasswordChangeEvent event) {
        return event.getAction() + ":" + event.getActorUserId() + ":" + event.getUserId();
    }
}
