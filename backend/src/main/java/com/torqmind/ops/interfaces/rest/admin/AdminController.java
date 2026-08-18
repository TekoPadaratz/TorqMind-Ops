package com.torqmind.ops.interfaces.rest.admin;

import com.torqmind.ops.application.admin.AdminService;
import com.torqmind.ops.application.auth.AuthService;
import com.torqmind.ops.application.company.CompanySettingsService;
import com.torqmind.ops.application.notification.EmailService;
import com.torqmind.ops.application.notification.EmailSettingsService;
import com.torqmind.ops.domain.email.EmailSettings;
import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.company.CompanySettings;
import com.torqmind.ops.domain.sector.Sector;
import com.torqmind.ops.domain.user.RoleLabels;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('MASTER')")
public class AdminController {

    private final AdminService adminService;
    private final CompanySettingsService companySettingsService;
    private final AuthService authService;
    private final EmailSettingsService emailSettingsService;
    private final EmailService emailService;

    public AdminController(AdminService adminService, CompanySettingsService companySettingsService,
                          AuthService authService, EmailSettingsService emailSettingsService, EmailService emailService) {
        this.adminService = adminService;
        this.companySettingsService = companySettingsService;
        this.authService = authService;
        this.emailSettingsService = emailSettingsService;
        this.emailService = emailService;
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers() {
        return adminService.listUsers().stream().map(UserResponse::from).toList();
    }

    @PostMapping("/users")
    public UserResponse createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        User user = adminService.createUser(
                me.role(),
                me.userId(),
                request.username(),
                request.fullName(),
                request.role(),
                request.password(),
                request.companyId(),
                request.branchId(),
                request.sectorId(),
                request.email()
        );
        return UserResponse.from(user);
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        User user = adminService.updateUser(
                me.role(),
                me.userId(),
                id,
                request.fullName(),
                request.role(),
                request.companyId(),
                request.branchId(),
                request.sectorId(),
                request.active(),
                request.email()
        );
        return UserResponse.from(user);
    }

    @PutMapping("/users/{id}/password")
    public UserResponse resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        User user = adminService.resetPassword(me.role(), me.userId(), id, request.newPassword());
        return UserResponse.from(user);
    }

    @PostMapping("/users/{id}/unlock")
    public UserResponse unlockUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return UserResponse.from(adminService.unlockUser(me.role(), id));
    }

    @PostMapping("/users/{id}/2fa/disable")
    public UserResponse disableUserTwoFactor(@PathVariable UUID id) {
        return UserResponse.from(authService.adminDisableTotp(id));
    }

    @GetMapping("/users/{id}/password-events")
    public List<PasswordEventResponse> listPasswordEvents(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        return adminService.listPasswordEvents(me.role(), id).stream()
                .map(PasswordEventResponse::from)
                .toList();
    }

    @GetMapping("/sectors")
    public List<Sector> listSectors(@RequestParam(required = false) Long companyId) {
        return adminService.listSectors(companyId == null ? 1L : companyId);
    }

    @PostMapping("/sectors")
    public Sector createSector(@Valid @RequestBody CreateSectorRequest request) {
        return adminService.createSector(request.name(), request.companyId(), request.branchId());
    }

    @PostMapping("/companies")
    public Company createCompany(@Valid @RequestBody CompanyRequest request) {
        return adminService.createCompany(
                request.name(), request.legalName(), request.cnpj(), toAddress(request));
    }

    @PutMapping("/companies/{id}")
    public Company updateCompany(@PathVariable Long id, @Valid @RequestBody CompanyRequest request) {
        return adminService.updateCompany(
                id, request.name(), request.legalName(), request.cnpj(), toAddress(request));
    }

    @PostMapping("/branches")
    public Branch createBranch(@Valid @RequestBody BranchRequest request) {
        return adminService.createBranch(
                request.companyId(), request.name(), request.legalName(), request.cnpj(), toAddress(request));
    }

    @PutMapping("/branches/{id}")
    public Branch updateBranch(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return adminService.updateBranch(
                id, request.name(), request.legalName(), request.cnpj(), toAddress(request));
    }

    @GetMapping("/companies/{id}/settings")
    public CompanySettingsResponse getSettings(@PathVariable Long id) {
        return CompanySettingsResponse.from(companySettingsService.getOrDefault(id));
    }

    @PutMapping("/companies/{id}/settings")
    public CompanySettingsResponse updateSettings(@PathVariable Long id, @RequestBody CompanySettingsRequest request) {
        return CompanySettingsResponse.from(companySettingsService.update(
                id,
                Boolean.TRUE.equals(request.requirePhotoOnComplete()),
                Boolean.TRUE.equals(request.requireCommentOnComplete()),
                request.defaultReminderMinutes(),
                request.checklistsEnabled() == null || request.checklistsEnabled()));
    }

    @GetMapping("/email-settings")
    public EmailSettingsResponse getEmailSettings() {
        return EmailSettingsResponse.from(emailSettingsService.get());
    }

    @PutMapping("/email-settings")
    public EmailSettingsResponse updateEmailSettings(@RequestBody EmailSettingsRequest request) {
        EmailSettings s = emailSettingsService.update(
                Boolean.TRUE.equals(request.enabled()),
                request.host(),
                request.port(),
                request.username(),
                request.password(),
                request.useTls() == null || request.useTls(),
                Boolean.TRUE.equals(request.useSsl()),
                request.fromEmail(),
                request.fromName());
        return EmailSettingsResponse.from(s);
    }

    @PostMapping("/email-settings/test")
    public java.util.Map<String, Object> testEmail(@RequestBody TestEmailRequest request) {
        emailService.sendTest(request.to());
        return java.util.Map.of("ok", true);
    }

    public record EmailSettingsRequest(
            Boolean enabled, String host, Integer port, String username, String password,
            Boolean useTls, Boolean useSsl, String fromEmail, String fromName) {
    }

    public record TestEmailRequest(String to) {
    }

    public record EmailSettingsResponse(
            boolean enabled, String host, int port, String username, boolean passwordSet,
            boolean useTls, boolean useSsl, String fromEmail, String fromName) {
        static EmailSettingsResponse from(EmailSettings s) {
            return new EmailSettingsResponse(
                    s.isEnabled(), s.getHost(), s.getPort(), s.getUsername(),
                    s.getPassword() != null && !s.getPassword().isBlank(),
                    s.isUseTls(), s.isUseSsl(), s.getFromEmail(), s.getFromName());
        }
    }

    public record CompanySettingsRequest(
            Boolean requirePhotoOnComplete,
            Boolean requireCommentOnComplete,
            Integer defaultReminderMinutes,
            Boolean checklistsEnabled
    ) {
    }

    public record CompanySettingsResponse(
            Long companyId,
            boolean requirePhotoOnComplete,
            boolean requireCommentOnComplete,
            int defaultReminderMinutes,
            boolean checklistsEnabled
    ) {
        static CompanySettingsResponse from(CompanySettings s) {
            return new CompanySettingsResponse(
                    s.getCompanyId(),
                    s.isRequirePhotoOnComplete(),
                    s.isRequireCommentOnComplete(),
                    s.getDefaultReminderMinutes(),
                    s.isChecklistsEnabled());
        }
    }

    private static AdminService.PostalAddressRequest toAddress(LegalCadastro request) {
        return new AdminService.PostalAddressRequest(
                request.addressStreet(),
                request.addressNumber(),
                request.addressComplement(),
                request.addressNeighborhood(),
                request.addressCity(),
                request.addressState(),
                request.addressPostalCode()
        );
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String fullName,
            @NotBlank String role,
            @NotBlank String password,
            Long companyId,
            Long branchId,
            Long sectorId,
            String email
    ) {
    }

    public record ResetPasswordRequest(@NotBlank String newPassword) {
    }

    public record UpdateUserRequest(
            @NotBlank String fullName,
            @NotBlank String role,
            Long companyId,
            Long branchId,
            Long sectorId,
            Boolean active,
            String email
    ) {
    }

    public record CreateSectorRequest(@NotBlank String name, Long companyId, Long branchId) {
    }

    public interface LegalCadastro {
        String addressStreet();
        String addressNumber();
        String addressComplement();
        String addressNeighborhood();
        String addressCity();
        String addressState();
        String addressPostalCode();
    }

    public record CompanyRequest(
            @NotBlank String name,
            String legalName,
            String cnpj,
            String addressStreet,
            String addressNumber,
            String addressComplement,
            String addressNeighborhood,
            String addressCity,
            String addressState,
            String addressPostalCode
    ) implements LegalCadastro {
    }

    public record BranchRequest(
            Long companyId,
            @NotBlank String name,
            String legalName,
            String cnpj,
            String addressStreet,
            String addressNumber,
            String addressComplement,
            String addressNeighborhood,
            String addressCity,
            String addressState,
            String addressPostalCode
    ) implements LegalCadastro {
    }

    public record UserResponse(
            String id,
            String username,
            String fullName,
            String email,
            String role,
            String roleLabel,
            Long companyId,
            Long branchId,
            Long sectorId,
            boolean active,
            boolean locked,
            Instant lockedUntil,
            Instant passwordChangedAt
    ) {
        static UserResponse from(User u) {
            Instant now = Instant.now();
            boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(now);
            return new UserResponse(
                    u.getId().toString(),
                    u.getUsername(),
                    u.getFullName(),
                    u.getEmail(),
                    u.getRole(),
                    RoleLabels.pt(u.getRole()),
                    u.getCompanyId(),
                    u.getBranchId(),
                    u.getSectorId(),
                    u.isActive(),
                    locked,
                    u.getLockedUntil(),
                    u.getPasswordChangedAt()
            );
        }
    }

    public record PasswordEventResponse(
            Long id,
            String action,
            String actionLabel,
            String actorUserId,
            String actorName,
            String actorUsername,
            Instant createdAt
    ) {
        static PasswordEventResponse from(AdminService.PasswordEventView event) {
            return new PasswordEventResponse(
                    event.id(),
                    event.action(),
                    event.actionLabel(),
                    event.actorUserId() == null ? null : event.actorUserId().toString(),
                    event.actorName(),
                    event.actorUsername(),
                    event.createdAt()
            );
        }
    }
}
