package com.torqmind.ops.infrastructure.security;

import java.util.UUID;

public record AppUserPrincipal(
        UUID userId,
        String username,
        String role,
        Long companyId,
        Long branchId
) {
}
