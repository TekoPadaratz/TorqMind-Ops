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
        AuthService.LoginOutcome outcome = authService.login(request.username(), request.password());
        if (outcome.totpRequired()) {
            return LoginResponse.totpChallenge(outcome.challenge());
        }
        return toLogin(outcome.result());
    }

    @PostMapping("/login/2fa")
    public LoginResponse loginTotp(@Valid @RequestBody TotpLoginRequest request) {
        AuthService.LoginResult result = authService.verifyTotpLogin(request.challenge(), request.code());
        return toLogin(result);
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

    @PostMapping("/password")
    public LoginResponse changePassword(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        AuthService.LoginResult result = authService.changeOwnPassword(
                principal.userId(),
                request.currentPassword(),
                request.newPassword()
        );
        return toLogin(result);
    }

    @GetMapping("/2fa")
    public TwoFactorStatus twoFactorStatus(@AuthenticationPrincipal AppUserPrincipal principal) {
        return new TwoFactorStatus(authService.totpEnabled(principal.userId()));
    }

    @PostMapping("/2fa/setup")
    public AuthService.TotpSetup setupTwoFactor(@AuthenticationPrincipal AppUserPrincipal principal) {
        return authService.setupTotp(principal.userId());
    }

    @PostMapping("/2fa/enable")
    public TwoFactorStatus enableTwoFactor(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TotpCodeRequest request
    ) {
        authService.enableTotp(principal.userId(), request.code());
        return new TwoFactorStatus(true);
    }

    @PostMapping("/2fa/disable")
    public TwoFactorStatus disableTwoFactor(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody TotpCodeRequest request
    ) {
        authService.disableTotp(principal.userId(), request.code());
        return new TwoFactorStatus(false);
    }

    private static LoginResponse toLogin(AuthService.LoginResult result) {
        return new LoginResponse(
                result.token(),
                result.userId().toString(),
                result.username(),
                result.fullName(),
                result.role(),
                RoleLabels.pt(result.role()),
                result.companyId(),
                result.branchId(),
                false,
                null
        );
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record TotpLoginRequest(@NotBlank String challenge, @NotBlank String code) {
    }

    public record TotpCodeRequest(@NotBlank String code) {
    }

    public record TwoFactorStatus(boolean enabled) {
    }

    public record LoginResponse(
            String token,
            String userId,
            String username,
            String fullName,
            String role,
            String roleLabel,
            Long companyId,
            Long branchId,
            boolean totpRequired,
            String challenge
    ) {
        public static LoginResponse totpChallenge(String challenge) {
            return new LoginResponse(null, null, null, null, null, null, null, null, true, challenge);
        }
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
