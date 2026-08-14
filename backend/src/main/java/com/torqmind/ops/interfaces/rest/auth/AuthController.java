package com.torqmind.ops.interfaces.rest.auth;

import com.torqmind.ops.application.auth.AuthService;
import com.torqmind.ops.domain.user.RoleLabels;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(
                result.token(),
                result.userId().toString(),
                result.username(),
                result.fullName(),
                result.role(),
                RoleLabels.pt(result.role()),
                result.companyId(),
                result.branchId()
        );
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return new MeResponse(
                principal.userId().toString(),
                principal.username(),
                principal.role(),
                RoleLabels.pt(principal.role()),
                principal.companyId(),
                principal.branchId()
        );
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(
            String token,
            String userId,
            String username,
            String fullName,
            String role,
            String roleLabel,
            Long companyId,
            Long branchId
    ) {
    }

    public record MeResponse(
            String userId,
            String username,
            String role,
            String roleLabel,
            Long companyId,
            Long branchId
    ) {
    }
}
