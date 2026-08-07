package com.torqmind.ops.interfaces.rest.admin;

import com.torqmind.ops.application.admin.AdminService;
import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.sector.Sector;
import com.torqmind.ops.domain.user.RoleLabels;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('MASTER')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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
                request.username(),
                request.fullName(),
                request.role(),
                request.password(),
                request.companyId(),
                request.branchId(),
                request.sectorId()
        );
        return UserResponse.from(user);
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
    public Company createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        return adminService.createCompany(request.name());
    }

    @PostMapping("/branches")
    public Branch createBranch(@Valid @RequestBody CreateBranchRequest request) {
        return adminService.createBranch(request.companyId(), request.name());
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String fullName,
            @NotBlank String role,
            @NotBlank String password,
            Long companyId,
            Long branchId,
            Long sectorId
    ) {
    }

    public record CreateSectorRequest(@NotBlank String name, Long companyId, Long branchId) {
    }

    public record CreateCompanyRequest(@NotBlank String name) {
    }

    public record CreateBranchRequest(Long companyId, @NotBlank String name) {
    }

    public record UserResponse(
            String id,
            String username,
            String fullName,
            String role,
            String roleLabel,
            Long companyId,
            Long branchId,
            boolean active
    ) {
        static UserResponse from(User u) {
            return new UserResponse(
                    u.getId().toString(),
                    u.getUsername(),
                    u.getFullName(),
                    u.getRole(),
                    RoleLabels.pt(u.getRole()),
                    u.getCompanyId(),
                    u.getBranchId(),
                    u.isActive()
            );
        }
    }
}
